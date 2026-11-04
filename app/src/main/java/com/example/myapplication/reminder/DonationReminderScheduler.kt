package com.example.myapplication.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.InfectionRuleDataException
import com.example.myapplication.data.InfectionRuleRepository
import com.example.myapplication.data.loadDonationRecords
import com.example.myapplication.data.loadDonorProfile
import com.example.myapplication.data.loadPlannedDonations
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.PlannedStatus
import com.example.myapplication.domain.Reminder
import com.example.myapplication.domain.ReminderMemory
import com.example.myapplication.domain.TYPE_INFO
import com.example.myapplication.domain.checkPlan
import com.example.myapplication.domain.checkReminders
import com.example.myapplication.domain.healthRestrictions
import com.example.myapplication.domain.nextEligibleByType
import com.example.myapplication.domain.nextReminderCheck
import com.example.myapplication.domain.reminderText
import com.example.myapplication.domain.upcomingPlan
import com.example.myapplication.model.DonationType
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "donation_reminders"
private const val KEY_ENABLED = "enabled"
private const val KEY_NEXT_CHECK = "next_check_millis"
private const val KEY_ELIGIBLE_TYPES = "eligible_types"
private const val KEY_REMINDED_PLAN_DATE = "reminded_plan_date"

private const val CHANNEL_ID = "donation_reminders"
private const val ACTION_CHECK = "com.example.myapplication.reminder.CHECK"
private const val NOTIFICATION_ELIGIBLE = 1
private const val NOTIFICATION_PLAN = 2

/** The daily check may run up to this long after its time, which lets the phone batch it. */
private val CHECK_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(30)

/**
 * 헌혈 알림: once a morning, works out the next eligible dates and the plan the way 현황 does and
 * notifies when a type has become possible, or a donation is planned for tomorrow. Everything is
 * computed on the phone from what's saved; nothing is sent anywhere.
 *
 * The check is a one-off alarm set again after each run, rather than a repeating one, whose first
 * run Android may hold back for most of a day. Its time is saved so that relaunching the app or
 * rebooting puts back the same alarm - or runs a missed check straight away - instead of skipping
 * a day.
 */
object DonationReminders {

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Android 13+ asks before an app may notify; earlier versions allow it unless turned off. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Whether a notification posted now would show. */
    fun canNotify(context: Context): Boolean =
        !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The system screen where the donor can allow this app's notifications. */
    fun notificationSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }

    /**
     * Turns reminders on: notes how things stand now, so the first check only reminds about what
     * changes from here, and sets the first check for the coming morning.
     */
    suspend fun enable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, true) }
        ensureChannel(context)
        scheduleNext(context)
        runCheck(context, notify = false)
    }

    fun disable(context: Context) {
        alarmManager(context)?.cancel(checkIntent(context))
        prefs(context).edit { clear() }
    }

    /**
     * Puts the saved check back in place after a reboot, an update or the app being stopped. A check
     * whose time has passed runs right away.
     */
    fun ensureScheduled(context: Context) {
        if (!isEnabled(context)) return
        val saved = prefs(context).getLong(KEY_NEXT_CHECK, 0L)
        if (saved > 0L) setAlarm(context, saved) else scheduleNext(context)
    }

    internal fun scheduleNext(context: Context) {
        if (!isEnabled(context)) return
        val at = nextReminderCheck(ZonedDateTime.now()).toInstant().toEpochMilli()
        prefs(context).edit { putLong(KEY_NEXT_CHECK, at) }
        setAlarm(context, at)
    }

    /** An inexact window rather than an exact alarm, which would need a permission of its own. */
    private fun setAlarm(context: Context, atMillis: Long) {
        alarmManager(context)?.setWindow(AlarmManager.RTC_WAKEUP, atMillis, CHECK_WINDOW_MILLIS, checkIntent(context))
    }

    private fun alarmManager(context: Context): AlarmManager? =
        ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun checkIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Works out today's reminders from what's saved and posts them when [notify]; always saves what
     * to compare with next time. Rules that fail to load leave out the 감염병·체류 restrictions
     * rather than the whole check, as 현황 does.
     */
    internal suspend fun runCheck(context: Context, notify: Boolean) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val today = LocalDate.now()
        val profile = loadDonorProfile(appContext)
        val records = loadDonationRecords(appContext, profile)
        val plan = upcomingPlan(loadPlannedDonations(appContext), today)
        val db = AppDatabase.getInstance(appContext)
        val medications = db.medicationHistoryDao().getAll()
        val restrictions = try {
            val rules = InfectionRuleRepository(appContext).loadRules()
            healthRestrictions(db.healthHistoryDao().getAllDiseases(), db.healthHistoryDao().getAllStays(), rules)
        } catch (e: InfectionRuleDataException) {
            emptyList()
        }

        val nextDates = nextEligibleByType(records, medications, today, restrictions)
            .mapValues { (_, result) -> (result as? IntegratedNextResult.Eligible)?.nextDate }
        val planned = checkPlan(plan, records, medications, restrictions)
            .map { PlannedStatus(it.planned.type, it.planned.date, it.allowedFrom) }

        val prefs = prefs(appContext)
        val previous = if (notify) loadMemory(prefs) else null
        val check = checkReminders(today, nextDates, planned, previous)
        saveMemory(prefs, check.memory)
        if (notify && isEnabled(appContext)) check.reminders.forEach { post(appContext, it) }
    }

    private fun loadMemory(prefs: SharedPreferences): ReminderMemory? {
        val types = prefs.getString(KEY_ELIGIBLE_TYPES, null) ?: return null
        return ReminderMemory(
            eligibleTypes = types.split(",").mapNotNull { name -> DonationType.entries.firstOrNull { it.name == name } }.toSet(),
            remindedPlanDate = prefs.getString(KEY_REMINDED_PLAN_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )
    }

    private fun saveMemory(prefs: SharedPreferences, memory: ReminderMemory) {
        prefs.edit {
            putString(KEY_ELIGIBLE_TYPES, memory.eligibleTypes.joinToString(",") { it.name })
            if (memory.remindedPlanDate != null) putString(KEY_REMINDED_PLAN_DATE, memory.remindedPlanDate.toString())
            else remove(KEY_REMINDED_PLAN_DATE)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "헌혈 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "다시 헌혈할 수 있게 된 날과 헌혈 계획 전날 알려 드려요."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun post(context: Context, reminder: Reminder) {
        // Checked here, right before posting: the permission can be taken away at any time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val text = reminderText(reminder) { TYPE_INFO.getValue(it).label }
        val id = when (reminder) {
            is Reminder.NowEligible -> NOTIFICATION_ELIGIBLE
            is Reminder.PlanTomorrow -> NOTIFICATION_PLAN
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_donation)
            .setColor(0xFFDC2626.toInt())
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/** Runs the morning check, and puts the alarm back after a reboot or an app update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> DonationReminders.ensureScheduled(context)
            ACTION_CHECK -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        DonationReminders.runCheck(context, notify = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Uncaught, it would crash the app in the background; tomorrow's check tries again.
                        Log.w("DonationReminders", "Reminder check failed", e)
                    } finally {
                        // Set tomorrow's even if this one failed, so one bad day doesn't end reminders.
                        DonationReminders.scheduleNext(context)
                        pending.finish()
                    }
                }
            }
        }
    }
}

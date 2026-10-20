package com.example.myapplication.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.PlannedDonation
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonationStateStoreTest {

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val opened = mutableSetOf<String>()

    /** Gives the store its own prefs file, so these tests never touch the app's real profile. */
    private val context = object : ContextWrapper(appContext) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val testName = "test_$name"
            opened += testName
            return super.getSharedPreferences(testName, mode)
        }
    }

    @After
    fun deleteTestPrefs() {
        opened.forEach { appContext.deleteSharedPreferences(it) }
    }

    @Test
    fun preferredProvinceIsSavedWithTheProfile() {
        saveDonorProfile(context, DonorProfile(name = "홍길동", preferredProvince = "부산"))

        val loaded = loadDonorProfile(context)
        assertEquals("부산", loaded.preferredProvince)
        assertEquals("홍길동", loaded.name)
    }

    @Test
    fun clearingThePreferredProvinceForgetsIt() {
        saveDonorProfile(context, DonorProfile(preferredProvince = "부산"))
        saveDonorProfile(context, DonorProfile(preferredProvince = null))

        assertNull(loadDonorProfile(context).preferredProvince)
    }

    @Test
    fun aPlanIsSavedAndClearedWithoutTouchingTheProfile() {
        saveDonorProfile(context, DonorProfile(name = "홍길동"))
        val plan = listOf(PlannedDonation(1, DonationType.PLATELET, LocalDate.of(2026, 10, 24)))

        savePlannedDonations(context, plan)
        assertEquals(plan, loadPlannedDonations(context))

        savePlannedDonations(context, emptyList())
        assertEquals(emptyList<PlannedDonation>(), loadPlannedDonations(context))
        assertEquals("홍길동", loadDonorProfile(context).name)
    }
}

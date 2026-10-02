package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.DeferralPeriod
import com.example.myapplication.model.DiseaseRule
import com.example.myapplication.model.DomesticMalariaRegion
import com.example.myapplication.model.DomesticMalariaRules
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.InfectionRules
import com.example.myapplication.model.InternationalMalariaRules
import com.example.myapplication.model.MalariaCountry
import com.example.myapplication.model.MalariaRestriction
import com.example.myapplication.model.OverseasTravelRule
import com.example.myapplication.model.VcjdRule
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.Period
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.SAXException

const val INFECTION_RULES_ASSET = "blood_donation_rules.xml"

/** Raised when the bundled rules are missing or don't say what a restriction needs. */
class InfectionRuleDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun fail(message: String): Nothing = throw InfectionRuleDataException("$INFECTION_RULES_ASSET: $message")

private fun Element.children(tag: String): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
}

private fun Element.child(tag: String): Element = children(tag).firstOrNull() ?: fail("<$tagName> has no <$tag>")

private fun Element.texts(tag: String): List<String> =
    children(tag).map { it.textContent.trim() }.filter { it.isNotEmpty() }

private fun Element.attr(name: String): String =
    getAttribute(name).trim().takeIf { it.isNotEmpty() } ?: fail("<$tagName> has no $name")

private val PERIOD_PATTERN = Regex("""(\d+)\s*(년|개월|일)""")

/** "3년" / "6개월" / "0일", wherever they appear in the text - so "연중 6개월 이상" reads as 6 months. */
internal fun parsePeriod(text: String): Period? {
    val match = PERIOD_PATTERN.find(text) ?: return null
    val amount = match.groupValues[1].toInt()
    return when (match.groupValues[2]) {
        "년" -> Period.ofYears(amount)
        "개월" -> Period.ofMonths(amount)
        else -> Period.ofDays(amount)
    }
}

internal fun parseDeferral(text: String): DeferralPeriod = when {
    "영구" in text -> DeferralPeriod.Permanent
    "치료종료" in text.replace(" ", "") -> DeferralPeriod.UntilTreatmentEnds
    else -> DeferralPeriod.After(parsePeriod(text) ?: fail("unknown deferral period \"$text\""))
}

private val YEAR_RANGE_PATTERN = Regex("""(\d{4})년?\s*~\s*(\d{4})년""")

/** "1980년~1996년" → 1980-01-01..1996-12-31: the whole of both end years counts. */
private fun parseYearRange(text: String): Pair<LocalDate, LocalDate> {
    val match = YEAR_RANGE_PATTERN.find(text) ?: fail("unknown period \"$text\"")
    val (from, to) = match.destructured
    return LocalDate.of(from.toInt(), 1, 1) to LocalDate.of(to.toInt(), 12, 31)
}

private fun Element.toRestriction(): MalariaRestriction {
    val allowed = buildSet {
        if (attr("wholeBlood") == "가능") add(DonationType.WHOLE_BLOOD)
        if (attr("platelet") == "가능") add(DonationType.PLATELET)
        if (attr("plasma") == "가능") add(DonationType.PLASMA)
    }
    val deferralText = attr("deferralPeriod")
    return MalariaRestriction(allowed, parsePeriod(deferralText) ?: fail("unknown deferralPeriod \"$deferralText\""))
}

private fun parseDiseases(root: Element): List<DiseaseRule> =
    root.child("InfectiousDiseases").children("Deferral").flatMap { deferral ->
        val periodText = deferral.attr("period")
        val period = parseDeferral(periodText)
        deferral.texts("Disease").map { DiseaseRule(it, period, periodText) }
    }

private fun parseVcjd(root: Element): List<VcjdRule> =
    root.child("vCJD_Regions").children("Rule").flatMap { rule ->
        if (parseDeferral(rule.attr("deferralType")) != DeferralPeriod.Permanent) {
            fail("only permanent vCJD rules are supported")
        }
        rule.children("Region").map { region ->
            val targetPeriod = region.attr("targetPeriod")
            val (from, to) = parseYearRange(targetPeriod)
            val minStayText = region.attr("minStay")
            VcjdRule(
                countries = region.attr("country").split('·', ',').map { it.trim() }.filter { it.isNotEmpty() },
                includedAreas = region.texts("IncludedArea"),
                from = from,
                to = to,
                minStay = parsePeriod(minStayText) ?: fail("unknown minStay \"$minStayText\""),
                targetPeriodText = targetPeriod,
                minStayText = minStayText
            )
        }
    }

private fun parseDomestic(root: Element): DomesticMalariaRules {
    val domestic = root.child("MalariaDomestic")
    val conditions = domestic.children("Condition")
    val overnight = conditions.firstOrNull { it.getAttribute("type") == "숙박" } ?: fail("no 숙박 condition")
    val offshore = conditions.firstOrNull { it.getAttribute("type") == "해상숙박" } ?: fail("no 해상숙박 condition")
    val minDuration = overnight.attr("minDuration")
    return DomesticMalariaRules(
        activeYearText = domestic.getAttribute("activeYear").trim(),
        minNights = Regex("""(\d+)\s*박""").find(minDuration)?.groupValues?.get(1)?.toInt()
            ?: fail("unknown minDuration \"$minDuration\""),
        overnight = overnight.child("Restriction").toRestriction(),
        offshore = offshore.child("Restriction").toRestriction(),
        offshoreConditionText = offshore.getAttribute("exceptionCondition").trim(),
        regions = domestic.child("Regions").children("Province").flatMap { province ->
            val note = province.getAttribute("note").trim().takeIf { it.isNotEmpty() }
            province.texts("City").map { city ->
                DomesticMalariaRegion(
                    province = province.attr("name"),
                    city = city,
                    followsInternationalRules = note?.contains("국외") == true,
                    note = note
                )
            }
        }
    )
}

private fun parseInternational(root: Element): InternationalMalariaRules {
    val international = root.child("MalariaInternational")
    val conditions = international.child("Conditions").children("Condition")
    val residence = conditions.firstOrNull { "거주" in it.getAttribute("type") } ?: fail("no 거주 condition")
    val travel = conditions.firstOrNull { "여행" in it.getAttribute("type") } ?: fail("no 여행 condition")
    val residenceDuration = residence.attr("duration")
    return InternationalMalariaRules(
        residenceMinStay = parsePeriod(residenceDuration) ?: fail("unknown duration \"$residenceDuration\""),
        residence = residence.child("Restriction").toRestriction(),
        travel = travel.child("Restriction").toRestriction(),
        residenceText = residenceDuration,
        travelText = travel.attr("duration"),
        countries = international.child("Continents").children("Continent").flatMap { continent ->
            val name = continent.attr("name")
            continent.children("EntireRegion").flatMap { it.texts("Country") }
                .map { MalariaCountry(it, name, entireRegion = true) } +
                continent.children("PartialRegion").flatMap { it.texts("Country") }
                    .map { MalariaCountry(it, name, entireRegion = false) }
        }
    )
}

private fun parseOverseasTravel(root: Element): OverseasTravelRule {
    val travel = root.child("OverseasTravel")
    return OverseasTravelRule(
        excludedCountry = travel.attr("excludedCountry"),
        restriction = travel.child("Restriction").toRestriction()
    )
}

/**
 * Parses the `blood_donation_rules.xml` payload. Uses the JDK's DOM parser rather than Android's
 * XmlPullParser so it runs directly in JVM unit tests. Anything a restriction would be computed
 * from - a period, a threshold, a section - that's missing or unreadable throws
 * [InfectionRuleDataException] instead of silently becoming "no restriction".
 */
fun parseInfectionRules(input: InputStream): InfectionRules {
    val root = try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement
    } catch (e: SAXException) {
        throw InfectionRuleDataException("$INFECTION_RULES_ASSET is not valid XML", e)
    }
    val rules = InfectionRules(
        diseases = parseDiseases(root),
        vcjdRules = parseVcjd(root),
        domesticMalaria = parseDomestic(root),
        internationalMalaria = parseInternational(root),
        overseasTravel = parseOverseasTravel(root)
    )
    if (rules.diseases.isEmpty()) fail("contained no diseases")
    return rules
}

/** Reads and parses the bundled rules, caching the result for the process lifetime. */
class InfectionRuleRepository(
    context: Context,
    private val assetName: String = INFECTION_RULES_ASSET
) {
    private val appContext = context.applicationContext

    @Volatile
    private var cached: InfectionRules? = null

    suspend fun loadRules(): InfectionRules = withContext(Dispatchers.IO) {
        cached ?: readAsset().also { cached = it }
    }

    private fun readAsset(): InfectionRules = try {
        appContext.assets.open(assetName).use(::parseInfectionRules)
    } catch (e: IOException) {
        throw InfectionRuleDataException("Could not read asset $assetName", e)
    }
}

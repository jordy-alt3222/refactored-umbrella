package com.servicefinder.app

import com.servicefinder.app.data.Ref
import com.servicefinder.app.data.Rule
import com.servicefinder.app.data.Schedule
import com.servicefinder.app.data.ScheduleItem
import com.servicefinder.app.domain.describeRule
import com.servicefinder.app.domain.planService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePlanTest {
    private val ref = Ref("test", 1)

    private fun item(name: String, vararg rules: Rule, engines: List<String> = emptyList()) =
        ScheduleItem(name = name, engineIds = engines, rules = rules.toList(), ref = ref)

    private fun schedule(vararg items: ScheduleItem, baseKm: Int? = 15_000) =
        Schedule(id = "s", title = "Normal", baseKm = baseKm, ref = ref, items = items.toList())

    private val oil = item("Engine oil", Rule("replace", everyKm = 15_000, everyMonths = 12))
    private val coolant = item(
        "Coolant",
        Rule("replace", firstKm = 210_000, firstMonths = 120, everyKm = 30_000, everyMonths = 24),
    )
    private val plugs = item(
        "Spark plugs",
        Rule("inspect", everyKm = 15_000, everyMonths = 12),
        Rule("replace", everyKm = 60_000, everyMonths = 48),
    )

    @Test
    fun plansTheFirstServiceAtOrAfterTheOdometerReading() {
        assertEquals(15_000, planService(schedule(oil), null, 14_200)!!.serviceKm)
        assertEquals(15_000, planService(schedule(oil), null, 15_000)!!.serviceKm)
        assertEquals(30_000, planService(schedule(oil), null, 15_001)!!.serviceKm)
        assertEquals(15_000, planService(schedule(oil), null, 0)!!.serviceKm)
    }

    @Test
    fun firstIntervalIsRespected() {
        val early = planService(schedule(coolant), null, 30_000)!!
        assertTrue(early.dueAtService.isEmpty())
        assertEquals(210_000, early.later.single().dueKm)

        assertEquals(1, planService(schedule(coolant), null, 210_000)!!.dueAtService.size)
        assertTrue(planService(schedule(coolant), null, 225_000)!!.dueAtService.isEmpty())
        assertEquals(1, planService(schedule(coolant), null, 240_000)!!.dueAtService.size)
    }

    @Test
    fun replaceWinsOverInspectWhenBothFallDue() {
        assertEquals("inspect", planService(schedule(plugs), null, 45_000)!!.dueAtService.single().action)
        assertEquals("replace", planService(schedule(plugs), null, 60_000)!!.dueAtService.single().action)
    }

    @Test
    fun oneOffRuleIsDueOnlyOnce() {
        val once = item("Valve clearance", Rule("inspect", atKm = listOf(90_000), atMonths = listOf(72)))
        assertEquals(90_000, planService(schedule(once), null, 10_000)!!.later.single().dueKm)
        assertEquals(1, planService(schedule(once), null, 90_000)!!.dueAtService.size)
        val after = planService(schedule(once), null, 100_000)!!
        assertTrue(after.dueAtService.isEmpty() && after.later.isEmpty())
        assertEquals(1, after.other.size)
    }

    @Test
    fun irregularMarksAreEachDue() {
        val marks = item("Drive belt", Rule("inspect", atKm = listOf(60_000, 105_000)))
        assertEquals(1, planService(schedule(marks), null, 60_000)!!.dueAtService.size)
        assertEquals(105_000, planService(schedule(marks), null, 75_000)!!.later.single().dueKm)
    }

    @Test
    fun timeOnlyAndNoteItemsAreListedSeparately() {
        val brakeFluid = item("Brake fluid", Rule("replace", everyMonths = 24))
        val note = item("Air conditioning", Rule("none", note = "No check required"))
        val plan = planService(schedule(oil, brakeFluid, note), null, 15_000)!!
        assertEquals(listOf("Engine oil"), plan.dueAtService.map { it.name })
        assertEquals(listOf("Brake fluid", "Air conditioning"), plan.other.map { it.name })
    }

    @Test
    fun itemsForOtherEnginesAreLeftOut() {
        val diesel = item("Fuel filter", Rule("replace", everyKm = 30_000), engines = listOf("diesel"))
        assertTrue(planService(schedule(diesel), "petrol", 30_000)!!.dueAtService.isEmpty())
        assertEquals(1, planService(schedule(diesel), "diesel", 30_000)!!.dueAtService.size)
        assertEquals(1, planService(schedule(diesel), null, 30_000)!!.dueAtService.size)
    }

    @Test
    fun baseIntervalFallsBackToTheShortestDistanceRule() {
        assertEquals(30_000, planService(schedule(coolant, baseKm = null), null, 1)!!.serviceKm)
        assertNull(planService(schedule(item("Brake fluid", Rule("replace", everyMonths = 24)), baseKm = null), null, 1))
    }

    @Test
    fun rulesAreDescribedInPlainEnglish() {
        assertTrue(describeRule(oil.rules.single()).matches(Regex("Replace every 15.000 km or 12 months")))
        assertTrue(describeRule(coolant.rules.single()).startsWith("Replace first at 210"))
    }
}

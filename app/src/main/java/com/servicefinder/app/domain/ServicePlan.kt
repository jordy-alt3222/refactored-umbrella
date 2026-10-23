package com.servicefinder.app.domain

import com.servicefinder.app.data.Ref
import com.servicefinder.app.data.Rule
import com.servicefinder.app.data.Schedule
import com.servicefinder.app.data.ScheduleItem
import java.text.NumberFormat
import java.util.Locale

/** One maintenance item as it applies to the service being planned. */
data class PlannedItem(
    val name: String,
    val qualifier: String?,
    /** Action at [dueKm] ("replace", "inspect", ...). */
    val action: String,
    /** Odometer reading at which the action is next due, or null for time-only and note-only items. */
    val dueKm: Int?,
    /** The interval as written for the technician, e.g. "Replace every 15,000 km or 12 months". */
    val interval: String,
    val ref: Ref,
)

data class ServicePlan(
    /** The scheduled service being planned: the first service at or after the odometer reading. */
    val serviceKm: Int,
    val dueAtService: List<PlannedItem>,
    /** Items not due at [serviceKm], soonest first. */
    val later: List<PlannedItem>,
    /** Items with a time interval only, or a note instead of an interval. */
    val other: List<PlannedItem>,
)

/**
 * Works out which items of a manufacturer's schedule fall due at the next service.
 *
 * Distance rules repeat every `everyKm`, starting at `firstKm` (or at `everyKm`
 * when there is no separate first interval); one-off rules happen at `atKm`. The
 * planned service is the first multiple of the schedule's base interval at or
 * after [odometerKm]. Time limits ("or 12 months, whichever comes first") are
 * kept in the interval text, because the app does not know the vehicle's age.
 */
fun planService(schedule: Schedule, engineId: String?, odometerKm: Int): ServicePlan? {
    val items = schedule.items.filter { engineId == null || it.engineIds.isEmpty() || engineId in it.engineIds }
    val base = schedule.baseKm
        ?: items.flatMap { it.rules }.mapNotNull { it.everyKm }.minOrNull()
        ?: return null
    val km = odometerKm.coerceAtLeast(0)
    val serviceKm = maxOf(base, ((km + base - 1) / base) * base)

    val due = mutableListOf<PlannedItem>()
    val later = mutableListOf<PlannedItem>()
    val other = mutableListOf<PlannedItem>()
    for (item in items) {
        val distanceRules = item.rules.filter { it.everyKm != null || it.atKm != null }
        if (distanceRules.isEmpty()) {
            other += item.planned(item.rules.firstOrNull()?.action ?: "none", null)
            continue
        }
        val dueNow = distanceRules.filter { isDueAt(it, serviceKm) }
        if (dueNow.isNotEmpty()) {
            due += item.planned(strongest(dueNow).action, serviceKm)
        } else {
            val next = distanceRules.mapNotNull { r -> nextAfter(r, serviceKm)?.let { r to it } }.minByOrNull { it.second }
            if (next != null) later += item.planned(next.first.action, next.second)
            else other += item.planned(distanceRules.first().action, null)
        }
    }
    return ServicePlan(serviceKm, due, later.sortedBy { it.dueKm }, other)
}

internal fun isDueAt(rule: Rule, km: Int): Boolean {
    rule.atKm?.let { return it == km }
    val every = rule.everyKm ?: return false
    val start = rule.firstKm ?: every
    return km >= start && (km - start) % every == 0
}

/** The first odometer reading after [km] at which the rule falls due, or null if it never will. */
internal fun nextAfter(rule: Rule, km: Int): Int? {
    rule.atKm?.let { return if (it > km) it else null }
    val every = rule.everyKm ?: return null
    val start = rule.firstKm ?: every
    if (km < start) return start
    return start + ((km - start) / every + 1) * every
}

private val ACTION_ORDER = listOf("replace", "add", "clean", "tighten", "inspect", "none")

private fun strongest(rules: List<Rule>): Rule =
    rules.minBy { ACTION_ORDER.indexOf(it.action).let { i -> if (i < 0) ACTION_ORDER.size else i } }

private fun ScheduleItem.planned(action: String, dueKm: Int?) =
    PlannedItem(name, qualifier, action, dueKm, text ?: rules.joinToString("; ") { describeRule(it) }, ref)

fun actionLabel(action: String): String = when (action) {
    "replace" -> "Replace"
    "inspect" -> "Inspect"
    "add" -> "Add"
    "clean" -> "Clean"
    "tighten" -> "Tighten"
    else -> ""
}

fun formatKm(km: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(km) + " km"

/** Plain-English description of a rule, e.g. "Inspect every 15,000 km or 12 months". */
fun describeRule(rule: Rule): String {
    fun limit(km: Int?, months: Int?) = listOfNotNull(km?.let(::formatKm), months?.let { "$it months" })
        .joinToString(" or ")
    val verb = actionLabel(rule.action)
    val body = when {
        rule.atKm != null || rule.atMonths != null -> "once at ${limit(rule.atKm, rule.atMonths)}"
        rule.firstKm != null || rule.firstMonths != null ->
            "first at ${limit(rule.firstKm, rule.firstMonths)}, then every ${limit(rule.everyKm, rule.everyMonths)}"
        rule.everyKm != null || rule.everyMonths != null -> "every ${limit(rule.everyKm, rule.everyMonths)}"
        else -> ""
    }
    return listOf(verb, body, rule.note.orEmpty()).filter { it.isNotBlank() }.joinToString(" ").trim()
}

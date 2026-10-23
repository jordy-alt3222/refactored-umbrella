package com.servicefinder.app.ui.vehicle

import com.servicefinder.app.data.Engine
import com.servicefinder.app.data.Schedule
import com.servicefinder.app.data.Tpms
import com.servicefinder.app.data.TyrePressure
import com.servicefinder.app.data.Vehicle
import com.servicefinder.app.data.WheelTorque

/*
 * Display helpers for vehicle figures. Nothing here derives or estimates a value:
 * figures are shown exactly as the source gives them, and an absent figure is
 * reported with the reason recorded in the catalogue's gaps.
 */

const val PROBLEMS_ONLY_REASON =
    "No owner's manual for this model could be obtained legally, so only common problems are listed."

const val CALIPER_REASON =
    "Brake caliper bolt torques are in the manufacturer's workshop manual, which was not accessible; " +
        "owner's manuals do not give them."

/** The catalogue's recorded reason for a missing figure, matched by keyword. */
fun Vehicle?.gapReason(vararg keywords: String, fallback: String): String {
    if (this == null) return PROBLEMS_ONLY_REASON
    return gaps.firstOrNull { g -> keywords.any { g.contains(it, ignoreCase = true) } } ?: fallback
}

fun Vehicle.oilFor(engine: Engine?) = if (engine == null) oil else oil.filter { it.engineId == engine.id }

fun Vehicle.tyresFor(engine: Engine?): List<TyrePressure> =
    if (engine == null) tyres else tyres.filter { it.engineIds == null || engine.id in it.engineIds }

fun Vehicle.schedulesFor(engine: Engine?): List<Schedule> =
    if (engine == null) schedules else schedules.filter { it.engineIds.isEmpty() || engine.id in it.engineIds }

fun Vehicle.engineName(id: String) = engines.firstOrNull { it.id == id }?.name ?: id

fun WheelTorque.valueText(): String? = when {
    minNm == null && maxNm == null -> null
    minNm == null || maxNm == null || minNm == maxNm -> "${minNm ?: maxNm} N·m"
    else -> "$minNm–$maxNm N·m"
}

fun TyrePressure.valueText(): String =
    listOfNotNull(frontKpa?.let { "Front $it kPa" }, rearKpa?.let { "Rear $it kPa" })
        .joinToString(" · ").ifEmpty { "Not available" }

fun Tpms.label(): String = when (status) {
    "standard" -> "TPMS fitted as standard"
    "optional" -> "TPMS fitted on some versions"
    "none" -> "No TPMS fitted"
    else -> "TPMS fitment not stated"
} + when (system) {
    "direct" -> " (direct, wheel sensors)"
    "indirect" -> " (indirect, via ABS)"
    else -> ""
}

fun Double.litres(): String = (if (this % 1.0 == 0.0) toInt().toString() else toString()) + " L"

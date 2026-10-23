package com.servicefinder.app.domain

import com.servicefinder.app.data.ProblemModelSummary
import com.servicefinder.app.data.Selection
import com.servicefinder.app.data.Vehicle

/** One choice in the model list: either a catalogue vehicle, or a model with common-problem data only. */
data class ModelOption(
    val make: String,
    val model: String,
    val series: String?,
    val market: String?,
    val yearFrom: Int,
    val yearTo: Int,
    val vehicleId: String?,
    /** Engines listed for the vehicle; the engine step is skipped when there are fewer than two. */
    val engineIds: List<String> = emptyList(),
) {
    val key: String get() = vehicleId ?: "$make|$model"
    val years: IntRange get() = yearFrom..yearTo
    val yearsLabel: String get() = if (yearFrom == yearTo) "$yearFrom" else "$yearFrom–$yearTo"
}

fun modelOptions(vehicles: List<Vehicle>, problemModels: List<ProblemModelSummary>): List<ModelOption> {
    val fromVehicles = vehicles.map {
        ModelOption(it.make, it.model, it.series, it.market, it.yearFrom, it.yearTo, it.id, it.engines.map { e -> e.id })
    }
    val problemOnly = problemModels.map { ModelOption(it.make, it.model, null, null, it.yearFrom, it.yearTo, null) }
    return (fromVehicles + problemOnly).sortedWith(
        compareBy<ModelOption>({ it.make.lowercase() }, { it.model.lowercase() }, { it.vehicleId == null }, { -it.yearTo }),
    )
}

fun ModelOption.toSelection(year: Int, engineId: String?) =
    Selection(make = make, model = model, vehicleId = vehicleId, year = year, engineId = engineId)

/** Short description of a selection, e.g. "2021 Mercedes-Benz C-Class". */
fun Selection.title(): String = "$year $make $model"

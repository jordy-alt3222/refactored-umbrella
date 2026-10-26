package com.serviceadvisor.app.data

import kotlinx.serialization.json.Json

/** Parses and checks a service data file. Pure Kotlin so the unit tests can run it on the JVM. */
object DatasetParser {
    private val json = Json { ignoreUnknownKeys = true }

    class InvalidDataset(val problems: List<String>) :
        Exception(problems.take(5).joinToString("\n") + if (problems.size > 5) "\n…and ${problems.size - 5} more" else "")

    /** Parses [text] and throws [InvalidDataset] if it is not a usable data file. */
    fun parse(text: String): Dataset {
        val dataset = try {
            json.decodeFromString<Dataset>(text)
        } catch (e: IllegalArgumentException) {
            throw InvalidDataset(listOf("Not a Service Advisor data file: ${e.message?.lineSequence()?.firstOrNull()}"))
        }
        val problems = problems(dataset)
        if (problems.isNotEmpty()) throw InvalidDataset(problems)
        return dataset
    }

    /** Every reason the dataset can't be used safely. Empty when it is fine. */
    fun problems(dataset: Dataset): List<String> = buildList {
        val procedures = dataset.procedures.associateBy { it.id }
        if (dataset.vehicles.isEmpty()) add("The file has no vehicles.")
        dataset.procedures.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { add("Procedure id '$it' is used more than once.") }
        dataset.vehicles.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { add("Vehicle id '$it' is used more than once.") }
        dataset.procedures.filter { it.steps.isEmpty() || it.steps.any(String::isBlank) }
            .forEach { add("Procedure '${it.id}' has an empty step.") }
        for (v in dataset.vehicles) {
            val service = procedures[v.serviceResetId]
            if (service == null) add("${v.id}: service reset '${v.serviceResetId}' does not exist.")
            else if (service.tpmsType != null) add("${v.id}: '${v.serviceResetId}' is a TPMS procedure, not a service reset.")
            if (v.tpmsResetId != null) {
                val tpms = procedures[v.tpmsResetId]
                if (tpms == null) add("${v.id}: TPMS reset '${v.tpmsResetId}' does not exist.")
                else if (tpms.tpmsType == null) add("${v.id}: '${v.tpmsResetId}' is not a TPMS procedure.")
            }
            if (v.oil.capacityLitres !in 1.0..30.0) add("${v.id}: oil capacity ${v.oil.capacityLitres} L looks wrong.")
            if (v.oil.grade.isBlank() || v.oil.spec.isBlank()) add("${v.id}: oil grade and spec are required.")
            if (v.oilFilter.ryco.isBlank()) add("${v.id}: Ryco filter number is required.")
            if (v.yearTo != null && v.yearTo < v.yearFrom) add("${v.id}: years are reversed.")
        }
    }
}

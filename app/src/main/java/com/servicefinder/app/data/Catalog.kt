package com.servicefinder.app.data

import kotlinx.serialization.Serializable

/**
 * The offline catalogue, as written by tools/build_data.py into assets/catalog.json.
 * Every figure carries a [Ref] to the document and page it came from; a missing
 * figure is simply absent (null or an empty list) and is shown as "Not available".
 */
@Serializable
data class Catalog(
    val version: Int,
    val generated: String,
    val sources: List<Source>,
    val vehicles: List<Vehicle>,
    val fuses: List<Fuse>,
    val problems: List<Problem>,
    val procedures: List<Procedure>,
)

@Serializable
data class Source(
    val id: String,
    val kind: String,
    val title: String,
    val publisher: String,
    val url: String? = null,
    val market: String? = null,
    val pages: Int? = null,
    val retrieved: String? = null,
    val note: String? = null,
)

/** A citation: the source document, its PDF page and the page number printed on the paper. */
@Serializable
data class Ref(
    val sourceId: String,
    val page: Int? = null,
    val label: String? = null,
    val detail: String? = null,
)

@Serializable
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val series: String? = null,
    val yearFrom: Int,
    val yearTo: Int,
    val market: String? = null,
    val sourceId: String,
    val summary: String? = null,
    val engines: List<Engine> = emptyList(),
    val oil: List<OilCapacity> = emptyList(),
    val tyres: List<TyrePressure> = emptyList(),
    val tpms: Tpms? = null,
    val wheelTorques: List<WheelTorque> = emptyList(),
    val caliperTorque: List<CaliperTorque> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val gaps: List<String> = emptyList(),
    val fuseNote: String? = null,
)

@Serializable
data class Engine(val id: String, val name: String, val code: String? = null, val fuel: String? = null)

@Serializable
data class OilCapacity(
    val engineId: String,
    val litres: Double? = null,
    val basis: String? = null,
    val note: String? = null,
    val ref: Ref,
)

@Serializable
data class TyrePressure(
    val sizes: List<String> = emptyList(),
    val frontKpa: Int? = null,
    val rearKpa: Int? = null,
    val spare: Boolean = false,
    val condition: String? = null,
    val note: String? = null,
    val engineIds: List<String>? = null,
    val ref: Ref,
)

@Serializable
data class Tpms(val status: String, val system: String = "unknown", val text: String? = null, val ref: Ref)

@Serializable
data class WheelTorque(
    val minNm: Int? = null,
    val maxNm: Int? = null,
    val variant: String? = null,
    val text: String? = null,
    val note: String? = null,
    val ref: Ref,
)

@Serializable
data class CaliperTorque(
    val axle: String,
    val fastener: String,
    val nm: Int? = null,
    val text: String? = null,
    val note: String? = null,
    val variant: String? = null,
    val ref: Ref,
)

@Serializable
data class Schedule(
    val id: String,
    val title: String,
    val engineIds: List<String> = emptyList(),
    val baseKm: Int? = null,
    val baseMonths: Int? = null,
    val note: String? = null,
    val ref: Ref,
    val severeRef: Ref? = null,
    val items: List<ScheduleItem> = emptyList(),
    val severe: List<SevereItem> = emptyList(),
)

@Serializable
data class ScheduleItem(
    val name: String,
    val qualifier: String? = null,
    val engineIds: List<String> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val text: String? = null,
    val ref: Ref,
)

/**
 * One maintenance rule. A rule repeats every [everyKm]/[everyMonths] (whichever
 * comes first), optionally starting at [firstKm]/[firstMonths]; or happens once at
 * [atKm]/[atMonths]. Action "none" carries only a [note].
 */
@Serializable
data class Rule(
    val action: String,
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val firstKm: Int? = null,
    val firstMonths: Int? = null,
    val atKm: Int? = null,
    val atMonths: Int? = null,
    val note: String? = null,
)

@Serializable
data class SevereItem(
    val name: String,
    val qualifier: String? = null,
    val action: String,
    val interval: String,
    val engineIds: List<String> = emptyList(),
    val ref: Ref,
)

@Serializable
data class Fuse(
    val vehicleId: String,
    val box: String,
    val engineIds: List<String>? = null,
    val number: String? = null,
    val name: String? = null,
    val rating: String? = null,
    val amps: Double? = null,
    val circuit: String? = null,
    val unused: Boolean = false,
    val ref: Ref,
)

@Serializable
data class Problem(
    val id: String,
    val make: String,
    val applies: List<ProblemApplies> = emptyList(),
    val vehicleIds: List<String> = emptyList(),
    val title: String,
    val system: String,
    val engines: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val diagnosis: List<String> = emptyList(),
    val remedy: String? = null,
    val note: String? = null,
    val bulletins: List<Bulletin> = emptyList(),
    val ref: Ref,
)

@Serializable
data class ProblemApplies(val model: String, val yearFrom: Int, val yearTo: Int)

@Serializable
data class Bulletin(val id: String, val summary: String)

@Serializable
data class Procedure(
    val id: String,
    val title: String,
    val system: String,
    val appliesTo: String? = null,
    val summary: String? = null,
    val tools: List<String> = emptyList(),
    val safety: List<String> = emptyList(),
    val steps: List<ProcedureStep> = emptyList(),
    val results: List<String> = emptyList(),
    val ref: Ref,
)

@Serializable
data class ProcedureStep(val title: String, val detail: String? = null, val expected: String? = null)

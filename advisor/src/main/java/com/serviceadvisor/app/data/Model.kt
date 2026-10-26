package com.serviceadvisor.app.data

import kotlinx.serialization.Serializable

/**
 * The service data file: the bundled sample in assets/vehicles.json, or a file a workshop imports.
 *
 * Procedures are stored once and referenced by id, because one reset procedure usually covers a
 * whole make or platform.
 */
@Serializable
data class Dataset(
    val info: DatasetInfo,
    val procedures: List<Procedure>,
    val vehicles: List<Vehicle>,
)

@Serializable
data class DatasetInfo(
    val name: String,
    /** True only when someone has checked every record against the oil and filter makers' lookups. */
    val verified: Boolean = false,
    /** Who checked the data and when, shown in Settings and on each service sheet. */
    val verifiedBy: String? = null,
    val updated: String? = null,
    val notes: String? = null,
)

@Serializable
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    /** Chassis or generation code, e.g. "AN120" or "PX". */
    val series: String? = null,
    val yearFrom: Int,
    /** Null while the model is still on sale. */
    val yearTo: Int? = null,
    /** Engine description, e.g. "2.8L turbo diesel". */
    val engine: String,
    val engineCode: String? = null,
    val fuel: Fuel,
    val oil: OilSpec,
    val oilFilter: OilFilter,
    val serviceResetId: String,
    /** Null when the vehicle has no tyre pressure monitoring; the TPMS section is then hidden. */
    val tpmsResetId: String? = null,
    /** Per-record verification. A record is treated as verified only if this and the dataset agree. */
    val verified: Boolean = false,
    val notes: String? = null,
)

@Serializable
enum class Fuel { Petrol, Diesel, Hybrid }

@Serializable
data class OilSpec(
    /** Refill capacity in litres, including the filter unless [capacityNote] says otherwise. */
    val capacityLitres: Double,
    val capacityNote: String? = null,
    /** SAE viscosity grade, e.g. "0W-20". Alternatives (e.g. by climate) go in [gradeNote]. */
    val grade: String,
    val gradeNote: String? = null,
    /** Required performance spec(s), e.g. "API SP / ILSAC GF-6A". */
    val spec: String,
)

@Serializable
data class OilFilter(
    /** Ryco part number, e.g. "Z386". */
    val ryco: String,
    val type: FilterType = FilterType.SpinOn,
)

@Serializable
enum class FilterType(val label: String) { SpinOn("Spin-on"), Cartridge("Cartridge") }

@Serializable
data class Procedure(
    val id: String,
    val title: String,
    /** What is needed before starting, e.g. "Ignition ON, engine off, all doors shut". */
    val before: String? = null,
    val steps: List<String>,
    /** Short caution or follow-up shown under the last step. */
    val notes: String? = null,
    /** Direct (sensor in each wheel) or indirect (ABS wheel-speed) TPMS. Null for service resets. */
    val tpmsType: TpmsType? = null,
)

@Serializable
enum class TpmsType(val label: String) {
    Direct("Direct TPMS (sensor in each wheel)"),
    Indirect("Indirect TPMS (uses ABS wheel speed)"),
}

/** Display helpers. */
val Vehicle.title: String get() = "$make $model"

val Vehicle.years: String get() = if (yearTo == null) "$yearFrom–on" else "$yearFrom–$yearTo"

val Vehicle.variant: String
    get() = listOfNotNull(engine, engineCode?.let { "($it)" }).joinToString(" ")

val Vehicle.subtitle: String
    get() = listOfNotNull(series, years, variant).joinToString(" · ")

fun Vehicle.coversYear(year: Int): Boolean = year >= yearFrom && (yearTo == null || year <= yearTo)

/** "4.2 L", "7 L", "4.35 L": as written in the data, without padding zeros. */
fun Double.litres(): String = "${java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()} L"

/** A value is shown as verified only when both the file and the record say it was checked. */
fun Dataset.isVerified(vehicle: Vehicle): Boolean = info.verified && vehicle.verified

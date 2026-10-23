package com.servicefinder.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.zip.CRC32

sealed interface ImportState {
    data object Loading : ImportState
    data object Ready : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * Loads assets/catalog.json into Room (only when the asset has changed) and
 * exposes typed, decoded views of it.
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val readAsset: () -> ByteArray,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val importLock = Mutex()
    private val _state = MutableStateFlow<ImportState>(ImportState.Loading)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Imports the bundled catalogue if the database holds a different one. Safe to call repeatedly. */
    suspend fun ensureImported() = importLock.withLock {
        _state.value = ImportState.Loading
        _state.value = try {
            withContext(Dispatchers.IO) { importIfChanged() }
            ImportState.Ready
        } catch (e: Exception) {
            ImportState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun importIfChanged() {
        val bytes = readAsset()
        val checksum = CRC32().apply { update(bytes) }.value.toString(16)
        if (dao.meta(KEY_CHECKSUM) == checksum) return
        val catalog = json.decodeFromString(Catalog.serializer(), bytes.decodeToString())
        val vehicles = catalog.vehicles.associateBy { it.id }
        dao.replaceAll(
            vehicles = catalog.vehicles.map {
                VehicleEntity(it.id, it.make, it.model, it.series, it.yearFrom, it.yearTo,
                    json.encodeToString(Vehicle.serializer(), it))
            },
            fuses = catalog.fuses.mapIndexed { i, f ->
                val v = vehicles[f.vehicleId]
                FuseEntity(
                    vehicleId = f.vehicleId,
                    sortIndex = i,
                    box = f.box,
                    search = listOfNotNull(f.number, f.name, f.circuit, f.rating, f.box, v?.make, v?.model)
                        .joinToString(" ").lowercase(),
                    json = json.encodeToString(Fuse.serializer(), f),
                )
            },
            problems = catalog.problems.map { p ->
                ProblemEntity(
                    p.id, p.make, p.title, p.system,
                    search = (listOf(p.title, p.system) + p.engines + p.symptoms + p.bulletins.map { it.id })
                        .joinToString(" ").lowercase(),
                    json = json.encodeToString(Problem.serializer(), p),
                )
            },
            problemModels = catalog.problems.flatMap { p ->
                p.applies.map { ProblemModelEntity(p.id, p.make, it.model, it.yearFrom, it.yearTo) }
            },
            procedures = catalog.procedures.mapIndexed { i, p ->
                ProcedureEntity(p.id, i, p.title, p.system, json.encodeToString(Procedure.serializer(), p))
            },
            sources = catalog.sources.map { SourceEntity(it.id, json.encodeToString(Source.serializer(), it)) },
            meta = listOf(
                MetaEntity(KEY_CHECKSUM, checksum),
                MetaEntity(KEY_GENERATED, catalog.generated),
            ),
        )
    }

    fun vehicles(): Flow<List<Vehicle>> =
        dao.vehicles().map { rows -> rows.map { json.decodeFromString(Vehicle.serializer(), it.json) } }

    suspend fun vehicle(id: String): Vehicle? =
        dao.vehicle(id)?.let { json.decodeFromString(Vehicle.serializer(), it.json) }

    fun problemModels(): Flow<List<ProblemModelSummary>> = dao.problemModels()

    fun fuses(vehicleId: String, query: String): Flow<List<Fuse>> =
        dao.fuses(vehicleId, query.trim().lowercase()).map { rows ->
            rows.map { json.decodeFromString(Fuse.serializer(), it.json) }
        }

    fun fuseCount(vehicleId: String): Flow<Int> = dao.fuseCount(vehicleId)

    fun problemsFor(make: String, model: String, year: Int, query: String = ""): Flow<List<Problem>> =
        dao.problemsFor(make, model, year, query.trim().lowercase()).map(::decodeProblems)

    fun problems(make: String, query: String): Flow<List<Problem>> =
        dao.problems(make, query.trim().lowercase()).map(::decodeProblems)

    suspend fun problem(id: String): Problem? =
        dao.problem(id)?.let { json.decodeFromString(Problem.serializer(), it.json) }

    fun procedures(): Flow<List<Procedure>> =
        dao.procedures().map { rows -> rows.map { json.decodeFromString(Procedure.serializer(), it.json) } }

    suspend fun procedure(id: String): Procedure? =
        dao.procedure(id)?.let { json.decodeFromString(Procedure.serializer(), it.json) }

    fun sources(): Flow<Map<String, Source>> =
        dao.sources().map { rows ->
            rows.map { json.decodeFromString(Source.serializer(), it.json) }.associateBy { it.id }
        }

    private fun decodeProblems(rows: List<ProblemEntity>) =
        rows.map { json.decodeFromString(Problem.serializer(), it.json) }

    private companion object {
        const val KEY_CHECKSUM = "checksum"
        const val KEY_GENERATED = "generated"
    }
}

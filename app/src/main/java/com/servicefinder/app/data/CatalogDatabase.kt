package com.servicefinder.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * Room holds the imported catalogue so that fuses and problems can be searched
 * quickly. Each row keeps the original catalogue object as JSON alongside the
 * few columns that are filtered or sorted on.
 */

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val make: String,
    val model: String,
    val series: String?,
    val yearFrom: Int,
    val yearTo: Int,
    val json: String,
)

@Entity(tableName = "fuses", indices = [Index("vehicleId")])
data class FuseEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val vehicleId: String,
    val sortIndex: Int,
    val box: String,
    /** Lower-cased number, name, circuit, rating and box, for LIKE searches. */
    val search: String,
    val json: String,
)

@Entity(tableName = "problems")
data class ProblemEntity(
    @PrimaryKey val id: String,
    val make: String,
    val title: String,
    val system: String,
    val search: String,
    val json: String,
)

/** One model (and model-year range) a problem applies to. */
@Entity(tableName = "problem_models", primaryKeys = ["problemId", "model"], indices = [Index("make", "model")])
data class ProblemModelEntity(
    val problemId: String,
    val make: String,
    val model: String,
    val yearFrom: Int,
    val yearTo: Int,
)

@Entity(tableName = "procedures")
data class ProcedureEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val title: String,
    val system: String,
    val json: String,
)

@Entity(tableName = "sources")
data class SourceEntity(@PrimaryKey val id: String, val json: String)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

/** A make/model that has common-problem data, with the model years covered. */
data class ProblemModelSummary(val make: String, val model: String, val yearFrom: Int, val yearTo: Int)

@Dao
interface CatalogDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Query("SELECT * FROM vehicles ORDER BY make, model, yearFrom")
    fun vehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun vehicle(id: String): VehicleEntity?

    @Query(
        "SELECT make, model, MIN(yearFrom) AS yearFrom, MAX(yearTo) AS yearTo FROM problem_models " +
            "GROUP BY make, model ORDER BY make, model",
    )
    fun problemModels(): Flow<List<ProblemModelSummary>>

    @Query(
        "SELECT * FROM fuses WHERE vehicleId = :vehicleId AND (:query = '' OR search LIKE '%' || :query || '%') " +
            "ORDER BY sortIndex",
    )
    fun fuses(vehicleId: String, query: String): Flow<List<FuseEntity>>

    @Query("SELECT COUNT(*) FROM fuses WHERE vehicleId = :vehicleId")
    fun fuseCount(vehicleId: String): Flow<Int>

    @Query(
        "SELECT p.* FROM problems p JOIN problem_models m ON m.problemId = p.id " +
            "WHERE m.make = :make AND m.model = :model AND :year BETWEEN m.yearFrom AND m.yearTo " +
            "AND (:query = '' OR p.search LIKE '%' || :query || '%') ORDER BY p.system, p.title",
    )
    fun problemsFor(make: String, model: String, year: Int, query: String): Flow<List<ProblemEntity>>

    @Query(
        "SELECT * FROM problems WHERE (:make = '' OR make = :make) " +
            "AND (:query = '' OR search LIKE '%' || :query || '%') ORDER BY make, system, title",
    )
    fun problems(make: String, query: String): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun problem(id: String): ProblemEntity?

    @Query("SELECT * FROM procedures ORDER BY sortIndex")
    fun procedures(): Flow<List<ProcedureEntity>>

    @Query("SELECT * FROM procedures WHERE id = :id")
    suspend fun procedure(id: String): ProcedureEntity?

    @Query("SELECT * FROM sources ORDER BY id")
    fun sources(): Flow<List<SourceEntity>>

    @Insert suspend fun insertVehicles(rows: List<VehicleEntity>)
    @Insert suspend fun insertFuses(rows: List<FuseEntity>)
    @Insert suspend fun insertProblems(rows: List<ProblemEntity>)
    @Insert suspend fun insertProblemModels(rows: List<ProblemModelEntity>)
    @Insert suspend fun insertProcedures(rows: List<ProcedureEntity>)
    @Insert suspend fun insertSources(rows: List<SourceEntity>)
    @Insert suspend fun insertMeta(rows: List<MetaEntity>)

    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
    @Query("DELETE FROM fuses") suspend fun clearFuses()
    @Query("DELETE FROM problems") suspend fun clearProblems()
    @Query("DELETE FROM problem_models") suspend fun clearProblemModels()
    @Query("DELETE FROM procedures") suspend fun clearProcedures()
    @Query("DELETE FROM sources") suspend fun clearSources()
    @Query("DELETE FROM meta") suspend fun clearMeta()

    /** Replaces the whole catalogue in one transaction, so a failed import leaves the old one intact. */
    @Transaction
    suspend fun replaceAll(
        vehicles: List<VehicleEntity>,
        fuses: List<FuseEntity>,
        problems: List<ProblemEntity>,
        problemModels: List<ProblemModelEntity>,
        procedures: List<ProcedureEntity>,
        sources: List<SourceEntity>,
        meta: List<MetaEntity>,
    ) {
        clearVehicles(); clearFuses(); clearProblems(); clearProblemModels()
        clearProcedures(); clearSources(); clearMeta()
        insertVehicles(vehicles); insertFuses(fuses); insertProblems(problems)
        insertProblemModels(problemModels); insertProcedures(procedures)
        insertSources(sources); insertMeta(meta)
    }
}

@Database(
    entities = [
        VehicleEntity::class, FuseEntity::class, ProblemEntity::class, ProblemModelEntity::class,
        ProcedureEntity::class, SourceEntity::class, MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun dao(): CatalogDao

    companion object {
        fun create(context: Context): CatalogDatabase =
            // The database is only a cache of assets/catalog.json, so a schema change
            // simply rebuilds it from the asset.
            Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

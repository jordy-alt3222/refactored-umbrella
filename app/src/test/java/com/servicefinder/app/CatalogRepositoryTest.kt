package com.servicefinder.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.servicefinder.app.data.Catalog
import com.servicefinder.app.data.CatalogDatabase
import com.servicefinder.app.data.CatalogRepository
import com.servicefinder.app.data.ImportState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The repository's decoded views must match the bundled catalogue, however often they are read. */
@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest {
    private val bytes = File("src/main/assets/catalog.json").readBytes()
    private val catalog = Json { ignoreUnknownKeys = true }.decodeFromString(Catalog.serializer(), bytes.decodeToString())
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repo = CatalogRepository(db.dao()) { bytes }

    @After fun close() = db.close()

    @Test
    fun decodedViewsMatchTheCatalogue() = runBlocking {
        repo.ensureImported()
        assertEquals(ImportState.Ready, repo.state.value)

        val vehicles = catalog.vehicles.associateBy { it.id }
        assertEquals(vehicles, repo.vehicles().first().associateBy { it.id })
        // A second read is served from the cache and must still be equal (and the same objects).
        val again = repo.vehicles().first().associateBy { it.id }
        assertEquals(vehicles, again)
        vehicles.keys.forEach { id -> assertSame(again[id], repo.vehicle(id)) }

        val problems = catalog.problems.associateBy { it.id }
        assertEquals(problems, repo.problems("", "").first().associateBy { it.id })
        assertEquals(problems, repo.problems("", "").first().associateBy { it.id })
        problems.values.forEach { assertEquals(it, repo.problem(it.id)) }
        val vw = repo.problems("Volkswagen", "").first()
        assertEquals(problems.values.filter { it.make == "Volkswagen" }.map { it.id }.toSet(), vw.map { it.id }.toSet())

        val kia = catalog.vehicles.first { it.id == "kia-carnival-ka4" }
        assertEquals(catalog.fuses.filter { it.vehicleId == kia.id }, repo.fuses(kia.id, "").first())
        assertEquals(catalog.procedures, repo.procedures().first())
        catalog.procedures.forEach { assertEquals(it, repo.procedure(it.id)) }
        assertEquals(catalog.sources.associateBy { it.id }, repo.sources().first())
    }
}

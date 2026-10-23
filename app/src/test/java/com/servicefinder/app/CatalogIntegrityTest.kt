package com.servicefinder.app

import com.servicefinder.app.data.Catalog
import com.servicefinder.app.data.Ref
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the bundled catalogue the way the app reads it: it must decode with the
 * app's own model classes, and every figure must point at a known source and page.
 */
class CatalogIntegrityTest {
    private val catalog: Catalog by lazy {
        val file = File("src/main/assets/catalog.json")
        Json { ignoreUnknownKeys = true }.decodeFromString(Catalog.serializer(), file.readText())
    }

    private fun allRefs(): List<Pair<String, Ref>> = buildList {
        catalog.vehicles.forEach { v ->
            v.oil.forEach { add("${v.id}/oil" to it.ref) }
            v.tyres.forEach { add("${v.id}/tyre" to it.ref) }
            v.tpms?.let { add("${v.id}/tpms" to it.ref) }
            v.wheelTorques.forEach { add("${v.id}/wheel" to it.ref) }
            v.caliperTorque.forEach { add("${v.id}/caliper" to it.ref) }
            v.schedules.forEach { s -> add("${v.id}/${s.id}" to s.ref); s.items.forEach { add("${v.id}/${s.id}/${it.name}" to it.ref) } }
        }
        catalog.fuses.forEach { add("fuse/${it.vehicleId}/${it.number}" to it.ref) }
        catalog.problems.forEach { add("problem/${it.id}" to it.ref) }
        catalog.procedures.forEach { add("procedure/${it.id}" to it.ref) }
    }

    @Test
    fun everyCitationNamesAKnownSource() {
        val ids = catalog.sources.map { it.id }.toSet()
        val bad = allRefs().filter { it.second.sourceId !in ids }
        assertTrue("unknown sources: ${bad.take(5)}", bad.isEmpty())
    }

    @Test
    fun documentCitationsHaveAPageWithinTheDocument() {
        val docs = catalog.sources.filter { it.kind in setOf("owners_manual", "service_manual", "textbook") }.associateBy { it.id }
        val bad = allRefs().filter { (_, r) ->
            val doc = docs[r.sourceId] ?: return@filter false
            val page = r.page
            page == null || page < 1 || (doc.pages != null && page > doc.pages)
        }
        assertTrue("bad pages: ${bad.take(5)}", bad.isEmpty())
    }

    @Test
    fun safetyCriticalValuesAreInPlausibleRanges() {
        catalog.vehicles.forEach { v ->
            v.oil.forEach { o -> o.litres?.let { assertTrue("${v.id} oil $it", it in 1.0..20.0) } }
            v.tyres.forEach { t ->
                listOfNotNull(t.frontKpa, t.rearKpa).forEach { assertTrue("${v.id} tyre $it", it in 150..700) }
            }
            v.wheelTorques.forEach { w ->
                listOfNotNull(w.minNm, w.maxNm).forEach { assertTrue("${v.id} wheel $it", it in 40..400) }
            }
        }
    }

    @Test
    fun referencesBetweenRecordsResolve() {
        val vehicleIds = catalog.vehicles.map { it.id }.toSet()
        assertTrue(catalog.fuses.all { it.vehicleId in vehicleIds })
        assertTrue(catalog.problems.all { p -> p.vehicleIds.all { it in vehicleIds } })
        catalog.vehicles.forEach { v ->
            val engines = v.engines.map { it.id }.toSet()
            assertTrue("${v.id} oil engine", v.oil.all { it.engineId in engines })
            assertTrue("${v.id} schedule engine", v.schedules.all { s -> s.engineIds.all { it in engines } })
        }
    }

    @Test
    fun problemsAndProceduresAreComplete() {
        catalog.problems.forEach { p ->
            assertTrue("${p.id} applies", p.applies.isNotEmpty())
            assertTrue("${p.id} symptoms", p.symptoms.isNotEmpty())
            assertTrue("${p.id} diagnosis", p.diagnosis.isNotEmpty())
            assertTrue("${p.id} bulletins", p.bulletins.isNotEmpty())
        }
        catalog.procedures.forEach { assertTrue("${it.id} steps", it.steps.isNotEmpty()) }
        assertEquals(catalog.problems.size, catalog.problems.map { it.id }.toSet().size)
    }
}

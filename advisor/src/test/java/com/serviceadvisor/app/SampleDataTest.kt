package com.serviceadvisor.app

import com.serviceadvisor.app.data.DatasetParser
import com.serviceadvisor.app.data.isVerified
import com.serviceadvisor.app.data.litres
import com.serviceadvisor.app.domain.VehicleSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleDataTest {
    // Gradle runs unit tests from the module directory.
    private val sample = DatasetParser.parse(File("src/main/assets/vehicles.json").readText())

    @Test fun sampleParsesWithoutProblems() {
        assertTrue(DatasetParser.problems(sample).isEmpty())
        assertTrue(sample.vehicles.size >= 10)
    }

    @Test fun sampleIsNeverPresentedAsVerified() {
        assertFalse(sample.info.verified)
        assertTrue(sample.vehicles.none { sample.isVerified(it) })
    }

    @Test fun sampleCoversVehiclesWithAndWithoutTpms() {
        assertTrue(sample.vehicles.any { it.tpmsResetId == null })
        assertTrue(sample.vehicles.any { it.tpmsResetId != null })
    }

    @Test fun searchMatchesEveryWord() {
        val ids = VehicleSearch.search(sample.vehicles, "hilux 2.8").map { it.id }
        assertEquals(listOf("toyota-hilux-an120-1gd"), ids)
    }

    @Test fun searchByYearUsesTheProductionRange() {
        val corollas = VehicleSearch.search(sample.vehicles, "corolla 2016").map { it.id }
        assertEquals(listOf("toyota-corolla-e170-2zr"), corollas)
    }

    @Test fun searchByRycoNumberIsAReverseLookup() {
        val hits = VehicleSearch.search(sample.vehicles, "R2735P")
        assertTrue(hits.size >= 2)
        assertTrue(hits.all { it.oilFilter.ryco == "R2735P" })
    }

    @Test fun blankSearchReturnsNothing() {
        assertTrue(VehicleSearch.search(sample.vehicles, "  ").isEmpty())
    }

    @Test fun recordIsVerifiedOnlyWhenFileAndRecordSaySo() {
        val v = sample.vehicles.first()
        val checkedFile = sample.copy(info = sample.info.copy(verified = true))
        assertFalse(checkedFile.isVerified(v))
        assertTrue(checkedFile.isVerified(v.copy(verified = true)))
        assertFalse(sample.isVerified(v.copy(verified = true)))
    }

    @Test fun brokenReferencesAreRejected() {
        val broken = sample.copy(vehicles = listOf(sample.vehicles.first().copy(serviceResetId = "missing")))
        assertTrue(DatasetParser.problems(broken).any { "missing" in it })
        val wrongKind = sample.copy(vehicles = listOf(sample.vehicles.first().copy(tpmsResetId = "toyota-mid-service")))
        assertTrue(DatasetParser.problems(wrongKind).any { "not a TPMS procedure" in it })
    }

    @Test fun notJsonGivesAReadableError() {
        val error = runCatching { DatasetParser.parse("hello") }.exceptionOrNull()
        assertTrue(error is DatasetParser.InvalidDataset)
    }

    @Test fun litresAreShownAsWritten() {
        assertEquals("4.4 L", 4.4.litres())
        assertEquals("7 L", 7.0.litres())
        assertEquals("4.35 L", 4.35.litres())
    }
}

package com.serviceadvisor.app.domain

import com.serviceadvisor.app.data.Vehicle
import com.serviceadvisor.app.data.coversYear

/**
 * Free-text vehicle search, as typed at the counter: "hilux 2.8", "corolla 2019", "ranger px",
 * "1gd" or a Ryco number such as "z386" (reverse lookup).
 *
 * Every word must match. A four-digit word matches any vehicle built in that year; other words
 * match the start of a word in the make, model, series, engine, engine code or filter number.
 */
object VehicleSearch {
    fun search(vehicles: List<Vehicle>, query: String): List<Vehicle> {
        val terms = query.lowercase().split(Regex("[\\s,]+")).filter(String::isNotEmpty)
        if (terms.isEmpty()) return emptyList()
        return vehicles
            .filter { v -> terms.all { term -> v.matches(term) } }
            .sortedWith(compareBy({ it.make }, { it.model }, { -it.yearFrom }, { it.engine }))
    }

    private fun Vehicle.matches(term: String): Boolean {
        term.toIntOrNull()?.let { if (term.length == 4) return coversYear(it) }
        return words().any { it.startsWith(term) } || oilFilter.ryco.lowercase() == term
    }

    private fun Vehicle.words(): List<String> =
        listOfNotNull(make, model, series, engine, engineCode, oilFilter.ryco, fuel.name)
            .joinToString(" ")
            .lowercase()
            .split(Regex("[\\s/()·,]+"))
            .filter(String::isNotEmpty)
}

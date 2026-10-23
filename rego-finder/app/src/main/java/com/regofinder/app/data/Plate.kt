package com.regofinder.app.data

/** The states and territories PlateAPI accepts. */
enum class AuState { NSW, VIC, QLD, WA, SA, TAS, ACT, NT }

object Plate {
    const val MAX_LENGTH = 10

    /** Upper-cases and drops the spaces and dashes PlateAPI strips anyway, so "abc-123" and "ABC 123" share a cache entry. */
    fun normalize(input: String): String = input.uppercase().filter { it != ' ' && it != '-' }

    fun isValid(normalized: String): Boolean =
        normalized.length in 1..MAX_LENGTH && normalized.all { it in 'A'..'Z' || it in '0'..'9' }
}

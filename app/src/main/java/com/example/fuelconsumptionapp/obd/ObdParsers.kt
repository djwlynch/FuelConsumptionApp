package com.example.fuelconsumptionapp.obd

import kotlin.math.max

object ObdParsers {

    /**
     * Extract the first data payload bytes for a 01xx response.
     * Accepts typical ELM output lines like:
     * - "41 10 0F A0"
     * - "41100FA0"
     * - multi-line; we use the first line that matches "41<modePid...>"
     */
    fun extractDataBytes(expectedPidHex: String, raw: String): List<Int>? {
        val pid = expectedPidHex.uppercase()
        val mode = pid.take(2) // "01"
        val pidByte = pid.drop(2) // "0D", "10", ...
        val expectedPrefixNoSpaces = ("4" + mode.drop(1) + pidByte).uppercase() // 41 + PID

        val lines = raw
            .replace(" ", "")
            .replace("\t", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hit = lines.firstOrNull { it.uppercase().startsWith(expectedPrefixNoSpaces) } ?: return null
        val payload = hit.substring(expectedPrefixNoSpaces.length)
        if (payload.length < 2) return emptyList()

        val bytes = payload
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }

        return bytes
    }

    fun parseSpeedKmh(raw: String): Double? {
        val b = extractDataBytes(ObdPids.SPEED, raw) ?: return null
        if (b.isEmpty()) return null
        return b[0].toDouble()
    }

    fun parseRpm(raw: String): Double? {
        val b = extractDataBytes(ObdPids.RPM, raw) ?: return null
        if (b.size < 2) return null
        return (b[0] * 256 + b[1]).toDouble() / 4.0
    }

    fun parseMapKpa(raw: String): Double? {
        val b = extractDataBytes(ObdPids.MAP, raw) ?: return null
        if (b.isEmpty()) return null
        return b[0].toDouble()
    }

    /** Intake air temperature in °C (PID 0x0F). */
    fun parseIntakeTempCelsius(raw: String): Double? {
        val b = extractDataBytes(ObdPids.IAT, raw) ?: return null
        if (b.isEmpty()) return null
        return b[0].toDouble() - 40.0
    }

    fun parseMafGramsPerSecond(raw: String): Double? {
        val b = extractDataBytes(ObdPids.MAF, raw) ?: return null
        if (b.size < 2) return null
        val value = (b[0] * 256 + b[1]).toDouble() / 100.0
        return value
    }

    fun parseFuelTrimPercent(raw: String, pid: String): Double? {
        val b = extractDataBytes(pid, raw) ?: return null
        if (b.isEmpty()) return null
        return (b[0] - 128.0) * 100.0 / 128.0
    }

    /**
     * Parses commanded equivalence ratio (phi) (PID 44) or O2S1 phi (PID 34).
     * Returns lambda (λ) = 1 / phi.
     */
    fun parseLambdaFromEquivalenceRatio(raw: String, pid: String): Double? {
        val b = extractDataBytes(pid, raw) ?: return null
        if (b.size < 2) return null
        val phi = (b[0] * 256 + b[1]).toDouble() / 32768.0
        if (!phi.isFinite() || phi <= 0.0) return null
        return 1.0 / max(phi, 1e-6)
    }
}


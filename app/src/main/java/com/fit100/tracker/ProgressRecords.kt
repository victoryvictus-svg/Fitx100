package com.fit100.tracker

import java.time.LocalDate

data class ProgressPoint(val date: LocalDate, val value: Double)

object ProgressRecords {
    fun series(values: Map<String, *>, suffix: String, today: LocalDate = LocalDate.now(), allowZero: Boolean = false, maximum: Double = Double.MAX_VALUE): List<ProgressPoint> = values.mapNotNull { (key, raw) ->
        if (key.length != 10 + suffix.length || !key.endsWith(suffix)) return@mapNotNull null
        val date = runCatching { LocalDate.parse(key.take(10)) }.getOrNull() ?: return@mapNotNull null
        val number = raw?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
        if (date > today || !number.isFinite() || number > maximum || number < 0 || (!allowZero && number == 0.0)) null else ProgressPoint(date, number)
    }.sortedBy { it.date }

    fun range(points: List<ProgressPoint>, range: String, today: LocalDate = LocalDate.now()): List<ProgressPoint> {
        val start = when (range) { "Week" -> today.minusDays(6); "Month" -> today.minusDays(29); else -> LocalDate.MIN }
        return points.filter { it.date >= start && it.date <= today }
    }
    fun bmi(weight: Double?, heightCm: Double?): Double? {
        if (weight == null || heightCm == null || !weight.isFinite() || !heightCm.isFinite() || weight <= 0 || heightCm <= 0) return null
        return (weight / (heightCm / 100) / (heightCm / 100)).takeIf { it.isFinite() }
    }
}

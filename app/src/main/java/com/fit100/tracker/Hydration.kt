package com.fit100.tracker

import kotlin.math.roundToInt

internal object Hydration {
    fun millilitres(litres: String?): Int {
        val value = litres?.toDoubleOrNull() ?: return 0
        if (!value.isFinite() || value < 0) return 0
        return (value * 1000).coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()
    }
    fun add(current: Int, amount: Int): Int {
        require(current >= 0 && amount in 1..5000)
        return (current.toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun litres(millilitres: Int): String { require(millilitres >= 0); return (millilitres / 1000.0).toString() }
}

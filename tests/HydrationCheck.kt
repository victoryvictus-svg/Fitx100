package com.fit100.tracker

fun main() {
    check(Hydration.millilitres("3") == 3000)
    check(Hydration.millilitres("1.25") == 1250)
    check(Hydration.millilitres("0.001") == 1)
    listOf(null, "", "bad", "NaN", "Infinity", "-1").forEach { check(Hydration.millilitres(it) == 0) }
    var amount = 0
    repeat(16) { amount = Hydration.add(amount, 250) }
    check(amount == 4000)
    check(Hydration.millilitres(Hydration.litres(amount)) == 4000)
    check(Hydration.add(amount, 500) == 4500)
    check(Hydration.add(1250, 375) == 1625)
    listOf(0, -1, 5001).forEach { check(runCatching { Hydration.add(0, it) }.isFailure) }
    check(Hydration.add(Int.MAX_VALUE - 1, 500) == Int.MAX_VALUE)
    println("Hydration conversion and logging checks passed")
}

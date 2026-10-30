package com.fit100.tracker
import java.time.LocalDate
import kotlin.math.abs
fun main() {
    val today=LocalDate.of(2026,10,23)
    val input=mapOf<String,Any>("2026-10-23-weight" to "105.2","2026-10-20-weight" to "106", "2026-10-01-weight" to "107.5", "2026-10-24-weight" to "104", "bad-weight" to "99", "2026-02-30-weight" to "99", "2026-10-21-weight" to "NaN", "2026-10-22-weight" to "0", "2026-10-19-weight" to "-1", "profile-currentweight" to "500", "2026-10-23-weight-extra" to "12")
    val weights=ProgressRecords.series(input,"-weight",today)
    check(weights.map { it.value }==listOf(107.5,106.0,105.2))
    check(ProgressRecords.range(weights,"Week",today).size==2)
    check(ProgressRecords.range(weights,"Month",today).size==3)
    check(ProgressRecords.range(weights,"All Time",today).size==3)
    val edge=listOf(ProgressPoint(today.minusDays(7),1.0),ProgressPoint(today.minusDays(6),2.0),ProgressPoint(today,3.0))
    check(ProgressRecords.range(edge,"Week",today).map {it.value}==listOf(2.0,3.0))
    val scores=ProgressRecords.series(mapOf("2026-10-20-composition-score" to "0", "2026-10-21-composition-score" to "101", "2026-10-22-composition-score" to "Infinity", "2026-10-23-composition-score" to "52"),"-composition-score",today,true,100.0)
    check(scores.map {it.value}==listOf(0.0,52.0))
    check(ProgressRecords.series(emptyMap<String,String>(),"-weight",today).isEmpty())
    check(ProgressRecords.bmi(100.0,200.0)==25.0)
    check(abs(ProgressRecords.bmi(106.0,176.0)!!-34.2200413)<.0001)
    check(ProgressRecords.bmi(null,176.0)==null)
    check(ProgressRecords.bmi(100.0,0.0)==null)
    check(ProgressRecords.bmi(Double.NaN,176.0)==null)
    println("Progress record parsing, date ranges, score validation and BMI checks passed")
}

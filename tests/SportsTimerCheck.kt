package com.fit100.tracker
fun main() {
 val start=1_000_000L
 check(SportsRecords.timerMillis(start,start)==0L)
 check(SportsRecords.timerMillis(start,start+90_000)==90_000L)
 check(SportsRecords.timerMillis(start,start-500)==0L)
 check(SportsRecords.timerMillis(start,start+90_000_000)==86_400_000L)
 check(SportsRecords.sports.distinct().size==SportsRecords.sports.size)
 check(SportsRecords.sports.last()=="Other / Custom")
 println("Sports timer restart arithmetic, rollback and 24-hour cap checks passed")
}

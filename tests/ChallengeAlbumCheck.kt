package com.fit100.tracker
import java.time.*
fun main() {
 val start=Instant.parse("2026-10-23T15:45:00Z").toEpochMilli();val day=Duration.ofDays(1).toMillis()
 check(ChallengeClock.day(null,start)==0);check(ChallengeClock.percent(null,start)==0)
 check(ChallengeClock.day(start,start)==1);check(ChallengeClock.percent(start,start)==0)
 check(ChallengeClock.day(start,start+day-1)==1);check(ChallengeClock.day(start,start+day)==2)
 check(ChallengeClock.percent(start,start+day)==1)
 check(ChallengeClock.day(start,start+99*day)==100);check(ChallengeClock.percent(start,start+99*day)==99)
 check(ChallengeClock.percent(start,start+100*day)==100);check(ChallengeClock.day(start,start+101*day)==100)
 check(ChallengeClock.day(start,start-1)==0);check(ChallengeClock.elapsed(start,start-1)==0L)
 check(ChallengeClock.end(start).toEpochMilli()==start+100*day)
 val dst=ZonedDateTime.of(2026,3,7,20,0,0,0,ZoneId.of("America/New_York")).toInstant().toEpochMilli()
 check(ChallengeClock.end(dst).toEpochMilli()-dst==100*day)
 val photos=AlbumRecords.photos(mapOf("workout-photo-2026-10-23-before" to "content://a", "body-photo-2026-10-22-front" to "content://b", "photo-1" to "content://c", "profile-photo" to "content://d", "body-photo-2026-02-30-front" to "x", "photo-101" to "x", "workout-photo-2026-10-23-bad" to "x"))
 check(photos.size==3);check(photos.first().date==LocalDate.of(2026,10,23));check(photos.last().label=="Earlier Day 1")
 println("Challenge elapsed-time, exact boundaries, DST and album indexing checks passed")
}

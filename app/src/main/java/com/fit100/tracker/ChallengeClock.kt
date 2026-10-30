package com.fit100.tracker
import java.time.*
object ChallengeClock {
 const val DAYS=100L
 fun elapsed(start:Long?,now:Long=System.currentTimeMillis()):Long = if(start==null) 0 else (now-start).coerceIn(0,Duration.ofDays(DAYS).toMillis())
 fun day(start:Long?,now:Long=System.currentTimeMillis()):Int = if(start==null || now<start) 0 else (elapsed(start,now)/Duration.ofDays(1).toMillis()+1).toInt().coerceAtMost(100)
 fun percent(start:Long?,now:Long=System.currentTimeMillis()):Int = (elapsed(start,now)*100/Duration.ofDays(DAYS).toMillis()).toInt()
 fun end(start:Long)=Instant.ofEpochMilli(start).plus(Duration.ofDays(DAYS))
 fun date(start:Long?,zone:ZoneId=ZoneId.systemDefault()):LocalDate = start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?:LocalDate.now(zone)
}

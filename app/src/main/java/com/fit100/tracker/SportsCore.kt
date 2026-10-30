package com.fit100.tracker

import java.io.*
import java.time.*
import java.util.UUID
import kotlin.math.*

// No Android dependencies: domain transitions run identically in JVM tests and on device.
data class SportsNow(val wall:Long,val mono:Long,val boot:Int)
data class ActiveTimer(var accumulated:Long=0,var anchor:Long=0,var boot:Int=-1,var running:Boolean=false):Serializable {
 fun elapsed(n:SportsNow):Long=accumulated+if(running && boot==n.boot)(n.mono-anchor).coerceAtLeast(0) else 0
 fun start(n:SportsNow){if(!running){anchor=n.mono;boot=n.boot;running=true}}
 fun pause(n:SportsNow){accumulated=elapsed(n);running=false;anchor=n.mono;boot=n.boot}
 fun checkpoint(n:SportsNow){val wasRunning=running && boot==n.boot;accumulated=elapsed(n);anchor=n.mono;boot=n.boot;running=wasRunning}
 fun reset(){accumulated=0;running=false}
 companion object { private const val serialVersionUID=1L }
}
data class ExerciseCountdown(var duration:Long=60000,var timer:ActiveTimer=ActiveTimer(),var notified:Boolean=false,var started:Boolean=false):Serializable {
 fun remaining(n:SportsNow)=(duration-timer.elapsed(n)).coerceAtLeast(0)
 fun begin(seconds:Int,n:SportsNow){duration=seconds.coerceIn(1,7200)*1000L;timer=ActiveTimer();timer.start(n);notified=false;started=true}
 fun adjust(seconds:Int,n:SportsNow){val elapsed=timer.elapsed(n).coerceAtMost(duration);timer.accumulated=elapsed;timer.anchor=n.mono;timer.boot=n.boot;if(elapsed>=duration)timer.running=false;duration=(duration+seconds*1000L).coerceIn(elapsed,7_200_000);notified=false}
 companion object { private const val serialVersionUID=1L }
}
data class ExerciseDefinition(val id:String,val name:String,val sets:Int,val reps:Int,val rest:Int,val met:Double,val instructions:String):Serializable { companion object { private const val serialVersionUID=1L } }
object SportsExercises {
 val all=listOf(
  ExerciseDefinition("walking","Walking",1,0,60,3.5,"Stand tall, relax your shoulders and look ahead. Walk with a natural heel-to-toe stride. Choose a safe route and a comfortable pace."),
  ExerciseDefinition("jogging","Jogging",1,0,60,7.0,"Warm up with an easy walk. Keep your posture tall, arms relaxed and steps short. Increase speed gradually and cool down afterward."),
  ExerciseDefinition("jacks","Jumping Jacks",3,20,30,7.5,"Start with feet together and arms by your sides. Jump feet apart while raising arms overhead. Return with a soft landing. Count one full out-and-back cycle as one repetition."),
  ExerciseDefinition("climbers","Mountain Climbers",3,20,30,8.0,"Begin in a high plank with hands under shoulders. Brace your core and keep hips level. Bring knees toward your chest alternately. Count each knee drive as one repetition."),
  ExerciseDefinition("knee","Knee Push-ups",3,10,60,3.8,"Place hands slightly wider than shoulders and rest knees on a padded surface. Keep a straight line from knees to shoulders. Lower your chest with elbows angled back, then press up."),
  ExerciseDefinition("pushups","Push-ups",3,8,60,3.8,"Place hands just wider than shoulders. Brace your core and keep head, hips and heels aligned. Lower your chest with elbows angled back, then press up. Use knee push-ups if you cannot maintain form."))
 fun get(id:String)=all.first { it.id==id }
}
data class SetRecord(val id:String=UUID.randomUUID().toString(),val reps:Int,val seconds:Int=0,val skipped:Boolean=false,val at:Long):Serializable { companion object { private const val serialVersionUID=1L } }
 data class PlannedExercise(val id:String,val sets:Int=SportsExercises.get(id).sets,val reps:Int=SportsExercises.get(id).reps,val rest:Int=SportsExercises.get(id).rest,val seconds:Int=30,val mode:String="Reps"):Serializable { companion object { private const val serialVersionUID=1L } }
 data class SportsPlan(val day:Int,var name:String,var recovery:Boolean=false,val exercises:MutableList<PlannedExercise> = mutableListOf()):Serializable { companion object { private const val serialVersionUID=1L } }
 data class SessionExercise(val target:PlannedExercise,val sets:MutableList<SetRecord> = mutableListOf(),var currentReps:Int=0,var done:Boolean=false,val timer:ActiveTimer=ActiveTimer(),val round:ExerciseCountdown=ExerciseCountdown(target.seconds*1000L)):Serializable {
 companion object { private const val serialVersionUID=1L }
 val repetitions get()=sets.sumOf { it.reps }+currentReps
 val finishedSets get()=sets.count { !it.skipped }
 val progress:Double get()=if(target.id in listOf("walking","jogging")) { if(done)1.0 else 0.0 } else (sets.sumOf { if(it.skipped)0.0 else (if(target.mode=="Timer")it.seconds.toDouble()/target.seconds else it.reps.toDouble()/target.reps.coerceAtLeast(1)).coerceAtMost(1.0) }/target.sets).coerceIn(0.0,1.0)
 }
 data class RoutePoint(val lat:Double,val lon:Double,val accuracy:Float,val mono:Long,val wall:Long):Serializable { companion object { private const val serialVersionUID=1L } }
 data class SportsSession(val id:String=UUID.randomUUID().toString(),var name:String,val started:Long,var ended:Long?=null,var status:String="ACTIVE",val timer:ActiveTimer=ActiveTimer(),val items:MutableList<SessionExercise> = mutableListOf(),var itemIndex:Int=0,var mode:String="Indoor / Manual",var distance:Double?=null,var steps:Int?=null,var calories:Double?=null,var weight:Double?=null,var notes:String="",val route:MutableList<RoutePoint> = mutableListOf(),var rest:ExerciseCountdown=ExerciseCountdown(),var gpsStatus:String="Not requested",var speed:Double?=null,var lastSensor:Float?=null,var sensorBoot:Int=-1,var manualSteps:Boolean=false,var distances:MutableMap<Int,Double>?=null):Serializable {
 val date:LocalDate get()=Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault()).toLocalDate()
 val repetitions get()=items.sumOf { it.repetitions }
 val totalSets get()=items.sumOf { it.finishedSets }
 val completedExercises get()=items.count { it.done && it.progress>=0.99999 }
 val completion get()=if(items.isEmpty())100 else (items.sumOf { it.progress }*100/items.size).toInt()
 companion object { private const val serialVersionUID=1L }
 }
object SportsEngine {
 fun pause(s:SportsSession,n:SportsNow){s.timer.pause(n);s.items.forEach { it.timer.pause(n);it.round.timer.pause(n) };s.rest.timer.pause(n);s.status="PAUSED";s.speed=null;s.lastSensor=null}
 fun resume(s:SportsSession,n:SportsNow){require(s.status=="PAUSED" || s.status=="ACTIVE");s.status="ACTIVE";s.timer.start(n);s.items.getOrNull(s.itemIndex)?.let { if(!it.done)it.timer.start(n) };s.rest.let { if(it.started&&!it.notified&&it.remaining(n)>0)it.timer.start(n) };s.items.getOrNull(s.itemIndex)?.round?.let { if(it.started&&!it.notified&&it.remaining(n)>0)it.timer.start(n) };s.lastSensor=null}
 fun rep(e:SessionExercise,delta:Int){check(!e.done);e.currentReps=(e.currentReps+delta).coerceIn(0,10000)}
 fun completeSet(s:SportsSession,skip:Boolean,n:SportsNow){check(s.status=="ACTIVE");val e=s.items[s.itemIndex];check(!e.done);val seconds=if(e.target.mode=="Timer") (e.round.timer.elapsed(n)/1000).coerceAtMost(e.target.seconds.toLong()).toInt() else 0
  require(skip || e.currentReps>0 || seconds>0);e.sets.add(SetRecord(reps=if(skip)0 else e.currentReps,seconds=if(skip)0 else seconds,skipped=skip,at=n.wall));e.currentReps=0;e.round.timer.pause(n);e.round.started=false;e.round.notified=true
  if(e.sets.size>=e.target.sets){e.done=true;e.timer.pause(n)}
  s.rest.begin(e.target.rest,n)
 }
 fun finish(s:SportsSession,n:SportsNow){if(s.status=="SAVED" || s.status=="REVIEW")return;pause(s,n);s.status="REVIEW";s.ended=n.wall;s.calories=estimate(s,n)}
 fun estimate(s:SportsSession,n:SportsNow):Double? {
  val weight=s.weight?:return null
  val observed=s.items.mapNotNull { e->val met=SportsExercises.all.find { it.id==e.target.id }?.met?:return@mapNotNull null;val time=e.timer.elapsed(n);if(time>0)met to time else null }
  val observedTime=observed.sumOf { it.second }
  val met=if(observedTime>0)observed.sumOf { it.first*it.second }/observedTime else if(s.items.size==1)SportsExercises.all.find { it.id==s.items[0].target.id }?.met else null
  return met?.let { it*3.5*weight/200*(s.timer.elapsed(n)/60000.0) }
 }
 fun acceptDistance(previous:RoutePoint,next:RoutePoint,exercise:String):Double? {
  if(!next.lat.isFinite()||!next.lon.isFinite()||!previous.lat.isFinite()||!previous.lon.isFinite()||next.lat !in -90.0..90.0||next.lon !in -180.0..180.0)return null
  if(next.accuracy<=0 || next.accuracy>30 || previous.accuracy>30)return null
  val seconds=(next.mono-previous.mono)/1000.0;if(seconds<=0 || seconds>30)return null
  val rad=Math.PI/180;val dl=(next.lat-previous.lat)*rad;val dn=(next.lon-previous.lon)*rad;val h=sin(dl/2).pow(2)+cos(previous.lat*rad)*cos(next.lat*rad)*sin(dn/2).pow(2);val metres=6371000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
  if(metres<max(3.0,(previous.accuracy+next.accuracy)*0.35) || metres/seconds>if(exercise=="walking")3.5 else 9.0)return null
  return metres
 }
 fun defaults(day:Int):SportsPlan { val recovery=day==3||day==7;val ids=when(day){1,4->listOf("knee","pushups");2,5->listOf("jacks","climbers");6->listOf("walking","jacks","climbers","knee","pushups");else->emptyList()};return SportsPlan(day,when(day){1,4->"Upper Body";2,5->"Lower Body";6->"Full Body";else->"Recovery / Rest"},recovery,ids.map { PlannedExercise(it) }.toMutableList()) }
}
object SportsCodec {
 fun encode(value:Serializable):ByteArray=ByteArrayOutputStream().use { b->ObjectOutputStream(b).use { it.writeObject(value) };b.toByteArray() }
 @Suppress("UNCHECKED_CAST") fun <T> decode(bytes:ByteArray):T=ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
}
interface SportsStorage { fun read(key:String):ByteArray?;fun write(key:String,data:ByteArray);fun remove(key:String);fun entries(prefix:String):List<Pair<String,ByteArray>> }
class SportsRepository(private val storage:SportsStorage) {
 fun session(id:String):SportsSession?=storage.read("session:$id")?.let { SportsCodec.decode<SportsSession>(it) }
 fun sessions()=storage.entries("session:").map { SportsCodec.decode<SportsSession>(it.second) }.sortedByDescending { it.started }
 fun save(s:SportsSession)=storage.write("session:${s.id}",SportsCodec.encode(s))
 fun complete(s:SportsSession){check(s.status=="REVIEW"||s.status=="SAVED");s.status="SAVED";save(s)}
 fun delete(id:String)=storage.remove("session:$id")
 fun active()=sessions().firstOrNull { it.status=="ACTIVE"||it.status=="PAUSED"||it.status=="REVIEW" }
 fun history()=sessions().filter { it.status=="SAVED" }
 fun plan(day:Int)=storage.read("plan:$day")?.let { SportsCodec.decode<SportsPlan>(it) }?:SportsEngine.defaults(day)
 fun plan(p:SportsPlan)=storage.write("plan:${p.day}",SportsCodec.encode(p))
 fun countdown()=storage.read("countdown")?.let { SportsCodec.decode<ExerciseCountdown>(it) }?:ExerciseCountdown()
 fun countdown(c:ExerciseCountdown)=storage.write("countdown",SportsCodec.encode(c))
}

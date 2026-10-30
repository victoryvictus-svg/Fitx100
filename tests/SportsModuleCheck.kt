package com.fit100.tracker
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

private class FileSportsStorage(private val folder:File):SportsStorage {
 init{folder.mkdirs()}
 private fun path(key:String)=File(folder,Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray()))
 override fun read(key:String)=path(key).takeIf { it.exists() }?.readBytes()
 override fun write(key:String,data:ByteArray){val target=path(key);val tmp=File(folder,"tmp");tmp.writeBytes(data);Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)}
 override fun remove(key:String){path(key).delete()}
 override fun entries(prefix:String)=folder.listFiles()!!.filter { it.name!="tmp" }.map { String(Base64.getUrlDecoder().decode(it.name)) to it.readBytes() }.filter { it.first.startsWith(prefix) }
}
fun main(args:Array<String>){var assertions=0;fun verify(value:Boolean){check(value);assertions++}
 val n=SportsNow(100000,1000,1);val t=ActiveTimer();t.start(n);verify(t.elapsed(n.copy(mono=6000))==5000L);t.pause(n.copy(mono=6000));verify(t.elapsed(n.copy(mono=99000))==5000L);t.start(n.copy(mono=100000));verify(t.elapsed(n.copy(mono=102000))==7000L);verify(t.elapsed(n.copy(wall=99999999,mono=102000))==7000L);verify(t.elapsed(n.copy(boot=2,mono=2000))==5000L)
 val count=ExerciseCountdown();count.begin(30,n);verify(count.remaining(n.copy(mono=11000))==20000L);count.timer.pause(n.copy(mono=11000));verify(count.remaining(n.copy(mono=99000))==20000L);count.timer.start(n.copy(mono=100000));verify(count.remaining(n.copy(mono=130000))==0L);count.adjust(10,n.copy(mono=110000));verify(count.remaining(n.copy(mono=110000))==20000L);count.timer.reset();verify(!count.timer.running)
 val s=SportsSession(name="Jumping Jacks",started=n.wall,items=mutableListOf(SessionExercise(PlannedExercise("jacks"))),weight=80.0);SportsEngine.resume(s,n);val e=s.items[0];SportsEngine.rep(e,-1);verify(e.currentReps==0);repeat(21){SportsEngine.rep(e,1)};SportsEngine.rep(e,-1);verify(e.currentReps==20);SportsEngine.completeSet(s,false,n.copy(mono=6000));verify(e.sets.single().reps==20);verify(e.currentReps==0);verify(s.totalSets==1);verify(s.repetitions==20);verify(s.completion==33);verify(s.rest.remaining(n.copy(mono=6000))==30000L)
 SportsEngine.pause(s,n.copy(mono=16000));verify(s.status=="PAUSED");val paused=s.timer.elapsed(n.copy(mono=90000));SportsEngine.resume(s,n.copy(mono=100000));verify(s.timer.elapsed(n.copy(mono=105000))==paused+5000);verify(s.rest.timer.running)
 repeat(20){SportsEngine.rep(e,1)};SportsEngine.completeSet(s,false,n.copy(mono=106000));repeat(20){SportsEngine.rep(e,1)};SportsEngine.completeSet(s,false,n.copy(mono=107000));verify(e.done);verify(s.completedExercises==1);verify(s.completion==100);verify(s.repetitions==60)
 SportsEngine.finish(s,n.copy(mono=110000));verify(s.status=="REVIEW");verify(!s.timer.running);verify(s.calories!=null&&s.calories!!>0);val ended=s.ended;SportsEngine.finish(s,n.copy(mono=120000));verify(s.ended==ended)
 val root=File(args[0]);root.mkdirs();val store=FileSportsStorage(root);val r=SportsRepository(store);r.save(s);val r2=SportsRepository(FileSportsStorage(root));verify(r2.session(s.id)!!.repetitions==60);r2.complete(r2.session(s.id)!!);r2.complete(r2.session(s.id)!!);verify(r2.history().count { it.id==s.id }==1);verify(r2.active()==null)
 val plan=r2.plan(1);verify(plan.name=="Upper Body");plan.name="Custom upper body";plan.exercises.add(PlannedExercise("jacks",2,15,45));java.util.Collections.swap(plan.exercises,0,2);r2.plan(plan);val r3=SportsRepository(FileSportsStorage(root));verify(r3.plan(1).name=="Custom upper body");verify(r3.plan(1).exercises.first().id=="jacks");verify(r3.plan(3).recovery);verify(r3.plan(7).recovery);verify(!r3.plan(6).recovery)
 r3.countdown(count);verify(r3.countdown().duration==count.duration);r3.delete(s.id);verify(r3.session(s.id)==null)
 val skip=SportsSession(name="Skip",started=n.wall,items=mutableListOf(SessionExercise(PlannedExercise("pushups",1,8))));SportsEngine.resume(skip,n);SportsEngine.completeSet(skip,true,n);verify(skip.completion==0);verify(skip.completedExercises==0);verify(skip.repetitions==0)
 val timed=SportsSession(name="Timed climbers",started=n.wall,items=mutableListOf(SessionExercise(PlannedExercise("climbers",mode="Timer"))));SportsEngine.resume(timed,n);timed.items[0].round.begin(30,n);SportsEngine.completeSet(timed,false,n.copy(mono=31000));verify(timed.items[0].sets[0].seconds==30);verify(!timed.items[0].round.started)
 val point=RoutePoint(12.0,77.0,5f,1000,100000);val next=point.copy(lat=12.0001,mono=11000);verify(SportsEngine.acceptDistance(point,next,"walking")!! in 10.0..12.0);verify(SportsEngine.acceptDistance(point,next.copy(accuracy=80f),"walking")==null);verify(SportsEngine.acceptDistance(point,next.copy(lat=13.0),"jogging")==null);verify(SportsEngine.acceptDistance(point,next.copy(mono=1000),"walking")==null);verify(SportsEngine.acceptDistance(point,next.copy(lat=12.000001),"walking")==null);verify(SportsEngine.acceptDistance(point,next.copy(mono=40000),"walking")==null)
 val partial=SessionExercise(PlannedExercise("pushups",3,8));partial.sets.add(SetRecord(reps=24,at=n.wall));verify(partial.progress in 0.33..0.34)
 val expired=ExerciseCountdown();expired.begin(30,n);expired.adjust(10,n.copy(mono=100000000));verify(expired.remaining(n.copy(mono=100000000))==10000L)
 val weighted=SportsSession(name="Mixed",started=n.wall,weight=80.0,timer=ActiveTimer(accumulated=60000),items=mutableListOf(SessionExercise(PlannedExercise("jacks"),timer=ActiveTimer(accumulated=60000)),SessionExercise(PlannedExercise("pushups"))));verify(kotlin.math.abs(SportsEngine.estimate(weighted,n)!!-10.5)<0.0001)
 verify(SportsEngine.estimate(skip,n)==null);verify(SportsExercises.all.size==6);println("PASS: $assertions assertions: monotonic clocks, pause/resume, wall-clock changes, reboot, countdowns, reps/undo, sets, completion, skipped sets, timed rounds, idempotent saves, disk repository reopen, plans, recovery and GPS filters")
}

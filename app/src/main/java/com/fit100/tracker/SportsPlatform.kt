package com.fit100.tracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.*
import android.content.ContentValues
import android.hardware.*
import android.location.*
import android.os.*
import android.provider.Settings
import java.time.*
import java.util.concurrent.CopyOnWriteArraySet

class SportsSqlStorage(context:Context,databaseName:String="sports.db"):SQLiteOpenHelper(context,databaseName,null,1),SportsStorage {
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE sports_records (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL)")}
 // Future schema upgrades must be additive. No destructive migrations or existing preference changes.
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){require(old==new){"Unsupported Sports database version"}}
 override fun read(key:String):ByteArray?=readableDatabase.query("sports_records",arrayOf("payload"),"id=?",arrayOf(key),null,null,null).use { if(it.moveToFirst())it.getBlob(0) else null }
 override fun write(key:String,data:ByteArray){writableDatabase.beginTransaction();try { writableDatabase.insertWithOnConflict("sports_records",null,ContentValues().apply { put("id",key);put("payload",data) },SQLiteDatabase.CONFLICT_REPLACE);writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() };SportsHub.changed()}
 override fun remove(key:String){writableDatabase.delete("sports_records","id=?",arrayOf(key));SportsHub.changed()}
 override fun entries(prefix:String):List<Pair<String,ByteArray>> = readableDatabase.query("sports_records",arrayOf("id","payload"),"id LIKE ?",arrayOf("$prefix%"),null,null,"id").use { c->buildList { while(c.moveToNext())add(c.getString(0) to c.getBlob(1)) } }
}
object SportsHub {
 private var repository:SportsRepository?=null
 val listeners=CopyOnWriteArraySet<()->Unit>()
 fun changed(){listeners.forEach { it() }}
 @Synchronized fun repo(c:Context):SportsRepository { if(repository==null){val store=SportsSqlStorage(c.applicationContext);repository=SportsRepository(store);if(store.read("countdown")==null){val seconds=c.getSharedPreferences("fit100",0).getString("profile-timer","60")?.toIntOrNull()?.coerceIn(10,600)?:60;repository!!.countdown(ExerciseCountdown(seconds*1000L))};migrateLegacy(c,repository!!)};return repository!! }
 fun now(c:Context)=SportsNow(System.currentTimeMillis(),SystemClock.elapsedRealtime(),Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,-1))
 internal fun migrateLegacy(c:Context,r:SportsRepository){val p=c.getSharedPreferences("fit100",0)
  if(!p.getBoolean("sports-db-migrated",false)){p.all.filterKeys { it.startsWith("sport-session-") }.forEach { (k,v)->val old=(v as? String)?.let { SportsRecords.parse(k,it) }?:return@forEach
   if(r.session(k)==null){val start=old.date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();r.save(SportsSession(id=k,name=old.sport,started=start,ended=start+(old.minutes*60000).toLong(),status="SAVED",timer=ActiveTimer(accumulated=(old.minutes*60000).toLong()),distance=old.km?.times(1000),notes=old.notes+"\nImported manual record; time of day was not recorded."))}
  };p.edit().putBoolean("sports-db-migrated",true).commit()}
  if(!p.getBoolean("sports-sets-migrated",false)){val sets=p.all.filterKeys { it.startsWith("workout-set-") }.mapNotNull { (key,value)->runCatching { key to org.json.JSONObject(value as String) }.getOrNull() }.groupBy { it.second.optString("date") }
   sets.forEach { (date,records)->runCatching { val day=LocalDate.parse(date);val id="legacy-sets-$date";if(r.session(id)==null){val groups=records.groupBy { it.second.optString("exercise","Exercise") };val items=groups.map { (name,entries)->val matching=SportsExercises.all.find { it.name.equals(name,true)||name.equals("Push Ups",true)&&it.id=="pushups" };val target=PlannedExercise(id=matching?.id?:"legacy:$name",sets=entries.size,reps=1,rest=60);SessionExercise(target,entries.map { (key,j)->SetRecord(id=key,reps=j.optInt("reps").coerceIn(0,10000),at=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }.toMutableList(),done=true) }.toMutableList();r.save(SportsSession(id=id,name="Imported exercise sets",started=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),status="SAVED",items=items,notes="Legacy sets; no session duration was recorded. Original preference records are preserved."))} } }
   p.edit().putBoolean("sports-sets-migrated",true).commit()
  }
 }
 fun recover(c:Context){val r=repo(c);val n=now(c);val common=r.countdown();if(common.timer.running&&common.timer.boot!=n.boot){common.timer.running=false;r.countdown(common)};val s=r.active()?:return
  val changed=s.timer.running && (s.timer.boot!=n.boot || (s.mode=="Outdoor GPS"&&!SportsTrackingService.running))
  if(changed){s.timer.running=false;s.items.forEach { it.timer.running=false;it.round.timer.running=false };s.rest.timer.running=false;s.status="PAUSED";s.gpsStatus="Tracking interrupted. Resume to reconnect GPS.";r.save(s)}
 }
 fun dailySteps(c:Context,date:LocalDate=LocalDate.now()):Int? {val p=c.getSharedPreferences("fit100",0);val measured=if(p.contains("sports-sensor-$date"))p.getInt("sports-sensor-$date",0) else null;val manual=repo(c).history().filter { it.date==date&&it.manualSteps }.sumOf { it.steps?:0 };return if(measured==null&&manual==0)null else (measured?:0)+manual}
 fun streak(c:Context):Int {val dates=repo(c).history().map { it.date }.toMutableSet();val p=c.getSharedPreferences("fit100",0);p.all.filter { (k,v)->k.startsWith("sports-recovery-")&&v==true }.keys.forEach { runCatching { dates.add(LocalDate.parse(it.removePrefix("sports-recovery-"))) } };var date=LocalDate.now();if(date !in dates)date=date.minusDays(1);var total=0;while(date in dates){total++;date=date.minusDays(1)};return total}
}
class SportsStepMonitor(private val c:Context,private val session:Boolean=false):SensorEventListener {
 private val manager=c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
 fun available()=manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null
 fun start():Boolean {if(Build.VERSION.SDK_INT>=29&&c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)return false;return runCatching { manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL) }?:false }.getOrDefault(false)}
 fun stop(){manager.unregisterListener(this)}
 override fun onAccuracyChanged(s:Sensor?,a:Int){}
 override fun onSensorChanged(e:SensorEvent){val n=SportsHub.now(c);val p=c.getSharedPreferences("fit100",0);val date=LocalDate.now().toString();val previous=p.getFloat("sports-last-sensor",-1f);val previousBoot=p.getInt("sports-sensor-boot",-2);val raw=e.values[0];val lastDay=p.getString("sports-sensor-day",null);val delta=if(previousBoot==n.boot && lastDay==date && raw>=previous)(raw-previous).toInt().coerceIn(0,10000) else 0
  p.edit().putFloat("sports-last-sensor",raw).putInt("sports-sensor-boot",n.boot).putString("sports-sensor-day",date).putInt("sports-sensor-$date",p.getInt("sports-sensor-$date",0)+delta).apply()
  if(session){val r=SportsHub.repo(c);val s=r.active()?:return;if(s.status!="ACTIVE"||s.manualSteps)return;val diff=if(s.sensorBoot==n.boot&&s.lastSensor!=null&&raw>=s.lastSensor!!)(raw-s.lastSensor!!).toInt().coerceIn(0,10000) else 0;s.steps=(s.steps?:0)+diff;s.lastSensor=raw;s.sensorBoot=n.boot;r.save(s)};SportsHub.changed()
 }
}
class SportsTrackingService:Service(),LocationListener {
 companion object { var running=false;private set
  fun start(a:Activity):Boolean=try { a.startForegroundService(Intent(a,SportsTrackingService::class.java));true }catch(e:Exception){val r=SportsHub.repo(a);r.active()?.let { SportsEngine.pause(it,SportsHub.now(a));it.gpsStatus="Cannot start tracking: ${e.javaClass.simpleName}. Use Indoor / Manual.";r.save(it) };false }
 }
 private val handler=Handler(Looper.getMainLooper());private var last:RoutePoint?=null;private lateinit var steps:SportsStepMonitor
 private val tick=object:Runnable { override fun run(){val r=SportsHub.repo(this@SportsTrackingService);val s=r.active();if(s==null||s.status!="ACTIVE"){stopSelf();return};val n=SportsHub.now(this@SportsTrackingService);s.timer.checkpoint(n);s.items.forEach { it.timer.checkpoint(n) };r.save(s);handler.postDelayed(this,5000)} }
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate();steps=SportsStepMonitor(this,true)}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
  val r=SportsHub.repo(this);val s=r.active();if(s==null||s.status!="ACTIVE"){stopSelf();return START_NOT_STICKY}
  try {val outdoor=s.mode=="Outdoor GPS";val fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;val coarse=checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
   if(outdoor && !fine && !coarse)throw SecurityException("Location permission missing")
   if(!outdoor && Build.VERSION.SDK_INT>=29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY}
   val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("sports-tracking","Active Sports tracking",NotificationManager.IMPORTANCE_LOW))
   val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("open-sports",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   val pause=PendingIntent.getBroadcast(this,5,Intent(this,SportsAlarmReceiver::class.java).setAction("sports.pause"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   val notification=Notification.Builder(this,"sports-tracking").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Fit100 · ${s.name}").setContentText(if(outdoor)"GPS tracking active · tap to open" else "Step tracking active · tap to open").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Pause",pause).build()).build()
   val type=if(outdoor)android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else if(Build.VERSION.SDK_INT>=34)android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
   if(Build.VERSION.SDK_INT>=29)startForeground(410,notification,type) else startForeground(410,notification)
   running=true;last=null;steps.stop();steps.start();if(outdoor){val lm=getSystemService(LocationManager::class.java);lm.removeUpdates(this);val provider=if(fine&&lm.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER;if(!lm.isProviderEnabled(provider)){s.gpsStatus="Location disabled. Enable device location or use Indoor / Manual.";r.save(s)} else {s.gpsStatus=if(fine)"Waiting for accurate GPS" else "Approximate access · accurate distance may be unavailable";r.save(s);lm.requestLocationUpdates(provider,2000,0f,this)} }
   handler.removeCallbacks(tick);handler.post(tick)
  }catch(e:Exception){SportsEngine.pause(s,SportsHub.now(this));s.gpsStatus="Tracking unavailable: ${e.javaClass.simpleName}. Indoor / Manual is available.";r.save(s);stopSelf()}
  return START_NOT_STICKY
 }
 override fun onLocationChanged(location:Location){val r=SportsHub.repo(this);val s=r.active()?:return;if(s.status!="ACTIVE"||s.mode!="Outdoor GPS")return
  val n=SportsHub.now(this);if(!location.hasAccuracy()||location.accuracy>30||location.accuracy<=0||(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)>15_000_000_000L){s.gpsStatus="Weak GPS · waiting for an accurate fix";s.speed=null;r.save(s);return}
  val next=RoutePoint(location.latitude,location.longitude,location.accuracy,location.elapsedRealtimeNanos/1_000_000,location.time)
  val prior=last;val distance=prior?.let { SportsEngine.acceptDistance(it,next,s.items.getOrNull(s.itemIndex)?.target?.id?:"walking") }
  if(distance!=null){s.distance=(s.distance?:0.0)+distance;val segments=s.distances?:mutableMapOf<Int,Double>().also { s.distances=it };segments[s.itemIndex]=(segments[s.itemIndex]?:0.0)+distance;s.speed=(distance/((next.mono-prior!!.mono)/1000.0)).takeIf { location.hasSpeed()&&(!location.hasSpeedAccuracy()||location.speedAccuracyMetersPerSecond<=1.5) };s.route.add(next);last=next} else if(prior==null||next.mono-prior.mono>30000){last=next;s.route.add(next);s.speed=null}
  if(s.route.size>20000)s.route.removeAt(0)
  s.gpsStatus="GPS ±${location.accuracy.toInt()} m";s.timer.checkpoint(n);r.save(s)
 }
 override fun onProviderDisabled(provider:String){val r=SportsHub.repo(this);r.active()?.let { it.gpsStatus="GPS disabled · no distance recorded";it.speed=null;r.save(it) };last=null}
 override fun onDestroy(){handler.removeCallbacks(tick);steps.stop();getSystemService(LocationManager::class.java).removeUpdates(this);running=false;SportsHub.repo(this).active()?.let { if(it.status=="ACTIVE"&&it.mode=="Outdoor GPS"){SportsEngine.pause(it,SportsHub.now(this));SportsHub.repo(this).save(it)} };super.onDestroy()}
}
object SportsAlarms {
 fun schedule(c:Context,key:String,count:ExerciseCountdown){val n=SportsHub.now(c);val i=Intent(c,SportsAlarmReceiver::class.java).setAction("sports.timer").putExtra("key",key);val pending=PendingIntent.getBroadcast(c,key.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val alarms=c.getSystemService(AlarmManager::class.java);alarms.cancel(pending);if(!count.timer.running||count.remaining(n)<=0)return;val whenAt=SystemClock.elapsedRealtime()+count.remaining(n);if(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms())alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,whenAt,pending) else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,whenAt,pending)
 }
 fun notify(c:Context){val p=c.getSharedPreferences("fit100",0);(c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(VibrationEffect.createOneShot(350,VibrationEffect.DEFAULT_AMPLITUDE));if(p.getBoolean("sports-sound",false))android.media.RingtoneManager.getRingtone(c,android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play()
  val nm=c.getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("sports-timers","Exercise timers",NotificationManager.IMPORTANCE_DEFAULT));if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)nm.notify(411,Notification.Builder(c,"sports-timers").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Fit100 · Timer complete").setContentText("Your next set is ready.").setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).putExtra("open-sports",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
 }
 fun check(c:Context,key:String){val r=SportsHub.repo(c);val s=r.active();val count=if(key=="common")r.countdown() else if(key=="rest")s?.rest else s?.items?.getOrNull(s.itemIndex)?.round
  if(count!=null&&count.timer.running&&!count.notified&&count.remaining(SportsHub.now(c))==0L){count.notified=true;count.timer.pause(SportsHub.now(c));count.timer.accumulated=count.duration;if(key=="common")r.countdown(count) else if(s!=null)r.save(s);notify(c)}
 }
}
class SportsAlarmReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action=="sports.pause"){val r=SportsHub.repo(c);r.active()?.let { SportsEngine.pause(it,SportsHub.now(c));r.save(it) };c.stopService(Intent(c,SportsTrackingService::class.java))}else SportsAlarms.check(c,i.getStringExtra("key")?:"common")}}

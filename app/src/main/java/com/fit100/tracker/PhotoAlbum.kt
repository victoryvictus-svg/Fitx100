package com.fit100.tracker
import android.app.*
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.time.LocalDate

data class AlbumPhoto(val key:String,val date:LocalDate?,val label:String,val uri:String)
object AlbumRecords {
 fun photos(values:Map<String,*>)=values.mapNotNull { (key,value)->
  val uri=value as? String?:return@mapNotNull null
  val match=Regex("(workout-photo|body-photo)-(\\d{4}-\\d{2}-\\d{2})-(before|after|front|side|back)").matchEntire(key)
  if(match!=null) { val date=runCatching { LocalDate.parse(match.groupValues[2]) }.getOrNull()?:return@mapNotNull null;AlbumPhoto(key,date,match.groupValues[3].replaceFirstChar { it.uppercase() },uri) }
  else Regex("photo-(\\d{1,3})").matchEntire(key)?.let { val day=it.groupValues[1].toInt();if(day in 1..100)AlbumPhoto(key,null,"Earlier Day $day",uri) else null }
 }.sortedWith(compareByDescending<AlbumPhoto> { it.date?:LocalDate.MIN }.thenBy { it.label })
}
class PhotoAlbum(private val a:Activity,private val prefs:SharedPreferences,private val page:LinearLayout,private val filter:String,private val limit:Int,private val change:(String)->Unit,private val more:()->Unit,private val choose:(String)->Unit,private val load:(ImageView,String)->Unit,private val open:(String)->Unit,private val back:()->Unit,private val start:Long?) {
 private val ink=Color.rgb(15,36,21);private val lime=Color.rgb(159,250,77);private val muted=Color.rgb(65,91,64)
 private fun dp(n:Int)=(n*a.resources.displayMetrics.density).toInt()
 private fun shape(c:Int)=GradientDrawable().apply { setColor(c);cornerRadius=dp(18).toFloat() }
 private fun text(s:String,size:Float=14f,color:Int=ink)=TextView(a).apply { text=s;textSize=size;setTextColor(color) }
 private fun add(p:LinearLayout,v:android.view.View) { p.addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) }) }
 private fun button(p:LinearLayout,label:String,action:()->Unit) { add(p,Button(a).apply { text=label;isAllCaps=false;setTextColor(ink);background=shape(lime);minimumHeight=dp(48);setOnClickListener { action() } }) }
 fun render() {
  button(page,"Back",back);add(page,text("Your Daily Photo Album",25f));add(page,text("Every saved workout and body photo, together by date.",13f,muted))
  val all=AlbumRecords.photos(prefs.all);add(page,text("${all.size} photos | ${all.mapNotNull { it.date }.distinct().size} recorded dates",14f))
  val types=LinearLayout(a);listOf("All Photos","Before & After","Body Photos").forEach { name->types.addView(Button(a).apply { text=name;isAllCaps=false;setPadding(dp(12),dp(6),dp(12),dp(6));setTextColor(if(filter==name)ink else Color.WHITE);background=shape(if(filter==name)lime else ink);setOnClickListener { change(name) } },LinearLayout.LayoutParams(-2,dp(48))) };add(page,HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled=false;addView(types) })
  button(page,"Add Today's Photo") { addFor(LocalDate.now()) }
  button(page,"Add for Another Date") { val today=LocalDate.now();DatePickerDialog(a,{_,y,m,d->addFor(LocalDate.of(y,m+1,d))},today.year,today.monthValue-1,today.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show() }
  val photos=all.filter { filter=="All Photos" || if(filter=="Body Photos")it.key.startsWith("body-photo-") else it.key.startsWith("workout-photo-") }
  if(photos.isEmpty())add(page,text("Your album starts here. Take a photo or choose one from your gallery.",16f,muted))
  photos.take(limit).groupBy { it.date }.forEach { (date,entries)->
   add(page,text(date?.toString()?:"Earlier Progress Photos",20f))
   if(date!=null && start!=null) { val index=java.time.temporal.ChronoUnit.DAYS.between(ChallengeClock.date(start),date)+1;if(index in 1..100)add(page,text("Challenge calendar day $index",12f,muted)) }
   entries.chunked(if(a.resources.configuration.screenWidthDp<360 || a.resources.configuration.fontScale>1.15f)1 else 2).forEach { pair->val row=LinearLayout(a)
    pair.forEach { item->val tile=LinearLayout(a).apply { orientation=1;background=shape(Color.rgb(231,243,221));setPadding(dp(10),dp(10),dp(10),dp(10)) }
     val frame=FrameLayout(a).apply { background=shape(Color.rgb(210,229,198)) }
     frame.addView(text(if(prefs.getBoolean("hide-progress-photos",false))"Preview hidden - tap to view" else "Loading photo...").apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
     val image=ImageView(a).apply { scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="${item.label}, ${date?:"earlier progress"}. Open photo";isFocusable=true;setOnClickListener { open(item.uri) } };frame.addView(image,FrameLayout.LayoutParams(-1,-1));if(!prefs.getBoolean("hide-progress-photos",false))load(image,item.uri)
     tile.addView(frame,LinearLayout.LayoutParams(-1,dp(190)));add(tile,text(item.label,16f));button(tile,"View Full Photo") { open(item.uri) };button(tile,"Change Photo") { choose(item.key) }
     row.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(3);marginEnd=dp(3) })
    };if(pair.size==1 && a.resources.configuration.screenWidthDp>=360 && a.resources.configuration.fontScale<=1.15f)row.addView(android.view.View(a),LinearLayout.LayoutParams(0,0,1f));add(page,row)
   }
  }
  if(photos.size>limit)button(page,"Load More Photos",more)
  add(page,text("Photos are private to this app. New gallery selections are copied into app storage. Earlier linked photos still require their original provider. Uninstalling removes app-owned photos; save important originals separately.",12f,muted))
 }
 private fun addFor(date:LocalDate) { AlertDialog.Builder(a).setTitle("Photo for $date").setItems(arrayOf("Before workout","After workout","Front","Side","Back")) { _,which->val phase=listOf("before","after","front","side","back")[which];choose(if(which<2)"workout-photo-$date-$phase" else "body-photo-$date-$phase") }.setNegativeButton("Cancel",null).show() }
}

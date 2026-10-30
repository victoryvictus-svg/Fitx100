package com.fit100.tracker
import android.app.*
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.time.LocalDate
import org.json.JSONObject

data class SportSession(val id:String,val sport:String,val date:LocalDate,val minutes:Double,val km:Double?,val notes:String)
object SportsRecords {
 val sports=listOf("Walking","Running","Cycling","Swimming","Gym / Strength","Yoga","Cricket","Football","Badminton","Tennis","Basketball","Volleyball","Table Tennis","Hockey","Kabaddi","Boxing","Martial Arts","Wrestling","Rugby","Baseball","Softball","Golf","Squash","Pickleball","Handball","Netball","Futsal","Athletics","Hiking","Trekking","Rowing","Kayaking","Canoeing","Surfing","Skating","Skateboarding","Skiing","Snowboarding","Climbing","Gymnastics","Dance","Pilates","Jump Rope","HIIT","Cross Training","Archery","Shooting","Bowling","Diving","Water Polo","Triathlon","Other / Custom")
 fun parse(id:String,raw:String):SportSession?=runCatching {
  val j=JSONObject(raw);val m=j.getDouble("minutes");val km=if(j.has("km"))j.getDouble("km") else null
  require(m.isFinite() && m>0 && m<=1440 && (km==null || km.isFinite() && km>=0 && km<=10000))
  SportSession(id,j.getString("sport"),LocalDate.parse(j.getString("date")),m,km,j.optString("notes"))
 }.getOrNull()
 fun timerMillis(start:Long,now:Long=System.currentTimeMillis())=(now-start).coerceIn(0,86_400_000)
}
class SportsDashboard(private val a:Activity,private val prefs:SharedPreferences,private val page:LinearLayout,private val refresh:()->Unit) {
 private val ink=Color.rgb(14,34,20);private val muted=Color.rgb(60,87,60);private val lime=Color.rgb(159,250,77)
 private fun dp(n:Int)=(n*a.resources.displayMetrics.density).toInt()
 private fun shape(c:Int)=GradientDrawable().apply { setColor(c);cornerRadius=dp(18).toFloat() }
 private fun add(p:LinearLayout,v:android.view.View) { p.addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(9) }) }
 private fun text(s:String,size:Float=14f)=TextView(a).apply { text=s;textSize=size;setTextColor(ink) }
 private fun card(title:String)=LinearLayout(a).apply { orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));background=shape(Color.rgb(232,244,222));add(page,this);add(this,text(title,21f).apply { setTypeface(null,Typeface.BOLD) }) }
 private fun note(p:LinearLayout,s:String) { add(p,text(s,13f).apply { setTextColor(muted) }) }
 private fun button(p:LinearLayout,s:String,action:()->Unit) { add(p,Button(a).apply { text=s;isAllCaps=false;setTextColor(ink);background=shape(lime);minimumHeight=dp(48);setOnClickListener { action() } }) }
 private fun input(p:LinearLayout,label:String,key:String,numeric:Boolean=false):EditText {
  note(p,label);return EditText(a).apply { setText(prefs.getString("sports-draft-$key",""));setTextColor(ink);minimumHeight=dp(48);if(numeric)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setSingleLine(key!="notes");addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){prefs.edit().putString("sports-draft-$key",s.toString()).apply()};override fun afterTextChanged(s:android.text.Editable?){} });add(p,this) }
 }
 private fun sessions()=prefs.all.filterKeys { it.startsWith("sport-session-") }.mapNotNull { (k,v)->(v as? String)?.let { SportsRecords.parse(k,it) } }.sortedByDescending { it.date }
 private fun save(sport:String,date:LocalDate,minutes:Double,km:Double?,notes:String) {
  val json=JSONObject().put("sport",sport).put("date",date.toString()).put("minutes",minutes).put("notes",notes);if(km!=null)json.put("km",km)
  prefs.edit().putString("sport-session-${java.util.UUID.randomUUID()}",json.toString()).apply()
 }
 fun render() {
  val all=sessions();val week=all.filter { it.date>=LocalDate.now().minusDays(6) && it.date<=LocalDate.now() }
  val hero=card("Sports & Activities");note(hero,"Choose a sport, time your activity, or log a past session. Use Other / Custom for any sport not listed.")
  note(hero,"This week: ${week.size} sessions | %.1f minutes".format(week.sumOf { it.minutes }))
  note(hero,"Manual activity tracking. Distance and notes are entered by you. Recording a sport does not start your 100-day challenge.")
  val form=card("Choose Your Sport")
  val spinner=Spinner(a).apply { adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,SportsRecords.sports);setSelection(SportsRecords.sports.indexOf(prefs.getString("sports-selected","Walking")).coerceAtLeast(0)) };add(form,spinner)
  val custom=input(form,"Custom sport name (used for Other / Custom)","custom")
  spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent:AdapterView<*>?){};override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long){prefs.edit().putString("sports-selected",SportsRecords.sports[position]).apply();custom.visibility=if(position==SportsRecords.sports.lastIndex)android.view.View.VISIBLE else android.view.View.GONE} }
  fun selected():String? { val value=spinner.selectedItem.toString();return if(value=="Other / Custom")custom.text.toString().trim().takeIf { it.isNotBlank() && it.length<=80 } else value }
  val start=prefs.getLong("sports-active-start",0)
  if(start>0) {
   val live=card("Active: ${prefs.getString("sports-active-name","Sport")}")
   add(live,Chronometer(a).apply { base=SystemClock.elapsedRealtime()-SportsRecords.timerMillis(start);textSize=32f;setTextColor(ink);start() })
   note(live,"Elapsed time continues while the app is closed. Sessions are limited to 24 hours; you can correct the duration using a manual log.")
   button(live,"Finish & Save Session") { val duration=SportsRecords.timerMillis(start)/60000.0;if(duration<=0) { Toast.makeText(a,"Record some time first",0).show();return@button };save(prefs.getString("sports-active-name","Sport")?:"Sport",java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),duration,null,"");prefs.edit().remove("sports-active-start").remove("sports-active-name").apply();refresh() }
   button(live,"Discard Active Session") { AlertDialog.Builder(a).setTitle("Discard this timer?").setMessage("This active session will not be saved.").setNegativeButton("Keep Recording",null).setPositiveButton("Discard") { _,_->prefs.edit().remove("sports-active-start").remove("sports-active-name").apply();refresh() }.show() }
  } else button(form,"Start Activity Timer") { val sport=selected();if(sport==null) { custom.error="Enter a sport name (up to 80 characters)";return@button };prefs.edit().putLong("sports-active-start",System.currentTimeMillis()).putString("sports-active-name",sport).apply();refresh() }
  val manual=card("Log a Session")
  val date=runCatching { LocalDate.parse(prefs.getString("sports-draft-date",LocalDate.now().toString())) }.getOrDefault(LocalDate.now())
  button(manual,"Date: $date") { DatePickerDialog(a,{_,y,m,d->prefs.edit().putString("sports-draft-date",LocalDate.of(y,m+1,d).toString()).apply();refresh()},date.year,date.monthValue-1,date.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show() }
  val minutes=input(manual,"Duration (minutes)","minutes",true);val distance=input(manual,"Distance (km, optional)","km",true);val notes=input(manual,"Notes: score, laps, sets, route or personal best (optional)","notes")
  button(manual,"Save Session") {
   val sport=selected();val duration=minutes.text.toString().toDoubleOrNull();val raw=distance.text.toString().trim();val km=raw.toDoubleOrNull();var invalid=false
   if(sport==null){custom.error="Enter a sport name (up to 80 characters)";invalid=true}
   if(duration==null || !duration.isFinite() || duration<=0 || duration>1440){minutes.error="Enter more than 0 and up to 1440 minutes";invalid=true}
   if(raw.isNotEmpty() && (km==null || !km.isFinite() || km<0 || km>10000)){distance.error="Enter 0 to 10000 km, or leave blank";invalid=true}
   if(date>LocalDate.now()){Toast.makeText(a,"Choose today or an earlier date",0).show();invalid=true}
   if(invalid)return@button
   save(sport!!,date,duration!!,km,notes.text.toString().take(2000));prefs.edit().remove("sports-draft-minutes").remove("sports-draft-km").remove("sports-draft-notes").remove("sports-draft-date").apply();(a.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(page.windowToken,0);Toast.makeText(a,"Sports session saved",0).show();refresh()
  }
  val history=card("Activity History")
  if(all.isEmpty())note(history,"No sessions yet. Start a timer or save your first activity above.")
  all.take(prefs.getInt("sports-history-limit",20)).forEach { session->add(history,text("${session.sport} | ${session.date}",17f));note(history,"%.1f min".format(session.minutes)+(session.km?.let { " | %.2f km".format(it) }?:""));if(session.notes.isNotBlank())note(history,session.notes);button(history,"Delete Session") { AlertDialog.Builder(a).setTitle("Delete ${session.sport} session?").setMessage("${session.date}: %.1f minutes".format(session.minutes)).setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_->prefs.edit().remove(session.id).apply();refresh() }.show() } }
  if(all.size>prefs.getInt("sports-history-limit",20))button(history,"Show More Sessions") { prefs.edit().putInt("sports-history-limit",prefs.getInt("sports-history-limit",20)+20).apply();refresh() }
 }
}

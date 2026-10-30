package com.fit100.tracker

import android.app.*
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.LocalDate
import org.json.JSONObject

/** Reference-inspired native sports hub. All completion data is local and dated. */
class WorkoutDashboard(private val a:Activity, private val p:SharedPreferences, private val page:LinearLayout, private val refresh:()->Unit) {
 private val lime=Color.rgb(140,255,39); private val white=Color.rgb(243,250,240); private val muted=Color.rgb(170,190,174)
 private val today=LocalDate.now().toString()
 private val groups=listOf("Chest","Back","Shoulders","Arms","Legs","Core","Cardio","Full Body")
 private val exercises=listOf(
  Triple("Barbell Bench Press","Chest","4 × 8–12 reps"),Triple("Incline Dumbbell Press","Chest","3 × 10–12 reps"),Triple("Push Ups","Chest","3 × 12–15 reps"),
  Triple("Lat Pulldown","Back","3 × 10–12 reps"),Triple("Seated Cable Row","Back","3 × 10–12 reps"),Triple("Dumbbell Shoulder Press","Shoulders","3 × 10–12 reps"),
  Triple("Lateral Raise","Shoulders","3 × 12–15 reps"),Triple("Biceps Curl","Arms","3 × 10–12 reps"),Triple("Triceps Pushdown","Arms","3 × 10–12 reps"),
  Triple("Goblet Squat","Legs","3 × 10–12 reps"),Triple("Plank","Core","3 × 30 seconds"),Triple("Brisk Walk","Cardio","10 minutes"),Triple("Bodyweight Squat","Full Body","3 × 12 reps"))
 private fun dp(v:Int)=(v*a.resources.displayMetrics.density).toInt()
 private fun bg(color:Int=Color.rgb(8,28,17))=GradientDrawable().apply { setColor(color);cornerRadius=dp(14).toFloat();setStroke(dp(1),Color.rgb(40,65,43)) }
 private fun text(s:String,size:Float=14f,color:Int=white)=TextView(a).apply { text=s;textSize=size;setTextColor(color);setPadding(0,dp(3),0,dp(3)) }
 private fun add(parent:LinearLayout,v:View)=parent.addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
 private fun card(title:String,parent:LinearLayout=page)=LinearLayout(a).apply { orientation=1;background=bg();setPadding(dp(14),dp(12),dp(14),dp(14));add(parent,this);if(title.isNotBlank())add(this,text(title,18f).apply { setTypeface(null,Typeface.BOLD) }) }
 private fun button(parent:LinearLayout,label:String,primary:Boolean=true,action:()->Unit) { add(parent,Button(a).apply { text=label;isAllCaps=false;setTextColor(if(primary)Color.rgb(5,20,8) else white);background=bg(if(primary)lime else Color.rgb(16,40,24));minimumHeight=dp(48);setOnClickListener { action() } }) }
 private fun go(screen:String) { p.edit().putString("workout-screen",screen).apply();refresh() }
 private fun selected()=p.getStringSet("workout-selected",setOf(exercises[0].first,exercises[3].first,exercises[5].first,exercises[7].first))!!.toSet()
 private fun saved()=p.all.filterKeys { it.startsWith("sport-session-") }.mapNotNull { (k,v)->(v as? String)?.let { SportsRecords.parse(k,it) } }.sortedByDescending { it.date }
 private fun art(parent:LinearLayout) { add(parent,object:View(a) {
  private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(c:Canvas) { val w=width.toFloat();val h=height.toFloat();pen.shader=LinearGradient(0f,0f,w,h,Color.rgb(30,61,38),Color.rgb(3,16,10),Shader.TileMode.CLAMP);c.drawRoundRect(0f,0f,w,h,dp(12).toFloat(),dp(12).toFloat(),pen);pen.shader=null
   pen.color=Color.rgb(65,89,67);pen.strokeWidth=dp(2).toFloat();for(i in 1..6)c.drawLine(w*i/7,0f,w*i/7,h,pen)
   pen.color=lime;pen.strokeWidth=dp(8).toFloat();pen.strokeCap=Paint.Cap.ROUND;c.drawLine(w*.22f,h*.42f,w*.78f,h*.42f,pen)
   pen.color=white;for(x in listOf(.24f,.30f,.70f,.76f)) { c.drawRoundRect(w*x-dp(7),h*.20f,w*x+dp(7),h*.65f,dp(4).toFloat(),dp(4).toFloat(),pen) }
   pen.color=lime;pen.textSize=dp(12).toFloat();pen.typeface=Typeface.DEFAULT_BOLD;c.drawText("BUILD STRENGTH. STAY CONSISTENT.",dp(14).toFloat(),h*.88f,pen)
  }
 }.apply { minimumHeight=dp(145);contentDescription="Barbell strength training illustration" }) }
 fun render() {
  val screen=p.getString("workout-screen","Workout")?:"Workout"
  if(screen=="Activity") { button(page,"‹ Back to Sports",false){go("Workout")};SportsDashboard(a,p,page,refresh).render();return }
  if(screen!="Workout")button(page,"‹ Sports  /  $screen",false){go("Workout")}
  val tabs=LinearLayout(a).apply { orientation=0 };val scroll=HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled=false;addView(tabs) };add(page,scroll)
  listOf("Workout","Exercises","Plans","Activity Log").forEach { title->tabs.addView(Button(a).apply { text=title;isAllCaps=false;textSize=12f;setTextColor(if(screen==title)Color.BLACK else white);background=bg(if(screen==title)lime else Color.rgb(12,32,20));setOnClickListener { go(title) } },LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(5) }) }
  when(screen) { "Workout"->home();"Exercises"->library();"Plans"->plans();"Activity Log"->history();"Session"->session();"Rest Day"->rest();else->detail(screen) }
 }
 private fun home() {
  val chosen=selected();val sets=p.getInt("workout-sets-$today",0)
  val hero=card("Today’s Workout");add(hero,text(p.getString("workout-plan","Upper Body Strength")!!,24f,lime));add(hero,text("${LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM"))}  •  ${chosen.size} exercises",13f,muted));art(hero)
  add(hero,text("${sets} sets logged today  •  Go at your own pace",13f,muted));button(hero,if(p.getLong("workout-start",0)>0)"Resume Workout  →" else "Start Workout  →"){start()}
  val stats=card("Your training, at a glance");add(stats,text("${chosen.size} exercises   •   3–4 sets each   •   50–60 min",14f,lime));add(stats,text("Suggested duration; adapt sets and loads to your ability.",12f,muted))
  val week=card("This Week");val days=(6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) };add(week,text(days.joinToString("    ") { it.dayOfWeek.name.take(1) },16f,muted));add(week,text(days.joinToString("    ") { d->if(saved().any { it.date==d })"✓" else "○" },20f,lime));add(week,text("${saved().count { it.date in days }} recorded sessions in the last 7 days",13f,muted))
  val quick=card("Quick Actions");button(quick,"Explore Workout Plans",false){go("Plans")};button(quick,"Exercise Library",false){go("Exercises")};button(quick,"Track a Sport / Activity",false){go("Activity")};button(quick,"Recovery & Rest Day",false){go("Rest Day")}
 }
 private fun start() { if(selected().isEmpty()){Toast.makeText(a,"Add an exercise first",0).show();go("Exercises");return};if(p.getLong("workout-start",0)==0L)p.edit().putLong("workout-start",System.currentTimeMillis()).apply();go("Session") }
 private fun library() {
  val head=card("Exercise Library");add(head,text("Tap an exercise for instructions. Add it to today’s workout.",13f,muted))
  val search=EditText(a).apply { hint="Search exercises";setHintTextColor(muted);setTextColor(white);setSingleLine(true) };add(head,search)
  val filter=Spinner(a);filter.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("All")+groups);add(head,filter)
  val results=LinearLayout(a).apply { orientation=1 };add(page,results)
  fun populate() { results.removeAllViews();val category=filter.selectedItem?.toString()?:"All";exercises.filter { (category=="All" || it.second==category) && it.first.contains(search.text.toString(),true) }.forEach { ex->val c=card(ex.first,results);add(c,text("${ex.second}  •  ${ex.third}",13f,muted));button(c,"Instructions  ›",false){go(ex.first)};val included=ex.first in selected();button(c,if(included)"✓ Added · tap to remove" else "+ Add to Workout",included){val next=selected().toMutableSet();if(included)next.remove(ex.first) else next.add(ex.first);p.edit().putStringSet("workout-selected",next).apply();populate()} };if(results.childCount==0)add(results,text("No matching exercises.")) }
  search.addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,af:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){populate()};override fun afterTextChanged(s:android.text.Editable?){} })
  filter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){populate()} };populate()
 }
 private fun plans() { listOf("Upper Body Strength" to setOf(0,3,5,7),"Full Body Foundation" to setOf(0,3,9,10),"Home Workout" to setOf(2,10,12),"Cardio & Core" to setOf(10,11)).forEach { (name,indices)->val c=card(name);add(c,text("${indices.size} exercises  •  Editable after selecting",13f,muted));button(c,if(p.getString("workout-plan","Upper Body Strength")==name)"✓ Selected Plan" else "Use This Plan"){p.edit().putString("workout-plan",name).putStringSet("workout-selected",indices.map { exercises[it].first }.toSet()).apply();go("Workout")} } }
 private fun detail(name:String) { val ex=exercises.firstOrNull { it.first==name }?:return;val c=card(name);art(c);add(c,text("${ex.second}  •  ${ex.third}",16f,lime));val instructions=when(name) { "Barbell Bench Press"->listOf("Lie on a bench with feet planted and a spotter for heavy sets.","Grip slightly wider than shoulder width; keep wrists stacked.","Lower the bar under control toward your chest.","Press up smoothly, keeping shoulders supported.");"Plank"->listOf("Place forearms under shoulders.","Brace your core and keep a straight line from head to heels.","Breathe steadily; stop when form breaks.");else->listOf("Choose a manageable resistance and warm up first.","Set a stable position and brace your core.","Move through a comfortable range with control.","Breathe throughout; stop if you feel pain.") };instructions.forEachIndexed { i,s->add(c,text("${i+1}   $s",15f)) };if(name!="Barbell Bench Press" && name!="Plank")add(c,text("General training cues. Ask a qualified trainer for exercise-specific technique.",12f,muted));button(c,"Start / Resume Workout"){start()} }
 private fun session() {
  val c=card("Today’s Workout");val start=p.getLong("workout-start",System.currentTimeMillis());add(c,Chronometer(a).apply { base=SystemClock.elapsedRealtime()-SportsRecords.timerMillis(start);setTextColor(lime);textSize=30f;start() });add(c,text("${p.getInt("workout-sets-$today",0)} sets logged today",14f,muted))
  selected().forEach { name->val ex=card(name);add(ex,text(exercises.firstOrNull { it.first==name }?.third?:"Custom exercise",13f,muted));val weight=EditText(a).apply { hint="Weight (kg), optional";setHintTextColor(muted);setTextColor(white);inputType=8194 };add(ex,weight);val reps=EditText(a).apply { hint="Reps or seconds";setHintTextColor(muted);setTextColor(white);inputType=2 };add(ex,reps)
   button(ex,"Log Set"){val count=reps.text.toString().toIntOrNull();val raw=weight.text.toString();val kg=raw.toDoubleOrNull();if(count==null || count !in 1..10000){reps.error="Enter 1–10000";return@button};if(raw.isNotBlank()&&(kg==null || !kg.isFinite() || kg<0 || kg>1000)){weight.error="Enter 0–1000 kg";return@button};val key="workout-set-${java.util.UUID.randomUUID()}";p.edit().putString(key,JSONObject().put("date",today).put("exercise",name).put("reps",count).put("kg",kg).toString()).putInt("workout-sets-$today",p.getInt("workout-sets-$today",0)+1).putLong("workout-rest-until",System.currentTimeMillis()+60000).apply();refresh()}
  }
  val rest=card("Rest Timer");val end=p.getLong("workout-rest-until",0);val remain=(end-System.currentTimeMillis()).coerceAtLeast(0);add(rest,Chronometer(a).apply { isCountDown=true;base=SystemClock.elapsedRealtime()+remain;textSize=28f;setTextColor(lime);setOnChronometerTickListener { if(SystemClock.elapsedRealtime()>=base){stop();text="00:00"} };if(remain>0)start() else text="00:00" });button(rest,"Start 60-second Rest",false){p.edit().putLong("workout-rest-until",System.currentTimeMillis()+60000).apply();refresh()}
  button(page,"Complete Workout"){AlertDialog.Builder(a).setTitle("Save completed workout?").setMessage("Save elapsed time to your activity history.").setNegativeButton("Keep training",null).setPositiveButton("Save"){_,_->val min=(SportsRecords.timerMillis(start)/60000.0).coerceAtLeast(0.01);val date=java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate();p.edit().putString("sport-session-${java.util.UUID.randomUUID()}",JSONObject().put("sport",p.getString("workout-plan","Strength Workout")).put("date",date.toString()).put("minutes",min).put("notes","Strength training session").toString()).remove("workout-start").remove("workout-rest-until").apply();go("Activity Log")}.show() }
 }
 private fun history() { val all=saved();val week=all.filter { it.date>=LocalDate.now().minusDays(6)&&it.date<=LocalDate.now() };val c=card("Activity Log");add(c,text("LAST 7 DAYS",12f,lime));add(c,text("${week.size} sessions  •  %.0f minutes".format(week.sumOf { it.minutes }),23f));button(c,"Track / Log an Activity",false){go("Activity")};if(all.isEmpty())add(c,text("Your first workout starts your history.",14f,muted));all.take(30).forEach { s->val row=card(s.sport);add(row,text("${s.date}  •  %.1f min".format(s.minutes)+(s.km?.let { "  •  %.2f km".format(it) }?:""),13f,lime));if(s.notes.isNotBlank())add(row,text(s.notes,13f,muted)) } }
 private fun rest() { val c=card("Recovery Today");art(c);add(c,text("Rest is part of progress",22f,lime));listOf("Light stretch · 10 minutes","Mobility routine","Stay hydrated","Focus on nutrition","Good sleep · 7–8 hours").forEachIndexed { i,s->add(c,CheckBox(a).apply { text=s;setTextColor(white);buttonTintList=android.content.res.ColorStateList.valueOf(lime);isChecked=p.getBoolean("recovery-$today-$i",false);setOnCheckedChangeListener { _,checked->p.edit().putBoolean("recovery-$today-$i",checked).apply() } }) };add(c,text("“Recovery today builds a stronger you tomorrow.”",16f,muted)) }
}

package com.fit100.tracker

import android.app.*
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.time.LocalDate

class DietState {
    var metric="Calories";var screen="Meal Plan"; var section="Today"; var selected=0; var recipe=0; var filter="All"; var date=LocalDate.now()
    val drafts=mutableMapOf<String,String>()
    fun save()=Bundle().apply { putString("metric",metric);putString("screen",screen);putString("section",section);putInt("selected",selected);putInt("recipe",recipe);putString("filter",filter);putString("date",date.toString());putBundle("drafts",Bundle().apply { drafts.forEach { (k,v)->putString(k,v) } }) }
    fun restore(b:Bundle?) { if(b==null)return;metric=b.getString("metric","Calories");screen=b.getString("screen","Meal Plan");section=b.getString("section","Today");selected=b.getInt("selected");recipe=b.getInt("recipe");filter=b.getString("filter","All");date=runCatching { LocalDate.parse(b.getString("date")) }.getOrDefault(LocalDate.now());b.getBundle("drafts")?.let { d->d.keySet().forEach { drafts[it]=d.getString(it,"") } } }
}
data class DietRecipe(val name:String,val category:String,val kcal:Int,val protein:Int,val carbs:Int,val fat:Int,val ingredients:List<String>,val steps:List<String>,val tags:Set<String>,val minutes:Int)
object DietFood {
 val recipes=listOf(
 DietRecipe("Oats Upma + 2 Eggs","Breakfast",450,25,55,12,listOf("Oats \u2022 50 g dry","Eggs \u2022 2","Onion \u2022 50 g","Carrot \u2022 50 g","Peas \u2022 50 g","Oil & spices \u2022 1 tsp"),listOf("Heat oil in a pan and soften chopped onion.","Add carrots and peas; cook until tender.","Stir in oats, water and spices; cook until soft.","Serve with two fully cooked eggs."),setOf("Eggs"),20),
 DietRecipe("Fruit & Roasted Chana","Snacks",200,8,35,3,listOf("Seasonal fruit \u2022 150 g","Roasted chana \u2022 30 g"),listOf("Wash and cut the fruit.","Serve with roasted chana."),emptySet(),5),
 DietRecipe("Chicken Rice Bowl","Lunch",520,35,60,12,listOf("Chicken \u2022 120 g","Rice \u2022 60 g dry","Mixed vegetables \u2022 150 g","Oil & spices \u2022 1 tsp"),listOf("Cook rice according to the pack.","Cook chicken thoroughly with spices.","Steam vegetables and serve together."),setOf("Chicken"),30),
 DietRecipe("Banana & Peanut Toast","Snacks",280,9,42,9,listOf("Banana \u2022 1","Bread \u2022 2 slices","Peanuts \u2022 15 g"),listOf("Toast bread.","Top with sliced banana and crushed peanuts."),setOf("Bread","Peanuts"),5),
 DietRecipe("Soy Chapati Bowl","Dinner",480,30,65,11,listOf("Soy chunks \u2022 40 g dry","Atta \u2022 60 g","Mixed vegetables \u2022 150 g","Oil & spices \u2022 1 tsp"),listOf("Prepare soy chunks according to the pack.","Cook with vegetables and spices.","Make chapatis and serve."),setOf("Soy","Wheat"),30),
 DietRecipe("Dal Rice & Salad","Lunch",460,18,75,9,listOf("Dal \u2022 60 g dry","Rice \u2022 50 g dry","Cucumber \u2022 100 g","Oil & spices \u2022 1 tsp"),listOf("Cook dal until soft and season.","Cook rice and wash salad vegetables.","Serve dal over rice with salad."),emptySet(),30),
 DietRecipe("Chickpea Vegetable Bowl","Dinner",420,19,60,12,listOf("Cooked chickpeas \u2022 180 g","Mixed vegetables \u2022 150 g","Oil & spices \u2022 1 tsp"),listOf("Cook vegetables until tender.","Add cooked chickpeas and spices.","Warm through and serve."),emptySet(),20))
 val slots=listOf("Breakfast","Mid Morning","Lunch","Pre-Workout","Post-Workout","Dinner")
 val times=listOf("08:00","11:00","13:00","16:00","19:00","20:00")
 val defaults=listOf(0,1,2,3,1,4)
 fun allowed(r:DietRecipe,food:String,exclusions:String,dairy:String):Boolean {
  if(food=="Vegan" && r.tags.any { it in setOf("Eggs","Chicken","Milk") })return false
  if(food in listOf("Vegetarian","Eggetarian") && "Chicken" in r.tags)return false
  if(food=="Vegetarian" && "Eggs" in r.tags)return false
  if(dairy=="Dairy-free" && "Milk" in r.tags)return false
  val blocked=exclusions.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
  return blocked.none { word->r.ingredients.any { it.lowercase().contains(word) } }
 }
}
class DietDashboard(private val a:Activity,private val prefs:SharedPreferences,private val page:LinearLayout,private val state:DietState,private val navigate:(String)->Unit,private val refresh:()->Unit) {
 private val lime=Color.rgb(159,250,77);private val ink=Color.rgb(16,35,20);private val muted=Color.rgb(69,96,64);private val pale=Color.rgb(232,244,222)
 private fun dp(n:Int)=(n*a.resources.displayMetrics.density).toInt()
 private fun shape(c:Int)=GradientDrawable().apply { setColor(c);cornerRadius=dp(18).toFloat() }
 private fun col()=LinearLayout(a).apply { orientation=1 }
 private fun text(s:String,size:Float=14f,color:Int=ink,bold:Boolean=false)=TextView(a).apply { text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD) }
 private fun add(p:LinearLayout,v:android.view.View,top:Int=8) { p.addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) }) }
 private fun card(title:String,parent:LinearLayout=page):LinearLayout { val c=col().apply { background=shape(pale);setPadding(dp(16),dp(16),dp(16),dp(16)) };add(parent,c,12);add(c,text(title,19f,ink,true),0);return c }
 private fun note(p:LinearLayout,s:String)=add(p,text(s,12f,muted))
 private fun button(p:LinearLayout,s:String,action:()->Unit) { add(p,Button(a).apply { text=s;isAllCaps=false;textSize=14f;minimumHeight=dp(48);setTextColor(ink);background=shape(lime);setOnClickListener { action() } },10) }
 private fun tabs(labels:List<String>,current:String,action:(String)->Unit) {
  val row=LinearLayout(a).apply { background=shape(ink);setPadding(dp(4),dp(4),dp(4),dp(4)) }
  labels.forEach { label->row.addView(text(label,12f,if(label==current)ink else Color.WHITE,label==current).apply { gravity=Gravity.CENTER;minimumHeight=dp(44);setPadding(dp(12),dp(8),dp(12),dp(8));if(label==current)background=shape(lime);isFocusable=true;setOnClickListener { action(label) } },LinearLayout.LayoutParams(-2,-2)) }
  add(page,HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled=false;addView(row) })
 }
 private fun value(k:String,d:String)=prefs.getString(k,d)?:d
 private fun eligible()=DietFood.recipes.indices.filter { DietFood.allowed(DietFood.recipes[it],value("profile-food","Non-vegetarian"),value("profile-allergies","Dairy"),value("profile-dairy","Dairy-free")) }
 private fun recipe(slot:Int):Int? { val saved=prefs.getInt("${state.date}-diet-eaten-$slot-recipe",-1);if(eaten(slot) && saved in DietFood.recipes.indices)return saved;val choices=eligible();val id=prefs.getInt("diet-swap-${state.date}-$slot",-1);if(id in choices)return id;val original=DietFood.defaults[slot];return if(original in choices)original else choices.firstOrNull { DietFood.recipes[it].category==DietFood.recipes[original].category }?:choices.firstOrNull() }
 private fun eaten(slot:Int)=prefs.getBoolean("${state.date}-diet-eaten-$slot",false)
 private fun summary(p:LinearLayout) {
  val ids=DietFood.slots.indices.filter { eaten(it) }.mapNotNull { recipe(it) }.map { DietFood.recipes[it] }
  note(p,"Planned meals marked eaten: ${ids.sumOf { it.kcal }} kcal \u2022 ${ids.sumOf { it.protein }} g protein\nEstimated portions; separate from daily check-in totals.")
 }
 fun render() {
  if(state.screen in listOf("Meal Plan","Recipes","Grocery List","Diet Settings","Diet Analytics"))tabs(listOf("Meal Plan","Recipes","Grocery List","Diet Settings"),state.screen) { navigate(it) }
  when(state.screen) { "Meal Details"->details();"Customize Meal Plan"->customize();"Recipes","Replace Meal"->recipes();"Grocery List"->grocery();"Diet Settings"->settings();"Diet Analytics"->analytics();else->plan() }
 }
 private fun plan() {
  val top=card("${value("diet-plan","Gym Day")} Plan")
  note(top,"${value(when(value("diet-plan","Gym Day")) { "Rest Day"->"profile-restcal";"Custom"->"diet-customcal";else->"profile-gymcal" },"2400")} kcal target \u2022 ${value("profile-protein","140")} g protein\n${value("profile-food","Non-vegetarian")} \u2022 ${value("profile-dairy","Dairy-free")}")
  button(top,"Change Meal Plan") { navigate("Customize Meal Plan") };summary(top)
  val metrics=LinearLayout(a)
  val targets=listOf(value(when(value("diet-plan","Gym Day")) { "Rest Day"->"profile-restcal";"Custom"->"diet-customcal";else->"profile-gymcal" },"2400").toDoubleOrNull()?:2400.0,value("profile-protein","140").toDoubleOrNull()?:140.0,(value("profile-watergoal","4000").toDoubleOrNull()?:4000.0)/1000)
  val actual=listOf(value("${state.date}-cal","0").toDoubleOrNull()?:0.0,value("${state.date}-protein","0").toDoubleOrNull()?:0.0,value("${state.date}-water","0").toDoubleOrNull()?:0.0)
  listOf("Calories","Protein (g)","Water (L)").forEachIndexed { i,label->val cell=col().apply { gravity=Gravity.CENTER_HORIZONTAL };cell.addView(text(if(i==2) "%.1f".format(actual[i]) else "${actual[i].toInt()}",16f,ink,true).apply { gravity=Gravity.CENTER;background=HomeArt("ring",if(targets[i]>0)(actual[i]/targets[i]).toFloat().coerceIn(0f,1f) else 0f,Color.rgb(65,160,25)) },LinearLayout.LayoutParams(dp(68),dp(68)));add(cell,text(label,11f,ink).apply { gravity=Gravity.CENTER });metrics.addView(cell,LinearLayout.LayoutParams(0,-2,1f)) };add(top,metrics,12)
  note(top,"Rings show your daily check-in totals.")
  tabs(listOf("Today","Week","Shopping List"),state.section) { if(it=="Shopping List")navigate("Grocery List") else { state.section=it;refresh() } }
  val date=card("${state.date}")
  button(date,"Choose Date") { DatePickerDialog(a,{_,y,m,d->state.date=LocalDate.of(y,m+1,d);refresh()},state.date.year,state.date.monthValue-1,state.date.dayOfMonth).show() }
  if(state.section=="Week") {
   repeat(7) { offset->val day=state.date.plusDays(offset.toLong());button(date,day.toString()+" \u2022 View Meals") { state.date=day;state.section="Today";refresh() } };note(date,"The template repeats unless you replace a meal for a specific date.");return
  }
  val meals=card("Today's Meals")
  DietFood.slots.forEachIndexed { i,slot->val id=recipe(i);val r=id?.let { DietFood.recipes[it] }
   val line=LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(12),0,dp(12));minimumHeight=dp(100);isFocusable=r!=null;contentDescription="$slot: ${r?.name?:"No matching recipe"}";if(r!=null)setOnClickListener { state.selected=i;state.recipe=id!!;navigate("Meal Details") } }
   val copy=col();add(copy,text("${value("diet-time-$i",DietFood.times[i])}  $slot",14f,ink,true),0);note(copy,r?.name?:"No recipe matches your exclusions");if(r!=null)note(copy,"~${r.kcal} kcal | P ${r.protein} g\n"+if(eaten(i))"Eaten" else "Tap for recipe")
   line.addView(copy,LinearLayout.LayoutParams(0,-2,1f));if(r!=null)line.addView(DietPlate(a,id!!),LinearLayout.LayoutParams(dp(88),dp(78)).apply { marginStart=dp(8) });add(meals,line,0)
  }
  val bottom=card("Daily Nutrition Summary");summary(bottom);note(bottom,"Daily check-in: ${value("${state.date}-cal","0")} kcal \u2022 ${value("${state.date}-protein","0")} g protein");button(bottom,"View Diet Analytics") { navigate("Diet Analytics") }
 }
 private fun details() {
  val r=DietFood.recipes.getOrElse(state.recipe) { DietFood.recipes.first() }
  val hero=card(r.name);note(hero,"${DietFood.slots[state.selected]} \u2022 ${value("diet-time-${state.selected}",DietFood.times[state.selected])} \u2022 ${r.minutes} minutes")
  hero.addView(DietPlate(a,state.recipe),LinearLayout.LayoutParams(-1,dp(190)).apply { topMargin=dp(14) })
  note(hero,"Estimated per serving: ${r.kcal} kcal\nProtein ${r.protein} g \u2022 Carbs ${r.carbs} g \u2022 Fats ${r.fat} g")
  val fav="diet-favourite-${state.recipe}";button(hero,if(prefs.getBoolean(fav,false))"Saved Recipe" else "Save Recipe") { prefs.edit().putBoolean(fav,!prefs.getBoolean(fav,false)).apply();refresh() }
  val ing=card("Ingredients \u2022 1 serving");r.ingredients.forEach { note(ing,it) }
  val steps=card("Recipe Steps");r.steps.forEachIndexed { i,s->note(steps,"${i+1}. $s") };note(steps,"Nutrition is a template estimate. Portions, brands and preparation change values. Check ingredients against your allergies.")
  if(recipe(state.selected)!=state.recipe) button(page,"Use for ${DietFood.slots[state.selected]}") { if(eaten(state.selected)) { Toast.makeText(a,"Undo Eaten before replacing this meal",0).show();return@button };prefs.edit().putInt("diet-swap-${state.date}-${state.selected}",state.recipe).apply();refresh() }
  button(page,"Replace This Meal") { state.filter="All";navigate("Replace Meal") }
  if(recipe(state.selected)==state.recipe) button(page,if(eaten(state.selected))"Undo Eaten" else "Mark as Eaten") { if(state.date>LocalDate.now()) { Toast.makeText(a,"Future meals cannot be marked eaten",0).show();return@button };val complete=!eaten(state.selected);prefs.edit().putBoolean("${state.date}-diet-eaten-${state.selected}",complete).putInt("${state.date}-diet-eaten-${state.selected}-recipe",if(complete)state.recipe else -1).apply();refresh() }
 }
 private fun recipes() {
  if(state.screen=="Replace Meal") note(card("Replace ${DietFood.slots[state.selected]}"),"Choose a recipe for ${state.date}.")
  tabs(listOf("All","Breakfast","Lunch","Dinner","Snacks","Favourites"),state.filter) { state.filter=it;refresh() }
  val ids=eligible().filter { state.filter=="All" || (state.filter=="Favourites" && prefs.getBoolean("diet-favourite-$it",false)) || DietFood.recipes[it].category==state.filter }
  if(ids.isEmpty())note(card("No matching recipes"),"Adjust filters or your meal preferences.")
  ids.forEach { id->val r=DietFood.recipes[id];val c=card(r.name);note(c,"~${r.kcal} kcal \u2022 ${r.protein} g protein \u2022 ${r.minutes} min")
   button(c,if(state.screen=="Replace Meal")"Use This Meal" else "View Recipe") { if(state.screen=="Replace Meal") { if(eaten(state.selected)) { Toast.makeText(a,"Undo Eaten before replacing this meal",0).show();return@button };prefs.edit().putInt("diet-swap-${state.date}-${state.selected}",id).apply() };state.recipe=id;navigate("Meal Details") }
  }
 }
 private fun grocery() {
  val c=card("Shopping List");note(c,"Ingredients for the selected date ${state.date}. Quantities below are recipe amounts before combining; adjust for household size.")
  val lines=DietFood.slots.indices.mapNotNull { recipe(it) }.flatMap { DietFood.recipes[it].ingredients }.groupingBy { it }.eachCount()
  lines.forEach { (ingredient,count)->add(c,CheckBox(a).apply { text=ingredient+if(count>1)" \u2022 $count" else "";setTextColor(ink);minimumHeight=dp(48);val key="grocery-${state.date}-$ingredient";isChecked=prefs.getBoolean(key,false);setOnCheckedChangeListener { _,v->prefs.edit().putBoolean(key,v).apply() } }) }
  button(c,"Share Shopping List") { val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply { type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,"Fit100 shopping \u2022 ${state.date}\n"+lines.entries.joinToString("\n") { "${it.key} \u2022 ${it.value}" }) };a.startActivity(android.content.Intent.createChooser(intent,"Share shopping list")) }
  button(c,"Clear Checkmarks") { val e=prefs.edit();lines.keys.forEach { e.remove("grocery-${state.date}-$it") };e.apply();refresh() }
 }
 private fun field(p:LinearLayout,label:String,key:String,default:String,numeric:Boolean=false):EditText {
  note(p,label);val input=EditText(a).apply { setText(state.drafts[key]?:value(key,default));setTextColor(ink);textSize=16f;minimumHeight=dp(48);setSingleLine(true);background=shape(Color.rgb(216,234,207));setPadding(dp(12),dp(6),dp(12),dp(6));inputType=if(numeric)InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
   addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){state.drafts[key]=s.toString()};override fun afterTextChanged(s:android.text.Editable?){} }) };add(p,input);return input
 }
 private fun choice(p:LinearLayout,label:String,key:String,default:String,options:List<String>) {
  note(p,label);val spinner=Spinner(a).apply { adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,options);setSelection(options.indexOf(state.drafts[key]?:value(key,default)).coerceAtLeast(0));onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){state.drafts[key]=options[pos]} } };add(p,spinner)
 }
 private fun customize() {
  val c=card("Select Plan Type");choice(c,"Plan type","diet-plan","Gym Day",listOf("Gym Day","Rest Day","Custom"))
  val fields=mutableListOf<Pair<String,EditText>>()
  listOf(Triple("Gym-day calories","profile-gymcal","2400"),Triple("Rest-day calories","profile-restcal","2200"),Triple("Custom calories","diet-customcal","2400"),Triple("Protein (g)","profile-protein","140"),Triple("Carbs (g)","profile-carbs","250"),Triple("Fats (g)","profile-fats","70"),Triple("Daily food budget (INR)","profile-budget","200")).forEach { (label,key,default)->fields+=key to field(c,label,key,default,true) }
  choice(c,"Food preference","profile-food","Non-vegetarian",listOf("Vegetarian","Non-vegetarian","Eggetarian","Vegan"));choice(c,"Dairy","profile-dairy","Dairy-free",listOf("Include","Dairy-free"))
  fields+="profile-allergies" to field(c,"Allergies / exclusions (comma separated)","profile-allergies","Dairy")
  fields+="profile-preferred" to field(c,"Preferred foods (for your reference)","profile-preferred","Eggs, chicken, rice, dal, soy")
  val times=card("Meal Timings \u2022 Six Meal Slots")
  DietFood.slots.forEachIndexed { i,label->val key="diet-time-$i";button(times,"$label  ${state.drafts[key]?:value(key,DietFood.times[i])}") { val t=(state.drafts[key]?:value(key,DietFood.times[i])).split(':');TimePickerDialog(a,{_,h,m->state.drafts[key]="%02d:%02d".format(h,m);refresh()},t[0].toInt(),t[1].toInt(),true).show() } }
  note(times,"Targets do not rescale recipe portions. Preferred foods are saved as notes; exclusions filter recipe ingredients. Verify food labels for allergens.")
  button(page,"Save Meal Plan") {
   var invalid=false;fields.take(7).forEach { (_,input)->val v=input.text.toString().toDoubleOrNull();if(v==null || !v.isFinite() || v<=0 || v>100000){input.error="Enter a positive value up to 100000";invalid=true} };if(invalid)return@button
   val e=prefs.edit();fields.forEach { (key,input)->e.putString(key,input.text.toString().trim()) };state.drafts.forEach { (k,v)->e.putString(k,v) };e.apply();state.drafts.clear();(a.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(page.windowToken,0);navigate("Meal Plan")
  }
 }
 private fun settings() {
  val c=card("Diet Settings");note(c,"Templates use your saved food preferences and exclusions. Meals can be replaced individually.");button(c,"Edit Targets, Preferences & Timings") { navigate("Customize Meal Plan") }
  button(c,"View Nutrition History") { navigate("Diet Analytics") }
  button(c,"Reset Meal Replacements for ${state.date}") { AlertDialog.Builder(a).setTitle("Reset replacements?").setMessage("This resets selected recipes for this date. Eaten flags for those slots will also be cleared.").setNegativeButton("Cancel",null).setPositiveButton("Reset") { _,_->val e=prefs.edit();DietFood.slots.indices.forEach { e.remove("diet-swap-${state.date}-$it");e.remove("${state.date}-diet-eaten-$it") };e.apply();refresh() }.show() }
 }
 private fun analytics() {
  val c=card("Daily Nutrition \u2022 ${state.date}");summary(c);note(c,"Manual check-in: ${value("${state.date}-cal","0")} kcal \u2022 ${value("${state.date}-protein","0")} g protein")
  tabs(listOf("Calories","Protein","Macros"),state.metric) { state.metric=it;refresh() }
  if(state.metric=="Macros") { val macros=card("Estimated Macros from Eaten Meals");val meals=DietFood.slots.indices.filter { eaten(it) }.mapNotNull { recipe(it) }.map { DietFood.recipes[it] };note(macros,"Protein ${meals.sumOf { it.protein }} g\nCarbs ${meals.sumOf { it.carbs }} g\nFats ${meals.sumOf { it.fat }} g");return }
  val points=ProgressRecords.series(prefs.all,if(state.metric=="Protein") "-protein" else "-cal",LocalDate.now(),true).filter { it.date>=LocalDate.now().minusDays(6) }
  val chart=card("Last Seven Days - Logged ${state.metric}")
  if(points.isEmpty())note(chart,"No calorie check-ins yet. Log daily totals in Today's check-in.") else { chart.addView(ProgressChart(a,points,if(state.metric=="Protein") "g" else "kcal",lime),LinearLayout.LayoutParams(-1,dp(220)));note(chart,"Average on recorded days: ${points.map { it.value }.average().toInt()} ${if(state.metric=="Protein") "g" else "kcal"}. Missing days are not counted as zero.") }
 }
}

class DietPlate(context:android.content.Context,private val recipe:Int):android.view.View(context) {
 private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
 override fun onDraw(c:android.graphics.Canvas) {
  super.onDraw(c);val save=c.save();c.scale(width/240f,height/180f)
  p.color=Color.rgb(24,54,30);c.drawRoundRect(0f,0f,240f,180f,18f,18f,p)
  p.color=Color.rgb(198,216,183);c.drawOval(26f,12f,214f,170f,p)
  p.color=Color.rgb(249,242,209);c.drawOval(38f,22f,202f,158f,p)
  repeat(32) { i->val x=60f+(i*29%116);val y=44f+(i*17%82);p.color=if(recipe==1)Color.rgb(240,150,42) else Color.rgb(216,177,100);c.drawOval(x,y,x+9,y+5,p) }
  repeat(8) { i->p.color=Color.rgb(73,139,46);val x=65f+(i*31%100);val y=35f+(i*23%96);c.drawCircle(x,y,9f,p);p.color=Color.rgb(225,91,38);c.drawCircle(x+9,y+12,6f,p) }
  if(recipe==0) { p.color=Color.WHITE;c.drawOval(135f,42f,184f,109f,p);p.color=Color.rgb(255,185,18);c.drawCircle(160f,76f,14f,p) }
  c.restoreToCount(save)
 }
}

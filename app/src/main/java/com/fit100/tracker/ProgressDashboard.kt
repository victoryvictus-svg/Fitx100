package com.fit100.tracker

import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProgressUiState {
    var screen = "Overview"
    var range = "Month"
    var measurementTab = "Current"
    var historyTab = "Trends"
    var compositionTab = "Overview"
    var analysisMetric = "Weight"
    var segmentMode = "Muscle"
    var angle = "Front"
    var date = LocalDate.now()
    var compareFirst: LocalDate? = null
    var compareLast: LocalDate? = null
    var editorReturn = "Overview"
    var historyLimit = 20
    val drafts = mutableMapOf<String, String>()
    fun save() = Bundle().apply {
        listOf("screen" to screen, "range" to range, "measurementTab" to measurementTab, "historyTab" to historyTab, "compositionTab" to compositionTab,
            "analysisMetric" to analysisMetric, "segmentMode" to segmentMode, "angle" to angle, "date" to date.toString(), "first" to compareFirst?.toString(), "last" to compareLast?.toString(), "editorReturn" to editorReturn).forEach { (k,v) -> putString(k,v) }
        putInt("historyLimit", historyLimit)
        putBundle("drafts", Bundle().apply { drafts.forEach { (k,v) -> putString(k,v) } })
    }
    fun restore(bundle: Bundle?) {
        if (bundle == null) return
        screen = bundle.getString("screen") ?: screen; range = bundle.getString("range") ?: range
        measurementTab = bundle.getString("measurementTab") ?: measurementTab; historyTab = bundle.getString("historyTab") ?: historyTab
        compositionTab = bundle.getString("compositionTab") ?: compositionTab; analysisMetric = bundle.getString("analysisMetric") ?: analysisMetric
        segmentMode = bundle.getString("segmentMode") ?: segmentMode; angle = bundle.getString("angle") ?: angle
        date = bundle.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date
        compareFirst = bundle.getString("first")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        compareLast = bundle.getString("last")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        editorReturn = bundle.getString("editorReturn") ?: editorReturn; historyLimit = bundle.getInt("historyLimit", 20)
        bundle.getBundle("drafts")?.let { b -> b.keySet().forEach { drafts[it] = b.getString(it) ?: "" } }
    }
}

class ProgressDashboard(
    private val activity: Activity, private val prefs: SharedPreferences, private val page: LinearLayout, private val state: ProgressUiState,
    private val navigate: (String) -> Unit, private val refresh: () -> Unit,
    private val choosePhoto: (String) -> Unit, private val openPhoto: (Uri) -> Unit,
    private val loadImage: (ImageView, String) -> Unit, private val catalogue: () -> Unit, private val export: () -> Unit
) {
    private val lime = Color.rgb(159, 250, 77)
    private val ink = Color.rgb(5, 22, 12)
    private val muted = Color.rgb(182, 205, 174)
    private val panelColor = Color.rgb(10, 31, 17)
    private val compact get() = activity.resources.configuration.screenWidthDp < 380 || activity.resources.configuration.fontScale > 1.15f
    private val measurements = listOf("Chest" to "chest", "Shoulders" to "shoulders", "Waist" to "waist", "Hips" to "hips", "Arms" to "upper-arm", "Thigh" to "thigh")
    private val composition = listOf(
        Triple("Body fat", "fat-percent", "%"), Triple("Skeletal muscle mass", "skeletal-muscle", "kg"), Triple("Body fat mass", "fat-mass", "kg"),
        Triple("Total body water", "body-water", "L"), Triple("Protein mass", "protein-mass", "kg"), Triple("Mineral mass", "mineral-mass", "kg"), Triple("Device composition score", "score", "/100")
    )
    private val segments = listOf("Left arm" to "left-arm", "Right arm" to "right-arm", "Trunk" to "trunk", "Left leg" to "left-leg", "Right leg" to "right-leg")
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
    private fun shape(color: Int, border: Boolean = false) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat(); if (border) setStroke(dp(1), Color.rgb(42, 73, 39)) }
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(activity).apply { this.text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD) }
    private fun add(parent: LinearLayout, view: View, top: Int = 0) { parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }) }
    private fun card(parent: LinearLayout = page) = column().apply { background = shape(panelColor, true); setPadding(dp(14), dp(14), dp(14), dp(14)); add(parent, this, 12) }
    private fun button(label: String, action: () -> Unit) = text(label, 14f, ink, true).apply {
        gravity = Gravity.CENTER; background = shape(lime); minimumHeight = dp(48); setPadding(dp(12),dp(10),dp(12),dp(10)); isFocusable = true; setOnClickListener { action() }
    }
    private fun note(parent: LinearLayout, value: String) = add(parent, text(value, 12f, muted), 8)
    private fun heading(parent: LinearLayout, title: String, detail: String? = null) { add(parent, text(title, 20f, Color.WHITE, true), 8); if (detail != null) note(parent, detail) }
    private fun fmt(value: Double?, unit: String = "") = if (value == null) "--" else "${"%.1f".format(value)}${if (unit.isBlank()) "" else " $unit"}"
    private fun stored(key: String): Double? = prefs.getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    private fun series(suffix: String, maximum: Double = Double.MAX_VALUE) = ProgressRecords.series(prefs.all, suffix, allowZero = suffix != "-weight" && !suffix.startsWith("-measurement-"), maximum = maximum)
    private fun weights() = series("-weight")
    private fun compositionSeries(key: String) = series("-composition-$key", if (key == "score" || key == "fat-percent") 100.0 else Double.MAX_VALUE)
    private fun currentWeight() = weights().lastOrNull()?.value ?: stored("profile-currentweight")
    private fun difference(points: List<ProgressPoint>): String = if (points.size < 2) "Add another record to see change" else "%+.1f since first record".format(points.last().value - points.first().value)
    private fun tabs(parent: LinearLayout, labels: List<String>, selected: String, action: (String) -> Unit) {
        val rootTabs=labels==listOf("Overview","Measurements","Photos","Analysis","History") && !compact
        val strip = row().apply { gravity=if(rootTabs) Gravity.CENTER else Gravity.CENTER_VERTICAL; background = shape(Color.rgb(16, 40, 23)); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        labels.forEach { label -> strip.addView(text(label, if(rootTabs) 11f else 12f, if (label == selected) ink else muted, label == selected).apply {
            background = shape(if (label == selected) lime else Color.TRANSPARENT); gravity = Gravity.CENTER; minimumHeight = dp(44); minimumWidth = if(rootTabs) 0 else dp(82)
            setPadding(dp(if(rootTabs) 8 else 12), dp(10), dp(if(rootTabs) 8 else 12), dp(10)); isFocusable = true; isSelected = label == selected; setOnClickListener { action(label) }
        }, LinearLayout.LayoutParams(-2, -2)) }
        val scroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; isFillViewport=true; addView(strip) }
        add(parent, scroll, 10)
        if (!rootTabs && labels.indexOf(selected) > 2) scroll.post { scroll.smoothScrollTo(dp(82 * (labels.indexOf(selected) - 1)), 0) }
    }
    private fun empty(parent: LinearLayout, title: String, detail: String) { add(parent, text(title, 16f, Color.WHITE, true), 10); note(parent, detail) }
    private fun metric(parent: LinearLayout, name: String, value: String, detail: String = "") {
        add(parent, text(name, 12f, muted)); add(parent, text(value, 22f, Color.WHITE, true), 7); if (detail.isNotBlank()) note(parent, detail)
    }
    private fun grid(parent: LinearLayout, items: List<Triple<String,String,String>>) {
        items.chunked(2).forEach { group -> val line = row().apply { gravity = Gravity.TOP }
            group.forEach { (title,value,detail) -> val cell = column().apply { background = shape(panelColor, true); setPadding(dp(12), dp(12), dp(12), dp(12)) }; metric(cell,title,value,detail); line.addView(cell, LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(3);marginEnd=dp(3) }) }
            if (group.size == 1) line.addView(View(activity),LinearLayout.LayoutParams(0,0,1f))
            add(parent,line,8)
        }
    }
    private fun chart(parent: LinearLayout, points: List<ProgressPoint>, unit: String, color: Int = lime) {
        if (points.isEmpty()) { empty(parent,"No records in this range","Add a dated entry or choose All Time to see earlier records."); return }
        parent.addView(ProgressChart(activity, points, unit, color), LinearLayout.LayoutParams(-1,dp(if(compact) 235 else 200)).apply { topMargin=dp(12) })
        note(parent, if(points.size == 1) "One recorded point. Add another to see a trend." else "Tap a point to inspect its value and date.")
    }
    private fun datePicker(selected: LocalDate, action: (LocalDate) -> Unit) {
        DatePickerDialog(activity,{_,y,m,d -> action(LocalDate.of(y,m+1,d))},selected.year,selected.monthValue-1,selected.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show()
    }
    private fun dateButton(parent: LinearLayout) { add(parent,button(state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))+"  - Choose Date") { datePicker(state.date) { state.date=it; refresh() } },12) }
    private fun edit(screen: String) { state.editorReturn=state.screen; state.date=LocalDate.now(); navigate(screen) }
    fun render() {
        val root = when(state.screen) { "Weight Progress","Edit Weight" -> "Overview"; "Body Composition","Muscle-Fat Analysis","Body Ratios","Segmental Analysis","Edit Report","Edit Segments" -> "Analysis"; "Edit Measurements" -> "Measurements"; else -> state.screen }
        tabs(page,listOf("Overview","Measurements","Photos","Analysis","History"),root) { navigate(it) }
        when(state.screen) {
            "Measurements" -> measurementsPage()
            "Photos" -> photosPage()
            "Analysis" -> analysisMenu()
            "History" -> historyPage()
            "Weight Progress" -> weightPage()
            "Body Composition" -> compositionPage()
            "Muscle-Fat Analysis" -> musclePage()
            "Body Ratios" -> ratiosPage()
            "Segmental Analysis" -> segmentPage()
            "Edit Weight","Edit Measurements","Edit Report","Edit Segments" -> editor()
            else -> overview()
        }
    }
    private fun overview() {
        val journey=card();heading(journey,"Your Progress")
        val start=if(prefs.contains("challenge-start-ms"))prefs.getLong("challenge-start-ms",0) else null
        val percent=ChallengeClock.percent(start)
        val line=row();line.addView(text("$percent%",22f,lime,true).apply { gravity=Gravity.CENTER;background=HomeArt("ring",percent/100f,lime) },LinearLayout.LayoutParams(dp(72),dp(72)).apply { marginEnd=dp(14) })
        val labels=column();add(labels,text("Day ${journeyDay()} of 100",18f,Color.WHITE,true));note(labels,if(start==null)"Start your challenge from Today" else "Started "+java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")))
        val bar=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply { max=100;progress=journeyDay();progressTintList=ColorStateList.valueOf(lime) };add(labels,bar,10)
        line.addView(labels,LinearLayout.LayoutParams(0,-2,1f));add(journey,line,12)
        val fat=compositionSeries("fat-percent").lastOrNull();val muscle=compositionSeries("skeletal-muscle").lastOrNull()
        val bmi=ProgressRecords.bmi(currentWeight(),stored("profile-height"))
        grid(page,listOf(Triple("Weight",fmt(currentWeight(),"kg"),if(weights().isEmpty()) "Saved profile value" else "Logged ${weights().last().date}"),Triple("Body fat",fmt(fat?.value,"%"),fat?.date?.toString()?:"No report entered"),Triple("Skeletal muscle",fmt(muscle?.value,"kg"),muscle?.date?.toString()?:"No report entered"),Triple("Calculated BMI",fmt(bmi),if(bmi==null) "Add weight and height" else "From weight and saved height")))
        val photos=card();heading(photos,"Your transformation","First and latest front photos")
        comparePhotos(photos,false)
        add(photos,button("View Detailed Progress") { navigate("Weight Progress") },14)
        val quick=card();heading(quick,"Keep your progress current")
        add(quick,button("Log Weight") { edit("Edit Weight") },12)
        add(quick,button("Update Measurements") { edit("Edit Measurements") },8)
        add(quick,button("Record Body Composition") { edit("Edit Report") },8)
        val spent=ProgressRecords.series(prefs.all,"-spent",allowZero=true).sumOf{it.value}
        note(quick,"Recorded food spend: INR ${"%.0f".format(spent)}")
    }
    private fun journeyDay()=ChallengeClock.day(if(prefs.contains("challenge-start-ms"))prefs.getLong("challenge-start-ms",0) else null)
    private fun weightPage() {
        tabs(page,listOf("Week","Month","All Time"),state.range) { state.range=it;refresh() }
        val points=weights()
        grid(page,listOf(Triple("Starting",fmt(stored("profile-startweight")?:points.firstOrNull()?.value,"kg"),"Saved starting weight"),Triple("Current",fmt(currentWeight(),"kg"),points.lastOrNull()?.date?.toString()?:"Profile value"),Triple("Target",fmt(stored("profile-targetweight"),"kg"),"From Fitness Goals")))
        val graph=card();heading(graph,"Weight Progress",difference(points));chart(graph,ProgressRecords.range(points,state.range),"kg")
        val log=card();heading(log,"Weight Log");add(log,button("+ Add Weight") { edit("Edit Weight") },10)
        if(points.isEmpty()) empty(log,"No weigh-ins yet","Your first saved weigh-in will appear here.")
        points.takeLast(state.historyLimit).reversed().forEach { point ->
            val position=points.indexOf(point);val delta=if(position>0) "  (%+.1f kg)".format(point.value-points[position-1].value) else ""
            add(log,text("${point.date}    ${fmt(point.value,"kg")}$delta",14f,Color.WHITE,true).apply { minimumHeight=dp(52);gravity=Gravity.CENTER_VERTICAL;isFocusable=true;setOnClickListener { state.date=point.date;state.editorReturn="Weight Progress";navigate("Edit Weight") } },4)
        }
        if(points.size>state.historyLimit) add(log,button("Show More") { state.historyLimit+=20;refresh() },10)
    }
    private fun measurementsPage() {
        tabs(page,listOf("Current","Trends","History"),state.measurementTab) { state.measurementTab=it;refresh() }
        when(state.measurementTab) {
            "Trends" -> measurements.forEach { (label,key) -> val data=series("-measurement-$key");val box=card();heading(box,"$label Trend",difference(data));chart(box,data,"cm") }
            "History" -> {
                val dates=measurements.flatMap { series("-measurement-${it.second}").map { p -> p.date } }.distinct().sortedDescending()
                if(dates.isEmpty()) empty(card(),"No measurement history","Save your first set of measurements to start a history.")
                dates.take(state.historyLimit).forEach { date -> val box=card();heading(box,date.toString());measurements.forEach { (label,key) -> stored("$date-measurement-$key")?.let { note(box,"$label: ${fmt(it,"cm")}") } };add(box,button("Edit Measurements") { state.date=date;state.editorReturn="Measurements";navigate("Edit Measurements") },10) }
                if(dates.size>state.historyLimit) add(page,button("Show More") { state.historyLimit+=20;refresh() },12)
            }
            else -> {
                dateButton(page)
                val body=card();heading(body,"Body Measurements","Measurements for ${state.date}")
                val display=row();val drawing=ImageView(activity).apply { setImageDrawable(ProgressBody());contentDescription="Body measurement diagram" }
                display.addView(drawing,LinearLayout.LayoutParams(dp(if(compact) 92 else 120),dp(240)).apply { marginEnd=dp(12) })
                val figures=column();metric(figures,"Height",fmt(stored("profile-height"),"cm"),"Saved profile height")
                measurements.take(3).forEach { (label,key) -> add(figures,text("$label  ${fmt(stored("${state.date}-measurement-$key"),"cm")}",14f,muted),12) }
                display.addView(figures,LinearLayout.LayoutParams(0,-2,1f));add(body,display,12)
                measurements.forEach { (label,key) ->
                    val points=series("-measurement-$key");val value=stored("${state.date}-measurement-$key")
                    val previous=points.lastOrNull { it.date<state.date }?.value
                    val line=row().apply { minimumHeight=dp(50) }
                    line.addView(text(label,14f,muted),LinearLayout.LayoutParams(0,-2,1f))
                    line.addView(text(fmt(value,"cm"),15f,Color.WHITE,true))
                    add(body,line)
                    if(value!=null && previous!=null) note(body,"%+.1f cm since previous entry".format(value-previous))
                }
                add(page,button("Update Measurements") { state.editorReturn="Measurements";navigate("Edit Measurements") },16)
            }
        }
    }
    private fun photoDates(angle: String): List<LocalDate> = prefs.all.keys.mapNotNull { key ->
        if(!key.startsWith("body-photo-") || !key.endsWith("-${angle.lowercase()}")) null
        else runCatching { LocalDate.parse(key.removePrefix("body-photo-").removeSuffix("-${angle.lowercase()}")) }.getOrNull()?.takeIf { it<=LocalDate.now() }
    }.distinct().sorted()
    private fun photoSlot(parent: LinearLayout, label: String, date: LocalDate, angle: String, selectable: Boolean) {
        val slot=column().apply { setPadding(dp(4),dp(6),dp(4),dp(6)) }
        add(slot,text(label,14f,Color.WHITE,true))
        val frame=FrameLayout(activity).apply { background=shape(Color.rgb(22,48,28));clipToOutline=true }
        val key="body-photo-$date-${angle.lowercase()}";val uri=prefs.getString(key,null)
        val hidden=prefs.getBoolean("hide-progress-photos",false)
        frame.addView(text(if(uri==null) "+\nAdd Photo" else if(hidden) "Preview hidden\nTap to view" else "Loading...",13f,muted).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
        if(uri!=null && !hidden) {
            val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="$angle photo for $date" }
            frame.addView(image,FrameLayout.LayoutParams(-1,-1));loadImage(image,uri)
        }
        frame.isFocusable=true;frame.contentDescription="$label $angle photo for $date. ${if(uri==null) "Add photo" else "View photo"}"
        frame.setOnClickListener { if(uri==null) choosePhoto(key) else openPhoto(Uri.parse(uri)) }
        slot.addView(frame,LinearLayout.LayoutParams(-1,dp(if(compact) 190 else 210)).apply { topMargin=dp(8) })
        if(selectable) add(slot,button(date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) {
            datePicker(date) { if(label=="First") state.compareFirst=it else state.compareLast=it;refresh() }
        },8) else note(slot,date.toString())
        parent.addView(slot,LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun comparePhotos(parent: LinearLayout, selectable: Boolean) {
        val angle=if(selectable) state.angle else "Front"
        val dates=photoDates(angle)
        val selectedFirst=if(selectable) state.compareFirst?:dates.firstOrNull()?:LocalDate.now() else dates.firstOrNull()?:LocalDate.now()
        val selectedLast=if(selectable) state.compareLast?:dates.lastOrNull()?:LocalDate.now() else dates.lastOrNull()?:LocalDate.now()
        val first=minOf(selectedFirst,selectedLast);val last=maxOf(selectedFirst,selectedLast)
        val pair=row().apply { gravity=Gravity.TOP };photoSlot(pair,"First",first,angle,selectable);photoSlot(pair,"Latest",last,angle,selectable);add(parent,pair,10)
        if(dates.size==1) note(parent,"One photo date recorded. Add a later photo for comparison.")
        if(dates.isEmpty()) note(parent,"No $angle body photos yet. Add your first photo or browse daily workout photos.")
        if(!selectable) add(parent,button("Open Photo Comparisons") { navigate("Photos") },10)
    }
    private fun photosPage() {
        tabs(page,listOf("Front","Side","Back"),state.angle) { state.angle=it;state.compareFirst=null;state.compareLast=null;refresh() }
        val compare=card();heading(compare,"Photo Comparisons","Choose dates to compare the same angle");comparePhotos(compare,true)
        val upload=card();heading(upload,"Add a Body Photo");dateButton(upload)
        add(upload,button("Add / Change ${state.angle} Photo") { choosePhoto("body-photo-${state.date}-${state.angle.lowercase()}") },12)
        val daily=card();heading(daily,"Daily Before & After Workout");note(daily,"Your existing daily photos and earlier progress photos are available in the catalogue.")
        add(daily,button("Open Daily Photo Catalogue") { catalogue() },12)
    }
    private fun analysisMenu() {
        heading(page,"Body Analysis","Your measurements and device-reported records")
        listOf("Body Composition" to "Water, protein, minerals and device score", "Muscle-Fat Analysis" to "Weight, skeletal muscle and fat mass", "Body Ratios" to "Calculated BMI, waist-to-hip ratio and recorded indices", "Segmental Analysis" to "Recorded muscle and fat by body region").forEach { (name,detail) ->
            val box=card();heading(box,name);note(box,detail);add(box,button("View $name") { navigate(name) },12)
        }
        add(page,button("Record Body Composition") { edit("Edit Report") },16)
    }
    private fun compositionPage() {
        tabs(page,listOf("Overview","Muscle-Fat","Segmental","Score"),state.compositionTab) {
            when(it) { "Muscle-Fat" -> navigate("Muscle-Fat Analysis");"Segmental" -> navigate("Segmental Analysis");else -> { state.compositionTab=it;refresh() } }
        }
        val score=compositionSeries("score").lastOrNull()
        val box=card();heading(box,"Body Composition Score")
        if(score==null) empty(box,"No device score recorded","Enter a score from your body-composition report. Fit100 does not estimate this score.")
        else {
            box.addView(text("${fmt(score.value)}\n/ 100",24f,lime,true).apply { gravity=Gravity.CENTER;background=HomeArt("ring",(score.value/100).toFloat(),lime) },LinearLayout.LayoutParams(dp(180),dp(180)).apply { gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(14) })
            note(box,"Device-reported score recorded ${score.date}")
        }
        if(state.compositionTab!="Score") composition.filter { it.second!="score" }.forEach { (label,key,unit) ->
            val data=compositionSeries(key);val tile=card();metric(tile,label,fmt(data.lastOrNull()?.value,unit),data.lastOrNull()?.let { "Recorded ${it.date}" }?:"No report entered")
            if(data.size>1) { note(tile,difference(data));chart(tile,data.takeLast(12),unit) }
        }
        add(page,button("Record / Update Report") { edit("Edit Report") },16)
    }
    private fun musclePage() {
        tabs(page,listOf("Weight","Skeletal Muscle","Body Fat"),state.analysisMetric) { state.analysisMetric=it;refresh() }
        val data=when(state.analysisMetric) { "Skeletal Muscle" -> compositionSeries("skeletal-muscle");"Body Fat" -> compositionSeries("fat-mass");else -> weights() }
        val box=card();heading(box,"${state.analysisMetric} (kg)");metric(box,"Latest recorded",fmt(data.lastOrNull()?.value,"kg"),difference(data));chart(box,data,"kg",if(state.analysisMetric=="Body Fat") Color.rgb(255,145,73) else lime)
        val all=card();heading(all,"Recorded Metrics")
        grid(all,listOf(Triple("Weight",fmt(weights().lastOrNull()?.value,"kg"),"Weigh-ins"),Triple("Skeletal muscle",fmt(compositionSeries("skeletal-muscle").lastOrNull()?.value,"kg"),"Device report"),Triple("Body fat mass",fmt(compositionSeries("fat-mass").lastOrNull()?.value,"kg"),"Device report")))
        add(page,button(if(state.analysisMetric=="Weight") "Log Weight" else "Record Body Composition") { edit(if(state.analysisMetric=="Weight") "Edit Weight" else "Edit Report") },16)
    }
    private fun ratiosPage() {
        val bmi=ProgressRecords.bmi(currentWeight(),stored("profile-height"));val box=card();metric(box,"Calculated BMI",fmt(bmi),"Weight (kg) divided by height (m) squared")
        if(bmi==null) note(box,"Add weight and profile height to calculate BMI.")
        add(box,text("How BMI is calculated - CDC",12f,lime).apply { minimumHeight=dp(48);gravity=Gravity.CENTER_VERTICAL;isFocusable=true;setOnClickListener { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.cdc.gov/growth-chart-training/hcp/using-bmi/calculating-bmi.html"))) } },8)
        val waist=series("-measurement-waist");val hips=series("-measurement-hips").associateBy { it.date }
        val pair=waist.lastOrNull { hips[it.date]!=null }
        val ratio=pair?.let { it.value / hips.getValue(it.date).value }
        val whr=card();metric(whr,"Waist-to-Hip Ratio",ratio?.let { "%.2f".format(it) }?:"--",pair?.let { "From waist and hips recorded ${it.date}" }?:"Record waist and hips on the same date")
        val fat=card();val data=compositionSeries("fat-percent");metric(fat,"Recorded Body Fat",fmt(data.lastOrNull()?.value,"%"),data.lastOrNull()?.date?.toString()?:"No report entered")
        val obesity=series("-composition-obesity-index");val index=card();metric(index,"Device Obesity Index",fmt(obesity.lastOrNull()?.value,"%"),"Optional device-reported index; no category assigned")
        note(index,"Fit100 displays these values without assigning diagnostic categories.")
        add(page,button("Update Measurements") { edit("Edit Measurements") },14);add(page,button("Record Report Values") { edit("Edit Report") },8)
    }
    private fun segmentPage() {
        tabs(page,listOf("Muscle","Fat"),state.segmentMode) { state.segmentMode=it;refresh() }
        val mode=state.segmentMode.lowercase();val all=segments.flatMap { series("-segment-$mode-${it.second}") };val date=all.maxOfOrNull{it.date}
        val diagram=card();heading(diagram,"Segmental ${state.segmentMode}",date?.let { "Latest region records through $it" }?:"No regional values recorded")
        diagram.addView(ImageView(activity).apply { setImageDrawable(ProgressBody());contentDescription="Body-region diagram" },LinearLayout.LayoutParams(-1,dp(230)))
        grid(page,segments.map { (label,key) -> val latest=series("-segment-$mode-$key").lastOrNull();Triple(label,fmt(latest?.value,"kg"),latest?.date?.toString()?:"Not recorded") })
        note(diagram,"Values are entered from a device report. Regional muscle and fat are not inferred from photos.")
        add(page,button("Record ${state.segmentMode} by Region") { edit("Edit Segments") },16)
    }
    private fun historyPage() {
        tabs(page,listOf("Trends","History List","Data"),state.historyTab) { state.historyTab=it;refresh() }
        when(state.historyTab) {
            "Trends" -> {
                listOf(Triple("Weight Trend",weights(),"kg"),Triple("Body Fat Trend",compositionSeries("fat-percent"),"%"),Triple("Skeletal Muscle Trend",compositionSeries("skeletal-muscle"),"kg")).forEachIndexed { index,(title,data,unit) ->
                    val box=card();heading(box,title,difference(data));chart(box,data,unit,if(index==1) Color.rgb(255,133,67) else lime)
                }
            }
            else -> {
                val values=prefs.all.filter { (key,value) ->
                    val date=runCatching { LocalDate.parse(key.take(10)) }.getOrNull()
                    date!=null && date<=LocalDate.now() && (key.endsWith("-weight") || key.contains("-measurement-") || key.contains("-composition-") || key.contains("-segment-")) && value.toString().toDoubleOrNull()?.isFinite()==true
                }
                val box=card();heading(box,if(state.historyTab=="Data") "Recorded Data" else "History & Trends","${values.size} saved values")
                if(values.isEmpty()) empty(box,"Your history starts here","Add weight, measurements or composition values to build your record.")
                values.keys.map { it.take(10) }.distinct().sortedDescending().take(state.historyLimit).forEach { date ->
                    val day=card();heading(day,date)
                    values.filterKeys { it.startsWith(date) }.toSortedMap().forEach { (key,value) ->
                        val name=key.drop(11).replace("measurement-","").replace("composition-","").replace("segment-","").replace("-"," ")
                        note(day,"${name.replaceFirstChar { it.uppercase() }}: $value")
                    }
                }
                if(values.keys.map {it.take(10)}.distinct().size>state.historyLimit) add(page,button("Show More Dates") { state.historyLimit+=20;refresh() },12)
                note(page,"Units: weights and mass in kg; measurements in cm; body water in litres; body fat and device indices in percent; device score out of 100.")
                add(page,button("Export My Data (CSV)") { export() },16)
            }
        }
    }
    private fun editor() {
        dateButton(page)
        val box=card();heading(box,when(state.screen) { "Edit Weight" -> "Log Weight";"Edit Measurements" -> "Update Body Measurements";"Edit Segments" -> "Record Regional ${state.segmentMode}";else -> "Body Composition Report" })
        if(state.screen=="Edit Report" || state.screen=="Edit Segments") note(box,"Enter values from your device report. Leave fields blank when unavailable.")
        val fields: List<Triple<String,String,String>> = when(state.screen) {
            "Edit Weight" -> listOf(Triple("Weight","weight","kg"))
            "Edit Measurements" -> measurements.map { (label,key) -> Triple(label,"measurement-$key","cm") }
            "Edit Segments" -> segments.map { (label,key) -> Triple(label,"segment-${state.segmentMode.lowercase()}-$key","kg") }
            else -> composition.map { (label,key,unit) -> Triple(label,"composition-$key",unit) } + Triple("Device obesity index (optional)","composition-obesity-index","%")
        }
        val inputs=fields.map { (label,suffix,unit) ->
            val key="${state.date}-$suffix"
            state.drafts.putIfAbsent(key,prefs.getString(key,"")?:"")
            val cell=if(compact) column() else row()
            cell.background=shape(Color.rgb(20,46,27),true);cell.setPadding(dp(10),dp(6),dp(10),dp(6))
            val input=EditText(activity).apply {
                id=View.generateViewId();textSize=15f;setTextColor(Color.WHITE);setHintTextColor(muted);hint="No value";minimumHeight=dp(48)
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                backgroundTintList=ColorStateList.valueOf(lime);setText(state.drafts[key]);contentDescription="$label ($unit)"
                addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { state.drafts[key]=s.toString() }
                    override fun afterTextChanged(s:Editable?) {}
                })
            }
            val title=text("$label ($unit)",13f,muted,true).apply { labelFor=input.id }
            if(compact) { add(cell,title);add(cell,input) } else { cell.addView(title,LinearLayout.LayoutParams(0,-2,1f));cell.addView(input,LinearLayout.LayoutParams(0,-2,1f)) }
            add(box,cell,9);key to input
        }
        add(page,button("Save ${if(state.screen=="Edit Weight") "Weight" else "Measurements"}") {
            var invalid=false
            inputs.forEach { (key,input) ->
                val raw=input.text.toString().trim();val value=raw.toDoubleOrNull()
                val positive=key.endsWith("-weight") || key.contains("-measurement-")
                val percentage=key.endsWith("-composition-fat-percent") || key.endsWith("-composition-score")
                val required=state.screen=="Edit Weight"
                if((required && raw.isBlank()) || (raw.isNotBlank() && (value==null || !value.isFinite() || value<0 || (positive && value==0.0) || (percentage && value>100)))) {
                    input.error=if(percentage) "Enter 0 to 100" else "Enter a valid ${if(positive) "positive " else ""}number";invalid=true
                }
            }
            if(invalid) return@button
            val edit=prefs.edit();inputs.forEach { (key,input) -> val value=input.text.toString().trim();if(value.isEmpty()) edit.remove(key) else edit.putString(key,value) };edit.apply()
            if(state.screen=="Edit Weight") weights().lastOrNull()?.let { latest -> prefs.edit().putString("profile-currentweight",latest.value.toString()).apply() }
            inputs.forEach { state.drafts.remove(it.first) }
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(page.windowToken,0)
            Toast.makeText(activity,if(state.screen=="Edit Weight") "Weight saved" else "Measurements saved",Toast.LENGTH_SHORT).show();navigate(state.editorReturn)
        },16)
        note(page,"Only saved entries appear in charts and history.")
    }
}

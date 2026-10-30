package com.fit100.tracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.util.Size
import java.util.concurrent.Executors
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.provider.MediaStore
import android.text.TextWatcher
import android.text.Editable
import android.view.*
import android.widget.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Calendar

private const val CHANNEL = "fit100_reminders"
private val GREEN = Color.rgb(170, 245, 117)
private val INK = Color.rgb(17, 29, 20)
private val MUTED = Color.rgb(68, 94, 67)
private val BG = Color.rgb(216, 234, 207)
private val SURFACE = Color.rgb(235, 245, 226)
private val reminders = listOf("Breakfast" to 8, "Water break" to 11, "Lunch" to 13, "Workout / walk" to 17, "Dinner" to 20, "Daily check-in" to 21)

fun scheduleReminders(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    reminders.forEachIndexed { index, item ->
        val preferences = context.getSharedPreferences("fit100", Context.MODE_PRIVATE)
        val hour = preferences.getInt("alarm-$index", item.second)
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", index)
        val pending = PendingIntent.getBroadcast(context, 100 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (!preferences.getBoolean("alarms", false) || !preferences.getBoolean("reminder-enabled-$index", true)) { am.cancel(pending); return@forEachIndexed }
        val at = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, preferences.getInt("alarm-minute-$index", 0)); set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, pending)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, pending)
    }
}
class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val prefs = context.getSharedPreferences("fit100", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("alarms", false) || !prefs.getBoolean("reminder-enabled-$id", true)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Fit100 reminders", NotificationManager.IMPORTANCE_HIGH))
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Fit100 • ${reminders.getOrNull(id)?.first ?: "Check-in"}")
            .setContentText("Small steps, big changes. Keep your routine going.").setAutoCancel(true).build()
        manager.notify(100 + id, notification)
        scheduleReminders(context)
    }
}
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && context.getSharedPreferences("fit100", Context.MODE_PRIVATE).getBoolean("alarms", false)) scheduleReminders(context)
    }
}

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("fit100", MODE_PRIVATE) }
    private lateinit var page: LinearLayout
    private lateinit var host: LinearLayout
    private var tab = 0
    private val progressState = ProgressUiState()
    private val dietState = DietState()
    private fun openDiet(screen:String) { pauseTimer();dietState.screen=screen;tab=1;pageScroll?.scrollTo(0,0);render() }
    private fun openProgress(screen: String) {
        pauseTimer(); progressState.screen=screen; tab=2
        pageScroll?.scrollTo(0,0);render()
    }
    private var profileScreen = "Profile"
    private var photoJournalTab = "Photos"
    private val profileDrafts = mutableMapOf<String, String>()
    private var pendingCameraUri: String? = null
    private val challengeHandler=Handler(Looper.getMainLooper())
    private val challengeTick=object:Runnable { override fun run() { if(tab==0 || tab==2 || tab==3)render();challengeHandler.postDelayed(this,60000) } }
    private fun challengeStart():Long? = if(prefs.contains("challenge-start-ms"))prefs.getLong("challenge-start-ms",0).takeIf { it>0 } else null
    private fun challengeDate()=ChallengeClock.date(challengeStart())
    private fun challengeLabel()=challengeStart()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z")) }?:"Not started"
    private fun startChallenge() { if(challengeStart()!=null)return;val now=System.currentTimeMillis();prefs.edit().putLong("challenge-start-ms",now).putString("start",LocalDate.now().toString()).apply();render() }
    private val lime = Color.rgb(159, 250, 77)
    private val forest = Color.rgb(5, 22, 12)
    private fun openProfile(screen: String) {
        profileScreen = screen
        pageScroll?.scrollTo(0, 0)
        tab = 3
        render()
    }
    private lateinit var safeRoot: FrameLayout
    private val backNavigation = BackNavigation()
    private var exitHint: Toast? = null

    private fun handleBack() {
        if(tab==5 && prefs.getString("workout-screen","Workout")!="Workout") { prefs.edit().putString("workout-screen","Workout").apply();render();return }
        if(tab==1 && dietState.screen!="Meal Plan") { openDiet("Meal Plan");return }
        val isProfileDetail = (tab == 3 && profileScreen != "Profile") || (tab == 4 && catalogueOrigin == 3)
        val isProgressDetail = (tab == 2 && progressState.screen != "Overview") || (tab == 4 && catalogueOrigin == 2)
        when (backNavigation.onBack(tab == 0, SystemClock.elapsedRealtime(), isProfileDetail, isProgressDetail)) {
            BackAction.PROGRESS -> {
                exitHint?.cancel();openProgress(if(tab==4) "Photos" else "Overview")
            }
            BackAction.PROFILE -> {
                exitHint?.cancel()
                openProfile("Profile")
            }
            BackAction.HOME -> {
                exitHint?.cancel()
                tab = 0
                pauseTimer()
                render()
                pageScroll?.post { pageScroll?.smoothScrollTo(0, 0) }
            }
            BackAction.PROMPT_EXIT -> {
                exitHint?.cancel()
                exitHint = Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).also { it.show() }
            }
            BackAction.EXIT -> { exitHint?.cancel(); finish() }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSafeWindow() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = INK
        window.navigationBarColor = INK
        safeRoot = FrameLayout(this).apply { setBackgroundColor(INK) }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        // Listen on the content container before any descendant can consume the insets.
        setContentView(safeRoot)
        val content = findViewById<ViewGroup>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { _, insets ->
            applySafeInsets(insets)
            insets
        }
        safeRoot.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { view.requestApplyInsets() }
            override fun onViewDetachedFromWindow(view: View) {}
        })
        safeRoot.post {
            safeRoot.rootWindowInsets?.let { applySafeInsets(it) }
            content.requestApplyInsets()
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applySafeInsets(insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            safeRoot.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
        } else {
            val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
            safeRoot.setPadding(maxOf(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
                maxOf(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
                maxOf(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
                maxOf(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0))
        }
    }
    private var renderedTab = -1
    private var pageScroll: ScrollView? = null
    private val expandedSections = mutableSetOf("Today's check-in", "Progress photos")
    private val compact get() = resources.configuration.screenWidthDp < 380 || resources.configuration.fontScale > 1.15f
    private fun section(title: String, subtitle: String): LinearLayout {
        val card = panel(page, top = 12, padding = 14)
        val content = column()
        val header = row().apply { minimumHeight = dp(56); isFocusable = true }
        val labels = column()
        add(labels, text(title, 17f, INK, true))
        add(labels, text(subtitle, 12f, MUTED), 4)
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val indicator = text("", 22f, MUTED).apply { setPadding(dp(12), 0, 0, 0) }
        header.addView(indicator)
        fun update() {
            val expanded = title in expandedSections
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            indicator.text = if (expanded) "-" else "+"
            header.contentDescription = "$title, ${if (expanded) "expanded" else "collapsed"}. Tap to ${if (expanded) "collapse" else "expand"}."
        }
        header.setOnClickListener {
            if (!expandedSections.add(title)) expandedSections.remove(title)
            update()
        }
        add(card, header)
        add(card, content, 6)
        update()
        return content
    }
    private var timer: CountDownTimer? = null
    private var timerRemaining = 60L
    private var timerRunning = false
    private var timerInitialized = false
    private var timerEndElapsed = 0L
    private var timerSelection = 60L
    private var timerPlayButton: TextView? = null
    private val timerPresetButtons = mutableMapOf<Long, TextView>()
    private var homeTasksValue: TextView? = null
    private var homeTasksBar: ProgressBar? = null
    private var homeTasksRing: ImageView? = null
    private val homeWide get() = resources.configuration.screenWidthDp >= 840 && resources.configuration.fontScale <= 1.15f
    private fun waterGoal() = prefs.getString("profile-watergoal", "4000")?.toDoubleOrNull()?.toInt()?.coerceIn(250, 10000) ?: 4000
    private fun waterToday() = Hydration.millilitres(prefs.getString("${LocalDate.now()}-water", "0"))
    private fun homeProgress() { openProgress("Overview") }
    private fun pauseTimer() {
        if (timerRunning) timerRemaining = ((timerEndElapsed - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
        timer?.cancel(); timerRunning = false; updateTimerViews()
    }
    private fun updateTimerViews() {
        timerText?.text = "%02d:%02d".format(timerRemaining / 60, timerRemaining % 60)
        timerPlayButton?.text = if (timerRunning) "Pause" else if (timerRemaining == 0L) "Restart" else "Start"
        timerPresetButtons.forEach { (seconds, button) ->
            button.background = rounded(if (timerSelection == seconds) lime else Color.rgb(24, 49, 32), 12)
            button.setTextColor(if (timerSelection == seconds) INK else Color.WHITE)
            button.isSelected = timerSelection == seconds
        }
    }
    private var timerText: TextView? = null
    private var photoDay = 1
    private var pendingPhotoKey: String? = null
    private var catalogueOrigin = 0
    private var catalogueDate = LocalDate.now()
    private var catalogueLimit = 7
    private val photoLoader = Executors.newSingleThreadExecutor()
    private fun openCatalogue() {
        catalogueOrigin = tab
        catalogueDate = LocalDate.now()
        catalogueLimit = 7
        tab = 4
        pauseTimer()
        render()
    }
    @Deprecated("Legacy back navigation")
    override fun onBackPressed() {
        handleBack()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(s: String, size: Float = 15f, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        this.text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
    }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun adaptiveHeader(): LinearLayout = if (compact) column() else row()
    private fun headerLabelParams() = if (compact) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f)
    private fun add(parent: LinearLayout, view: View, top: Int = 0) { parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }) }
    private fun panel(parent: LinearLayout, color: Int = SURFACE, top: Int = 12, padding: Int = 16): LinearLayout {
        val box = column().apply { background = rounded(color); setPadding(dp(padding), dp(padding), dp(padding), dp(padding)) }
        add(parent, box, top); return box
    }
    private fun pill(label: String, action: () -> Unit, dark: Boolean = false): TextView = text(label, 14f, if (dark) Color.WHITE else INK, true).apply {
        minimumHeight = dp(48); isFocusable = true; gravity = Gravity.CENTER; background = rounded(if (dark) INK else GREEN, 15); setPadding(dp(16), dp(13), dp(16), dp(13)); setOnClickListener { action() }
    }
    private fun heading(parent: LinearLayout, title: String, sub: String? = null) {
        add(parent, text(title, 23f, INK, true), 19)
        if (sub != null) add(parent, text(sub, 13f, MUTED), 4)
    }
    private fun day(): Int = ChallengeClock.day(challengeStart())
    private fun workout(): String = if (isRest()) "Recovery & mobility" else when (LocalDate.now().dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> "Upper body strength"
        DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> "Lower body strength"
        DayOfWeek.SATURDAY -> "Full body strength"
        else -> "Full body strength"
    }
    private fun isRest(): Boolean {
        val days = (prefs.getString("profile-rest", "Wednesday, Sunday") ?: "Wednesday, Sunday").split(",").map { it.trim().uppercase() }
        return LocalDate.now().dayOfWeek.name in days
    }
    private fun calorieTarget() = (prefs.getString(if (isRest()) "profile-restcal" else "profile-gymcal", if (isRest()) "2200" else "2400")?.toDoubleOrNull() ?: 2400.0).toInt()
    private fun proteinTarget() = (prefs.getString("profile-protein", "140")?.toDoubleOrNull() ?: 140.0).toInt()
    private fun budgetTarget() = (prefs.getString("profile-budget", "200")?.toDoubleOrNull() ?: 200.0).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSafeWindow()
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
        progressState.restore(savedInstanceState?.getBundle("progressState"))
        dietState.restore(savedInstanceState?.getBundle("dietState"))
        profileScreen = savedInstanceState?.getString("profileScreen") ?: "Profile"
        photoJournalTab = savedInstanceState?.getString("photoJournalTab") ?: "Photos"
        pendingCameraUri = savedInstanceState?.getString("pendingCameraUri")
        savedInstanceState?.getBundle("profileDrafts")?.let { bundle -> bundle.keySet().forEach { key -> profileDrafts[key] = bundle.getString(key) ?: "" } }
        tab = savedInstanceState?.getInt("tab", 0) ?: 0
        savedInstanceState?.getStringArrayList("sections")?.let { expandedSections.clear(); expandedSections.addAll(it) }
        photoDay = savedInstanceState?.getInt("photoDay", 1) ?: 1
        pendingPhotoKey = savedInstanceState?.getString("pendingPhotoKey")
        catalogueOrigin = savedInstanceState?.getInt("catalogueOrigin", 0) ?: 0
        catalogueDate = savedInstanceState?.getString("catalogueDate")?.let { LocalDate.parse(it) } ?: LocalDate.now()
        albumFilter=savedInstanceState?.getString("albumFilter")?:"All Photos"
        catalogueLimit = savedInstanceState?.getInt("catalogueLimit", 7) ?: 7
        if (savedInstanceState?.getBoolean("timerInitialized", false) == true) {
            timerInitialized = true
            timerRemaining = savedInstanceState.getLong("timerRemaining", 60)
            timerSelection = savedInstanceState.getLong("timerSelection", 60)
        }
        render()
        if (savedInstanceState?.getBoolean("timerRunning", false) == true && tab == 0) {
            val remaining = ((savedInstanceState.getLong("timerEndElapsed", 0) - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
            if (remaining > 0) startTimer(remaining) else { timerRemaining = 0; updateTimerViews() }
        }
        savedInstanceState?.getInt("scroll")?.let { position -> pageScroll?.post { pageScroll?.scrollTo(0, position) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("timerInitialized", timerInitialized)
        outState.putLong("timerRemaining", timerRemaining)
        outState.putLong("timerSelection", timerSelection)
        outState.putBoolean("timerRunning", timerRunning)
        outState.putLong("timerEndElapsed", timerEndElapsed)
        outState.putBundle("progressState",progressState.save())
        outState.putBundle("dietState",dietState.save())
        outState.putString("profileScreen", profileScreen)
        outState.putString("photoJournalTab", photoJournalTab)
        outState.putString("pendingCameraUri", pendingCameraUri)
        outState.putBundle("profileDrafts", Bundle().apply { profileDrafts.forEach { (key, value) -> putString(key, value) } })
        outState.putInt("tab", tab)
        outState.putInt("scroll", pageScroll?.scrollY ?: 0)
        outState.putInt("photoDay", photoDay)
        outState.putString("pendingPhotoKey", pendingPhotoKey)
        outState.putInt("catalogueOrigin", catalogueOrigin)
        outState.putString("catalogueDate", catalogueDate.toString())
        outState.putString("albumFilter",albumFilter)
        outState.putInt("catalogueLimit", catalogueLimit)
        outState.putStringArrayList("sections", ArrayList(expandedSections))
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume();challengeHandler.removeCallbacks(challengeTick);if(::safeRoot.isInitialized && ::page.isInitialized)render();challengeHandler.postDelayed(challengeTick,60000) }
    override fun onPause() {
        challengeHandler.removeCallbacks(challengeTick)
        backNavigation.reset()
        exitHint?.cancel()
        super.onPause()
    }
    override fun onDestroy() {
        timer?.cancel()
        photoLoader.shutdownNow()
        super.onDestroy()
    }
    private fun render() {
        val previousScroll = if (renderedTab == tab) pageScroll?.scrollY ?: 0 else 0
        renderedTab = tab
        host = column().apply {
            background = if (tab == 0 || tab == 1 || tab == 2 || tab == 5 || (tab == 3 && profileScreen == "Profile")) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(forest, Color.rgb(23, 58, 31)))
                else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(228, 240, 217), BG))
        }
        val top = row().apply { setPadding(dp(22), dp(17), dp(22), dp(13)); setBackgroundColor(INK) }
        if(tab==1 && dietState.screen!="Meal Plan") {
            top.addView(text("\u2039",32f,lime,true).apply { gravity=Gravity.CENTER;minimumWidth=dp(48);minimumHeight=dp(48);contentDescription="Back to Meal Plan";setOnClickListener { handleBack() } })
            top.addView(text(dietState.screen,20f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        } else if (tab == 2 && progressState.screen != "Overview") {
            top.addView(text("\u2039",32f,lime,true).apply { gravity=Gravity.CENTER;minimumWidth=dp(48);minimumHeight=dp(48);contentDescription="Back to Progress";isFocusable=true;setOnClickListener { handleBack() } })
            top.addView(text(when(progressState.screen) { "Measurements" -> "Body Measurements";"History" -> "History & Trends";"Photos" -> "Progress Photos";"Edit Weight" -> "Log Weight";"Edit Measurements" -> "Update Measurements";"Edit Report" -> "Record Composition";"Edit Segments" -> "Record Regions";else -> progressState.screen },20f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        } else if (tab == 3 && profileScreen != "Profile") {
            top.addView(text("\u2039", 32f, lime, true).apply {
                gravity = Gravity.CENTER; minimumWidth = dp(48); minimumHeight = dp(48)
                contentDescription = "Back to Profile"; isFocusable = true; setOnClickListener { handleBack() }
            })
            top.addView(text(profileScreen, 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            if ((tab == 0 || (tab == 2 && progressState.screen == "Overview")) && !compact) top.addView(ImageView(this).apply {
                setImageDrawable(FitIconDrawable("leaf", lime)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(32), dp(40)).apply { marginEnd = dp(8) })
            val brand = column()
            add(brand, text("Fit100", 28f, lime, true).apply {
                if (tab == 0 || (tab == 2 && progressState.screen == "Overview")) this.text = android.text.SpannableString("Fit100").apply {
                    setSpan(android.text.style.ForegroundColorSpan(Color.WHITE), 0, 3, 0)
                }
            })
            add(brand, text("A BETTER YOU TOMORROW", 9f, Color.LTGRAY), 2)
            top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
            if (tab == 0 || (tab == 2 && progressState.screen == "Overview")) {
                top.addView(ImageView(this).apply {
                    setImageDrawable(FitIconDrawable("bell", Color.WHITE)); background = rounded(forest, 14)
                    setPadding(dp(11), dp(11), dp(11), dp(11)); contentDescription = "Smart reminders"; isFocusable = true
                    setOnClickListener { pauseTimer(); openProfile("Smart Reminders") }
                }, LinearLayout.LayoutParams(dp(44), dp(48)).apply { marginEnd = dp(7) })
                val badge = column().apply {
                    gravity = Gravity.CENTER; background = rounded(forest, 14); minimumHeight = dp(48); setPadding(dp(8), dp(4), dp(8), dp(4))
                    isFocusable = true; setOnClickListener { if(tab==2) openProgress("Weight Progress") else homeProgress() }
                }
                add(badge, text("Day ${day().coerceIn(0, 100)} / 100", 11f, lime, true).apply { gravity = Gravity.CENTER })
                add(badge, text(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), 10f, Color.LTGRAY).apply { gravity = Gravity.CENTER }, 3)
                top.addView(badge)
            }
            if (tab == 3) top.addView(ImageView(this).apply {
                setImageDrawable(FitIconDrawable("settings", Color.WHITE))
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12)); contentDescription = "App Settings"
                isFocusable = true; setOnClickListener { openProfile("App Settings") }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        add(host, top)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        pageScroll = scroll
        page = column().apply { setPadding(dp(if (compact) 12 else 18), dp(7), dp(if (compact) 12 else 18), dp(28)) }
        val centered = FrameLayout(this)
        centered.addView(page, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(centered)
        scroll.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val width = minOf(right - left, dp(if (tab == 0 && homeWide) 960 else 640))
                if (page.layoutParams.width != width) page.layoutParams = (page.layoutParams as FrameLayout.LayoutParams).apply { this.width = width }
            }
        }
        host.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) { 0 -> today(); 1 -> meals(); 2 -> progress(); 4 -> photoCatalogue(); 5 -> WorkoutDashboard(this,prefs,page,{ render() }).render(); else -> profile() }
        val nav = row().apply { setPadding(dp(12), dp(9), dp(12), dp(12)); setBackgroundColor(INK) }
        listOf("Today", "Diet", "Sports", "Progress", "Profile").forEachIndexed { i, label ->
            val destination=listOf(0,1,5,2,3)[i]
            val selected = tab == destination || (tab == 4 && destination == catalogueOrigin)
            val button = column().apply {
                gravity = Gravity.CENTER; minimumHeight = dp(56); isFocusable = true
                setPadding(dp(3), dp(7), dp(3), dp(7))
                if (selected) background = rounded(Color.rgb(24, 58, 16), 18)
                contentDescription = label; isSelected = selected
                setOnClickListener { profileScreen = "Profile"; progressState.screen="Overview"; dietState.screen="Meal Plan"; tab = destination; pauseTimer(); render() }
            }
            button.addView(ImageView(this).apply {
                setImageDrawable(FitIconDrawable(listOf("home", "diet", "workout", "chart", "user")[i], if (selected) lime else Color.LTGRAY)); imageTintList = android.content.res.ColorStateList.valueOf(if (selected) lime else Color.LTGRAY)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            add(button, text(label, if(compact) 9f else 11f, if (selected) lime else Color.LTGRAY, selected).apply { gravity = Gravity.CENTER;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END }, 3)
            nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        if (tab != 3 || profileScreen == "Profile") add(host, nav)
        backNavigation.reset()
        exitHint?.cancel()
        safeRoot.removeAllViews()
        safeRoot.addView(host, FrameLayout.LayoutParams(-1, -1))
        safeRoot.requestApplyInsets()
        scroll.post { scroll.scrollTo(0, previousScroll) }
    }
    // Latest successfully linked progress photo, not just today's photo.
    private fun latestProgressPhoto(): Pair<Int, Uri>? {
        val start = challengeDate()
        val candidates = mutableListOf<Triple<LocalDate, Int, String>>()
        for (d in 1..100) {
            prefs.getString("photo-$d", null)?.let { candidates.add(Triple(start.plusDays(d - 1L), d, it)) }
        }
        catalogueDates().forEach { date ->
            listOf("after", "before").forEach { phase ->
                prefs.getString(workoutPhotoKey(date, phase), null)?.let {
                    candidates.add(Triple(date, (ChronoUnit.DAYS.between(start, date) + 1).toInt().coerceIn(1, 100), it))
                }
            }
        }
        for ((_, d, stored) in candidates.sortedWith(compareByDescending<Triple<LocalDate, Int, String>> { it.first }.thenBy { if (prefs.getString(workoutPhotoKey(it.first, "after"), null) == it.third) 0 else if (prefs.getString(workoutPhotoKey(it.first, "before"), null) == it.third) 1 else 2 })) {
            val uri = try { Uri.parse(stored) } catch (_: Exception) { continue }
            try {
                contentResolver.openInputStream(uri)?.use { it.read() } ?: continue
                return d to uri
            } catch (_: Exception) { /* File was moved or access was revoked. */ }
        }
        return null
    }

    private fun chooseProgressPhoto() {
        photoDay = day().coerceIn(1, 100)
        choosePhoto("photo-$photoDay")
    }

    private fun choosePhoto(key:String) { AlertDialog.Builder(this).setTitle("Add photo").setItems(arrayOf("Take photo","Choose from gallery")) { _,which->if(which==0)takePhoto(key) else chooseGallery(key) }.setNegativeButton("Cancel",null).show() }
    private fun chooseGallery(key: String) {
        pendingPhotoKey = key
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 71)
    }

    private fun catalogueShortcut(): View {
        val button = pill("Daily Photo Album", { openCatalogue() })
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_gallery, 0, 0, 0)
        button.compoundDrawablePadding = dp(10)
        return button
    }

    private fun workoutPhotoKey(date: LocalDate, phase: String) = "workout-photo-$date-$phase"

    private fun catalogueDates(): List<LocalDate> = prefs.all.keys.mapNotNull { key ->
        if (!key.startsWith("workout-photo-")) null
        else runCatching { LocalDate.parse(key.removePrefix("workout-photo-").removeSuffix("-before").removeSuffix("-after")) }.getOrNull()
    }.distinct().sortedDescending()

    private fun openPhoto(uri:Uri) {
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Full photo";minimumHeight=dp(360) }
        val box=column();add(box,text("Photo",20f,INK,true));box.addView(image,LinearLayout.LayoutParams(-1,dp(440)))
        val dialog=AlertDialog.Builder(this).setView(box).setPositiveButton("Close",null).create();dialog.show();loadProfileImage(image,uri.toString())
    }

    private fun photoSlot(date: LocalDate, phase: String): LinearLayout {
        val key = workoutPhotoKey(date, phase)
        val stored = prefs.getString(key, null)
        val box = column().apply { setPadding(dp(6), dp(8), dp(6), dp(8)) }
        val label = if (phase == "before") "Before workout" else "After workout"
        add(box, text(label, 14f, INK, true))
        val preview = FrameLayout(this).apply { background = rounded(BG, 12) }
        val status = text(if (stored == null) "No photo yet" else "Loading photo...", 12f, MUTED).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        preview.addView(status, FrameLayout.LayoutParams(-1, -1))
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "$label photo for $date. Tap to view full image."
            isFocusable = stored != null
        }
        preview.addView(image, FrameLayout.LayoutParams(-1, -1))
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(if (compact) 220 else 190)).apply { topMargin = dp(8) })
        if (stored != null) {
            val uri = Uri.parse(stored)
            image.setOnClickListener { openPhoto(uri) }
            if (prefs.getBoolean("hide-progress-photos", false)) status.text = "Preview hidden. Tap to view."
            if (!prefs.getBoolean("hide-progress-photos", false)) photoLoader.execute {
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, Size(dp(280), dp(280)), null)
                    else {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        val options = BitmapFactory.Options().apply { inSampleSize=1 }
                        while (bounds.outWidth / options.inSampleSize > dp(560) || bounds.outHeight / options.inSampleSize > dp(560)) options.inSampleSize *= 2
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    }
                }.getOrNull()
                runOnUiThread {
                    if (!isDestroyed && image.isAttachedToWindow) {
                        image.setImageBitmap(bitmap)
                        status.text = if (bitmap == null) "Photo unavailable. Choose again." else ""
                    }
                }
            }
        }
        add(box, pill(if (stored == null) "Add photo" else "Change photo", { choosePhoto(key) }), 8)
        return box
    }

    private var albumFilter="All Photos"
    private fun photoCatalogue() {
        PhotoAlbum(this,prefs,page,albumFilter,catalogueLimit,{ albumFilter=it;render() },{ catalogueLimit+=12;render() },{ choosePhoto(it) },{ image,uri->loadProfileImage(image,uri) },{ openPhoto(Uri.parse(it)) },{ handleBack() },challengeStart()).render()
    }

    private fun homeIcon(kind: String, color: Int, size: Int = 24) = ImageView(this).apply {
        setImageDrawable(FitIconDrawable(kind, color)); minimumWidth = dp(size); minimumHeight = dp(size)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun homeCard(parent: LinearLayout, dark: Boolean = false, padding: Int = 16): LinearLayout = panel(parent, if (dark) forest else SURFACE, 14, padding).apply {
        if (dark) background = rounded(forest, 22).apply { setStroke(dp(1), Color.rgb(73, 108, 57)) }
    }
    private fun homeBar(parent: LinearLayout, value: Int, target: Int, color: Int): ProgressBar {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = target.coerceAtLeast(1); progress = value.coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(color)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 71, 53))
            minimumHeight = dp(6)
        }
        parent.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(9) })
        return bar
    }
    private fun todayPhotoActions() {
        AlertDialog.Builder(this).setTitle("Today's workout photo")
            .setItems(arrayOf("Before workout", "After workout", "Open daily photo catalogue")) { _, choice ->
                when (choice) {
                    0 -> choosePhoto(workoutPhotoKey(LocalDate.now(), "before"))
                    1 -> choosePhoto(workoutPhotoKey(LocalDate.now(), "after"))
                    else -> openCatalogue()
                }
            }.setNegativeButton("Cancel", null).show()
    }
    private fun today() {
        homeTasksValue = null; homeTasksBar = null; homeTasksRing = null
        val challenge=homeCard(page,true)
        add(challenge,text("YOUR 100-DAY CHALLENGE",13f,lime,true))
        add(challenge,text(if(challengeStart()==null)"You decide when to begin. Activities can be logged before you start." else "Day ${day()} of 100 - ${ChallengeClock.percent(challengeStart())}% complete",14f,Color.WHITE),7)
        if(challengeStart()==null)add(challenge,pill("Start Challenge",{ startChallenge() }),10)
        add(challenge,pill("Track Sports & Activities",{ pauseTimer();tab=5;pageScroll?.scrollTo(0,0);render() }),8)
        homeHero()
        homeMetrics()
        homePlan()
        if (homeWide) {
            val columns = row().apply { gravity = Gravity.TOP }
            val left = column(); val right = column()
            columns.addView(left, LinearLayout.LayoutParams(0, -2, 1.7f).apply { marginEnd = dp(12) })
            columns.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
            add(page, columns)
            homeSchedule(left); homeTimer(left); homeWater(right); homeMotivation(right)
        } else {
            homeSchedule(page); homeWater(page); homeTimer(page); homeMotivation(page)
        }
    }
    private fun homeHero() {
        val hero = homeCard(page, true, 14)
        val content = if (compact) column() else row().apply { gravity = Gravity.TOP }
        val latest = latestProgressPhoto()
        val stored = if (!prefs.getBoolean("hide-progress-photos", false)) latest?.second?.toString() ?: prefs.getString("profile-photo", null) else prefs.getString("profile-photo", null)
        val photoFrame = FrameLayout(this).apply {
            background = rounded(Color.rgb(29, 59, 32), 18); clipToOutline = true
            minimumHeight = dp(215); isFocusable = true; contentDescription = "Add or view today's workout photos"
            setOnClickListener { todayPhotoActions() }
        }
        val photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        if (stored != null) {
            photoFrame.addView(photo, FrameLayout.LayoutParams(-1, -1)); loadProfileImage(photo, stored)
        } else {
            photoFrame.background = HomeArt("landscape")
            val prompt = column().apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(14), dp(10), dp(14)) }
            prompt.addView(homeIcon("camera", lime, 42), LinearLayout.LayoutParams(dp(42), dp(42)))
            add(prompt, text("Your transformation\nstarts here", 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, 12)
            photoFrame.addView(prompt, FrameLayout.LayoutParams(-1, -1))
        }
        if (compact) content.addView(photoFrame, LinearLayout.LayoutParams(-1, dp(190)))
        else content.addView(photoFrame, LinearLayout.LayoutParams(0, dp(if (homeWide) 220 else 195), .42f).apply { marginEnd = dp(14) })
        val story = column()
        add(story, text("YOUR TRANSFORMATION", 9f, lime, true), if (compact) 14 else 2)
        add(story, text("Small Steps.\nBig Changes.", if (compact) 25f else 22f, Color.WHITE, true), 8)
        add(story, text(if(challengeStart()==null) "Ready for your 100 days?" else if(ChallengeClock.percent(challengeStart())==100) "100 days completed" else "Day ${day()} of 100", 17f, lime, true), 12)
        homeBar(story, ChallengeClock.percent(challengeStart()), 100, lime)
        add(story, text("${ChallengeClock.percent(challengeStart())}% complete - ${challengeLabel()}",10f,Color.LTGRAY),5)
        if(challengeStart()==null) add(story,pill("Start My 100-Day Challenge",{ startChallenge() }),12)
        else add(story,text("Finishes "+ChallengeClock.end(challengeStart()!!).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")),10f,Color.LTGRAY),5)
        add(story, profileButton("View Progress", { homeProgress() }), 12)
        if (compact) add(content, story) else content.addView(story, LinearLayout.LayoutParams(0, -2, .58f))
        add(hero, content)
        val footer = adaptiveHeader()
        val label = column()
        add(label, text("Discipline today.", 14f, Color.WHITE, true)); add(label, text("A better you tomorrow.", 12f, Color.LTGRAY), 3)
        footer.addView(label, headerLabelParams())
        footer.addView(profileButton("+ Today's Photo", { todayPhotoActions() }), if (compact) LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } else LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        add(hero, footer, 14)
    }
    private fun homeMetrics() {
        val summary = homeCard(page, true, 12)
        val date = LocalDate.now()
        val calories = prefs.getString("$date-cal", "0")?.toDoubleOrNull()?.toInt() ?: 0
        val protein = prefs.getString("$date-protein", "0")?.toDoubleOrNull()?.toInt() ?: 0
        val tasks = (0..6).count { prefs.getBoolean("$date-task-$it", false) }
        val values = listOf(calories, protein, waterToday(), tasks)
        val targets = listOf(calorieTarget(), proteinTarget(), waterGoal(), 7)
        val names = listOf("Calories", "Protein", "Water", "Tasks")
        val kinds = listOf("fire", "workout", "water", "check")
        val colors = listOf(Color.rgb(255, 157, 42), Color.rgb(31, 215, 219), Color.rgb(25, 182, 255), Color.rgb(31, 215, 219))
        val columns = if (resources.configuration.screenWidthDp >= 600 && resources.configuration.fontScale <= 1.15f) 4 else 2
        var line = row()
        values.indices.forEach { i ->
            if (i > 0 && i % columns == 0) { add(summary, line, 6); line = row() }
            val metric = column().apply { setPadding(dp(8), dp(9), dp(8), dp(9)) }
            val icon = homeIcon(kinds[i], colors[i], 28).apply { background = HomeArt("ring", values[i].toFloat() / targets[i].coerceAtLeast(1), colors[i]); setPadding(dp(13), dp(13), dp(13), dp(13)) }
            val head = row()
            head.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            icon.setPadding(dp(10), dp(10), dp(10), dp(10))
            val labels = column().apply { setPadding(dp(8), 0, 0, 0) }
            val value = text("${values[i]} / ${targets[i]}${if (i == 1) " g" else if (i == 2) " ml" else ""}", 12f, Color.WHITE, true)
            add(labels, value); add(labels, text(names[i], 10f, Color.LTGRAY), 4)
            head.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); add(metric, head)
            val bar = homeBar(metric, values[i], targets[i], colors[i])
            if (i == 3) { homeTasksValue = value; homeTasksBar = bar; homeTasksRing = icon }
            line.addView(metric, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(summary, line, 4)
    }
    private fun homePlan() {
        val plan = homeCard(page, false, 14).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(225, 255, 199), Color.rgb(201, 244, 168))).apply { cornerRadius = dp(20).toFloat() }
        }
        val head = row()
        head.addView(homeIcon("workout", forest, 28).apply { background = rounded(lime, 24); setPadding(dp(10), dp(10), dp(10), dp(10)) }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        val labels = column(); add(labels, text(if (isRest()) "Rest Day" else "Gym Day", 18f, INK, true)); add(labels, text(workout(), 12f, MUTED), 4)
        head.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(text("\u203a", 28f, INK).apply { minimumWidth = dp(36); gravity = Gravity.CENTER })
        head.minimumHeight = dp(48); head.isFocusable = true; head.setOnClickListener { showWorkoutPlan() }
        add(plan, head)
        val targets = row()
        listOf("${calorieTarget()} kcal" to "Daily energy", "${proteinTarget()} g" to "Protein", "INR ${budgetTarget()}" to "Daily budget").forEach { (value, label) ->
            val cell = column().apply { setPadding(dp(4), dp(9), dp(4), 0) }
            add(cell, text(value, 13f, INK, true)); add(cell, text(label, 10f, MUTED), 3)
            targets.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(plan, targets, 6)
    }
    private fun homeSchedule(parent: LinearLayout) {
        val card = homeCard(parent, false, 14)
        val header = adaptiveHeader()
        header.addView(text("Today's Schedule", 21f, INK, true), headerLabelParams())
        header.addView(text(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")), 11f, MUTED))
        add(card, header)
        val rest = isRest()
        val entries = listOf(
            Triple("07:00", "Morning weigh-in + water", "Check weight and log your first drink"),
            Triple("08:00", "Breakfast", "Poha / upma + eggs"),
            Triple("11:00", "Water + fruit", "Seasonal fruit and a water break"),
            Triple("13:00", "Lunch", "Rice + protein + vegetables"),
            Triple("17:00", if (rest) "Recovery & mobility" else "Gym session", if (rest) "Easy walk and stretches" else "${workout()} - ${prefs.getString("profile-duration", "60")} min"),
            Triple("20:00", "Dinner", "Chapati + eggs / soya + vegetables"),
            Triple("21:00", "Daily check-in + photo", "Log your day and add a progress photo")
        )
        val kinds = listOf("calendar", "diet", "apple", "diet", "workout", "diet", "camera")
        val colors = listOf(lime, Color.rgb(255, 161, 43), Color.rgb(255, 94, 100), Color.rgb(76, 138, 255), Color.rgb(164, 120, 240), Color.rgb(25, 190, 183), Color.rgb(245, 105, 159))
        val date = LocalDate.now()
        entries.forEachIndexed { i, (time, title, detail) ->
            if (i > 0) divider(card)
            val line = row().apply { minimumHeight = dp(70); setPadding(0, dp(8), 0, dp(8)) }
            line.addView(text(time, 11f, MUTED, true), LinearLayout.LayoutParams(dp(43), -2))
            line.addView(homeIcon(kinds[i], if (i == 0) forest else Color.WHITE, 20).apply { background = rounded(colors[i], 18); setPadding(dp(8), dp(8), dp(8), dp(8)) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(9) })
            val labels = column().apply {
                minimumHeight = dp(48); isFocusable = true
                setOnClickListener {
                    when (i) {
                        0 -> { pauseTimer(); openProfile("Today's Check-in") }
                        1, 3, 5 -> { pauseTimer(); tab = 1; render() }
                        2 -> customWaterAmount()
                        4 -> showWorkoutPlan()
                        else -> todayPhotoActions()
                    }
                }
            }
            add(labels, text(title, 13f, INK, true)); add(labels, text(detail, 11f, MUTED), 4)
            line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(CheckBox(this).apply {
                minimumWidth = dp(48); minimumHeight = dp(48); contentDescription = "Complete $title"
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(43, 99, 26))
                isChecked = prefs.getBoolean("$date-task-$i", false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("$date-task-$i", checked).apply()
                    val count = (0..6).count { prefs.getBoolean("$date-task-$it", false) }
                    homeTasksValue?.text = "$count / 7"; homeTasksBar?.progress = count; homeTasksRing?.background = HomeArt("ring", count / 7f, Color.rgb(31, 215, 219))
                }
            })
            add(card, line, if (i == 0) 12 else 0)
        }
        add(card, profileButton("Open Today's Check-in", { pauseTimer(); openProfile("Today's Check-in") }), 12)
    }
    private fun addWater(amount: Int) {
        val current = waterToday()
        val next = Hydration.add(current, amount)
        val date = LocalDate.now()
        prefs.edit().putString("$date-water-undo", Hydration.litres(current)).putString("$date-water", Hydration.litres(next)).apply()
        render()
        Toast.makeText(this, "$amount ml added", Toast.LENGTH_SHORT).show()
    }
    private fun customWaterAmount(goal: Boolean = false) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = if (goal) "250 to 10,000 ml" else "1 to 5,000 ml"
            setText(if (goal) waterGoal().toString() else "250"); selectAll(); contentDescription = if (goal) "Daily water goal in millilitres" else "Drink amount in millilitres"
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (goal) "Daily water goal" else "Add water (ml)")
            .setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toIntOrNull()
                val allowed = if (goal) 250..10000 else 1..5000
                if (value == null || value !in allowed) { input.error = "Enter ${allowed.first} to ${allowed.last} ml"; return@setOnClickListener }
                dialog.dismiss()
                if (goal) { prefs.edit().putString("profile-watergoal", value.toString()).apply(); render() } else addWater(value)
            }
        }
        dialog.show()
    }
    private fun homeWater(parent: LinearLayout) {
        val card = homeCard(parent, false, 14)
        val heading = row()
        heading.addView(homeIcon("water", Color.rgb(10, 155, 236), 27), LinearLayout.LayoutParams(dp(28), dp(30)).apply { marginEnd = dp(7) })
        heading.addView(text("Water Tracker", 21f, INK, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("\u22ef", 25f, MUTED).apply { gravity = Gravity.CENTER; minimumWidth = dp(48); minimumHeight = dp(48); contentDescription = "Edit daily water goal"; isFocusable = true; setOnClickListener { customWaterAmount(true) } })
        add(card, heading)
        val goal = waterGoal(); val amount = waterToday(); val fraction = (amount.toFloat() / goal).coerceIn(0f, 1f)
        add(card, text("Daily goal: $goal ml - tap \u22ef to edit", 11f, MUTED), 3)
        val total = row()
        total.addView(text("$amount / $goal ml", if (homeWide) 22f else 28f, Color.rgb(4, 130, 218), true), LinearLayout.LayoutParams(0, -2, 1f))
        total.addView(text("${(fraction * 100).toInt()}%", 14f, INK, true).apply {
            gravity = Gravity.CENTER; background = HomeArt("ring", fraction, Color.rgb(0, 174, 242)); contentDescription = "${(fraction * 100).toInt()} percent of water goal"
        }, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8) })
        add(card, total, 13)
        val glasses = ((goal + 249) / 250).coerceAtMost(20)
        val serving = if (glasses == 20 && goal > 5000) (goal + 19) / 20 else 250
        var line = row()
        for (i in 0 until glasses) {
            if (i > 0 && i % 4 == 0) { add(card, line, 5); line = row() }
            val portion = minOf(serving, goal - i * serving).coerceAtLeast(1)
            val filled = ((amount - i * serving).toFloat() / portion).coerceIn(0f, 1f)
            val glass = column().apply { gravity = Gravity.CENTER; setPadding(dp(2), dp(6), dp(2), dp(4)) }
            glass.addView(ImageView(this).apply {
                setImageDrawable(HomeArt("glass", filled)); contentDescription = "Glass ${i + 1}: ${(filled * portion).toInt()} of $portion ml"
            }, LinearLayout.LayoutParams(dp(32), dp(39)))
            add(glass, text("$portion ml", 9f, MUTED).apply { gravity = Gravity.CENTER }, 3)
            line.addView(glass, LinearLayout.LayoutParams(0, -2, 1f))
        }
        // Keep the final row aligned to the same four-column grid.
        repeat((4 - glasses % 4) % 4) { line.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
        add(card, line, 5)
        add(card, profileButton("+ 250 ml", { addWater(250) }), 14)
        val actions = row()
        listOf("+ 500 ml" to { addWater(500) }, "Custom" to { customWaterAmount() }).forEach { (label, action) ->
            actions.addView(pill(label, action, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        add(card, actions, 7)
        val undo = "${LocalDate.now()}-water-undo"
        if (prefs.contains(undo)) add(card, pill("Undo last drink", {
            prefs.edit().putString("${LocalDate.now()}-water", prefs.getString(undo, "0")).remove(undo).apply(); render()
        }, true), 7)
        val hour = prefs.getInt("alarm-1", 11); val minute = prefs.getInt("alarm-minute-1", 0)
        val enabled = prefs.getBoolean("alarms", false) && prefs.getBoolean("reminder-enabled-1", true) && (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
        val at = LocalDate.now().atTime(hour, minute)
        val status = if (enabled) "${if (at.isBefore(LocalDateTime.now())) "Tomorrow" else "Today"} at %02d:%02d".format(hour, minute) else "Set up water reminders"
        val reminder = row().apply { minimumHeight = dp(60); isFocusable = true; setOnClickListener { pauseTimer(); openProfile("Smart Reminders") } }
        reminder.addView(homeIcon("bell", INK, 22), LinearLayout.LayoutParams(dp(26), dp(28)).apply { marginEnd = dp(9) })
        val labels = column(); add(labels, text("Water reminder", 11f, MUTED)); add(labels, text(status, 13f, INK, true), 4)
        reminder.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); reminder.addView(text("\u203a", 24f, MUTED)); add(card, reminder, 9)
        add(card, text(if (amount >= goal) "Today's water goal is complete." else "$glasses glasses fill your $goal ml goal. Every drink you log counts.", 12f, MUTED), 7)
    }
    private fun homeTimer(parent: LinearLayout) {
        val card = homeCard(parent, true, 16)
        if (!timerInitialized) {
            timerRemaining = prefs.getString("profile-timer", "60")?.toLongOrNull()?.coerceIn(1, 3600) ?: 60L
            timerSelection = timerRemaining; timerInitialized = true
        }
        val header = row()
        header.addView(homeIcon("timer", lime, 30), LinearLayout.LayoutParams(dp(32), dp(36)).apply { marginEnd = dp(10) })
        val labels = column(); add(labels, text("Workout Rest Timer", 18f, Color.WHITE, true)); add(labels, text("Quick countdown between sets", 11f, Color.LTGRAY), 4)
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); add(card, header)
        val clock = row().apply { gravity = Gravity.CENTER }
        listOf(-10L, 0L, 10L).forEach { adjustment ->
            if (adjustment == 0L) {
                timerText = text("", if (compact) 39f else 46f, lime, true).apply { gravity = Gravity.CENTER; contentDescription = "Rest countdown" }
                clock.addView(timerText, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                val control = text(if (adjustment < 0) "-10s" else "+10s", 12f, Color.WHITE, true).apply {
                    background = rounded(Color.rgb(24, 49, 32), 28); gravity = Gravity.CENTER; minimumHeight = dp(48); minimumWidth = dp(48); isFocusable = true
                    contentDescription = if (adjustment < 0) "Subtract ten seconds" else "Add ten seconds"
                    setOnClickListener {
                        val next = (timerRemaining + adjustment).coerceIn(10, 3600)
                        timerSelection = 0
                        if (timerRunning) startTimer(next) else { timerRemaining = next; updateTimerViews() }
                    }
                }
                clock.addView(control, LinearLayout.LayoutParams(dp(48), dp(48)))
            }
        }
        add(card, clock, 18)
        val presets = row(); timerPresetButtons.clear()
        listOf(30L, 60L, 90L, 120L).forEach { seconds ->
            val button = text("${seconds}s", 13f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; minimumHeight = dp(48); isFocusable = true
                setOnClickListener { pauseTimer(); timerRemaining = seconds; timerSelection = seconds; updateTimerViews() }
            }
            timerPresetButtons[seconds] = button
            presets.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        add(card, presets, 12)
        timerPlayButton = profileButton("Start", {
            if (timerRunning) pauseTimer() else startTimer(if (timerRemaining > 0) timerRemaining else if (timerSelection > 0) timerSelection else 60L)
        })
        add(card, timerPlayButton!!, 12); updateTimerViews()
        add(card, pill("View Workout Plan", { showWorkoutPlan() }, true), 7)
    }
    private fun homeMotivation(parent: LinearLayout) {
        val card = homeCard(parent, true, 18).apply { background = HomeArt("landscape"); isFocusable = true; contentDescription = "Be one percent better every day. Open Progress."; setOnClickListener { homeProgress() } }
        card.addView(homeIcon("chart", lime, 30), LinearLayout.LayoutParams(dp(30), dp(36)))
        add(card, text("Be 1% Better\nEvery Day", 25f, Color.WHITE, true), 20)
        add(card, text("See how far you've come  \u2192", 13f, Color.rgb(218, 237, 204)), 8)
    }
    private fun startTimer(seconds: Long) {
        timer?.cancel(); timerRemaining = seconds.coerceIn(1, 3600); timerRunning = true
        timerEndElapsed = SystemClock.elapsedRealtime() + timerRemaining * 1000L
        updateTimerViews()
        timer = object : CountDownTimer(timerRemaining * 1000L, 200) {
            override fun onTick(ms: Long) { timerRemaining = (ms + 999) / 1000; updateTimerViews() }
            override fun onFinish() {
                timerRemaining = 0; timerRunning = false; updateTimerViews()
                val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.start()
    }

    private fun showWorkoutPlan() {
        val exercises = if (isRest()) "Recovery day\nOptional gentle walk\n10-15 minutes mobility" else when (LocalDate.now().dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> "Bench press: 3 × 8–12\nLat pulldown: 3 × 10–12\nSeated row: 3 × 10–12\nShoulder press: 3 × 8–12"
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> "Leg press: 3 × 8–12\nRomanian deadlift: 3 × 8–12\nLeg curl: 3 × 10–12\nCalf raises: 3 × 12–15"
            DayOfWeek.SATURDAY -> "Goblet squat: 3 × 10\nIncline press: 3 × 10\nCable row: 3 × 12\nPlank: 3 × 30 seconds"
            else -> "Goblet squat: 3 x 10\nIncline press: 3 x 10\nCable row: 3 x 12\nPlank: 3 x 30 seconds"
        }
        AlertDialog.Builder(this).setTitle(workout()).setMessage(exercises).setPositiveButton("Done",null).show()
    }
    private fun meals() { DietDashboard(this,prefs,page,dietState,{ openDiet(it) },{ render() }).render() }
    private fun progress() {
        ProgressDashboard(this,prefs,page,progressState,
            { openProgress(it) }, { render() }, { choosePhoto(it) }, { openPhoto(it) },
            { image,uri -> loadProfileImage(image,uri) }, { openCatalogue() }, {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="text/csv";putExtra(Intent.EXTRA_TITLE,"Fit100-${LocalDate.now()}.csv") },81)
            }).render()
    }
    private fun settingRow(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val line = row().apply { minimumHeight = dp(64); isFocusable = true; setPadding(dp(4), dp(13), dp(4), dp(13)); setOnClickListener { action() } }
        val labels = column()
        add(labels, text(title, 15f, INK, true))
        add(labels, text(detail, 12f, MUTED), 4)
        line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(text("\u203a", 24f, MUTED))
        add(parent, line, 3)
    }
    private fun editSetting(title: String, key: String, initial: String, numeric: Boolean = false) {
        val input = EditText(this).apply {
            setText(prefs.getString(key, initial)); setSelection(text.length)
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString(key, input.text.toString().trim()).apply(); render() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun profile() {
        when (profileScreen) {
            "Edit Profile" -> personalForm()
            "Change Profile Photo" -> profilePhotoPage()
            "Fitness Goals" -> goalsForm()
            "Diet Preferences" -> dietForm()
            "Workout Settings" -> workoutForm()
            "Smart Reminders" -> remindersForm()
            "Progress Photos & Measurements" -> measurementsPage()
            "App Settings" -> appSettingsPage()
            "Privacy & Security" -> privacyPage()
            "Today's Check-in" -> checkInForm()
            else -> profileMenu()
        }
    }
    private fun outlined(color: Int = SURFACE) = rounded(color, 14).apply { setStroke(dp(1), Color.rgb(181, 207, 168)) }
    private fun profileButton(label: String, action: () -> Unit) = pill(label, action).apply { background = rounded(lime, 14) }
    private fun profileValue(key: String, default: String) = profileDrafts[key] ?: prefs.getString(key, default) ?: default
    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(Color.rgb(192, 215, 179)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) })
    }
    private fun menuItem(parent: LinearLayout, icon: Int, title: String, detail: String, action: () -> Unit) {
        val line = row().apply { minimumHeight = dp(60); setPadding(dp(2), dp(6), dp(2), dp(6)); isFocusable = true; setOnClickListener { action() } }
        line.addView(ImageView(this).apply {
            setImageDrawable(FitIconDrawable(FitIconDrawable.forResource(icon), forest)); imageTintList = android.content.res.ColorStateList.valueOf(forest)
            background = rounded(lime, 24); setPadding(dp(10), dp(10), dp(10), dp(10))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
        val labels = column()
        add(labels, text(title, 14f, INK, true)); add(labels, text(detail, 11f, MUTED), 4)
        line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(text("\u203a", 25f, MUTED).apply { setPadding(dp(6), 0, dp(2), 0) })
        add(parent, line)
    }
    private fun portrait(size: Int): View {
        val frame = FrameLayout(this).apply { background = rounded(lime, size); clipToOutline = true }
        val initials = (prefs.getString("profile-name", "My Fitness Journey") ?: "F").trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
        frame.addView(text(initials.ifEmpty { "F" }, 27f, forest, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        prefs.getString("profile-photo", null)?.let { stored ->
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "Your profile photo" }
            frame.addView(image, FrameLayout.LayoutParams(-1, -1)); loadProfileImage(image, stored)
        }
        frame.contentDescription = "Change your profile photo"; frame.isFocusable = true
        frame.setOnClickListener { openProfile("Change Profile Photo") }
        return frame
    }
    private fun loadProfileImage(image:ImageView,stored:String) {
        photoLoader.execute {
            val bitmap=runCatching {
                val uri=Uri.parse(stored)
                if(Build.VERSION.SDK_INT>=28) android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver,uri)) { decoder,info,_->val ratio=(1200f/maxOf(info.size.width,info.size.height)).coerceAtMost(1f);decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1));decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE }
                else {
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
                    val options=BitmapFactory.Options().apply { inSampleSize=1 };while(bounds.outWidth/options.inSampleSize>1200 || bounds.outHeight/options.inSampleSize>1200)options.inSampleSize*=2
                    val raw=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }?:error("Unavailable")
                    val rotation=contentResolver.openInputStream(uri)?.use { when(android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)) { 3->180;6->90;8->270;else->0 } }?:0
                    if(rotation==0)raw else android.graphics.Bitmap.createBitmap(raw,0,0,raw.width,raw.height,android.graphics.Matrix().apply { postRotate(rotation.toFloat()) },true)
                }
            }.getOrNull()
            runOnUiThread { if(!isDestroyed && image.isAttachedToWindow) { image.setImageBitmap(bitmap);((image.parent as? FrameLayout)?.getChildAt(0) as? TextView)?.text=if(bitmap==null) "Photo unavailable. Choose again." else "";if(bitmap==null) { image.setBackgroundColor(BG);image.contentDescription="Photo unavailable. Choose it again." } } }
        }
    }

    private fun profileMenu() {
        val hero = panel(page, forest, 8, 14).apply { background = rounded(forest).apply { setStroke(dp(1), Color.rgb(43, 68, 39)) } }
        val identity = row()
        identity.addView(portrait(74), LinearLayout.LayoutParams(dp(74), dp(74)).apply { marginEnd = dp(12) })
        val name = column()
        add(name, text(prefs.getString("profile-name", "My Fitness Journey") ?: "My Fitness Journey", 21f, Color.WHITE, true))
        val d = day().coerceIn(0, 100)
        add(name, text(if (day() < 1) "Start your challenge from Today" else "Day $d of 100", 14f, lime, true), 5)
        add(name, text(challengeLabel(), 11f, Color.LTGRAY), 4)
        identity.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        identity.addView(ImageView(this).apply {
            setImageDrawable(FitIconDrawable("edit", lime)); imageTintList = android.content.res.ColorStateList.valueOf(lime)
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(Color.rgb(24, 58, 16), 24)
            contentDescription = "Edit Profile"; isFocusable = true; setOnClickListener { openProfile("Edit Profile") }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(hero, identity)
        val progressLine = row()
        progressLine.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = ChallengeClock.percent(challengeStart()); progressTintList = android.content.res.ColorStateList.valueOf(lime)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(45, 61, 48))
            contentDescription = "${ChallengeClock.percent(challengeStart())} percent of journey completed"
        }, LinearLayout.LayoutParams(0, dp(10), 1f))
        progressLine.addView(text("${ChallengeClock.percent(challengeStart())}%", 14f, Color.WHITE, true).apply { setPadding(dp(12), 0, 0, 0) })
        add(hero, progressLine, 14)
        val stats = row()
        val currentWeight = prefs.getString("${LocalDate.now()}-weight", null)?.takeIf { it.isNotBlank() } ?: prefs.getString("profile-currentweight", "--")
        listOf("$currentWeight kg" to "Current weight", "${prefs.getString("profile-targetweight", "--")} kg" to "Target weight", "${if(challengeStart()==null)100 else ((java.time.Duration.ofDays(100).toMillis()-ChallengeClock.elapsed(challengeStart())+86_399_999)/86_400_000)}" to "Days left").forEach { (value, label) ->
            val stat = column().apply { setPadding(dp(3), dp(8), dp(3), dp(5)) }
            add(stat, text(value, 17f, Color.WHITE, true)); add(stat, text(label, 10f, Color.LTGRAY), 4)
            stats.addView(stat, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(hero, stats, 8)
        if(challengeStart()==null)add(page,profileButton("Start My 100-Day Challenge",{ startChallenge() }),14)
        add(page,profileButton("Sports & Activities",{ tab=5;pauseTimer();pageScroll?.scrollTo(0,0);render() }),10)
        val menu = panel(page, SURFACE, 0, 13)
        val items = listOf(
            Triple(android.R.drawable.ic_menu_myplaces, "Personal Information", "Name, age, height, weight"),
            Triple(android.R.drawable.ic_menu_today, "Fitness Goals", "Calories, protein, target weight"),
            Triple(android.R.drawable.ic_menu_agenda, "Diet Preferences", "Food type, allergies, budget"),
            Triple(android.R.drawable.ic_menu_manage, "Workout Settings", "Gym days, rest days, duration"),
            Triple(android.R.drawable.ic_lock_idle_alarm, "Smart Reminders", "Meals, water and workout alerts"),
            Triple(android.R.drawable.ic_menu_camera, "Progress Photos & Measurements", "Daily photos, measurements and weight"),
            Triple(android.R.drawable.ic_menu_preferences, "App Settings", "Export data, notifications and privacy")
        )
        items.forEachIndexed { i, (icon, title, detail) ->
            if (i > 0) divider(menu)
            menuItem(menu, icon, title, detail) { openProfile(if (title == "Personal Information") "Edit Profile" else title) }
        }
        add(page, profileButton("Today's check-in", { openProfile("Today's Check-in") }), 14)
        dailySnapshot(page)
    }
    private fun dailySnapshot(parent: LinearLayout) {
        val date = LocalDate.now().toString()
        val saved = listOf("weight", "cal", "protein", "spent", "sleep", "water").count { prefs.getString("$date-$it", null)?.toDoubleOrNull() != null }
        val card = panel(parent, Color.rgb(22, 58, 31), 16, 18)
        add(card, text("YOUR DAILY SNAPSHOT", 11f, lime, true))
        add(card, text(if (saved == 0) "A small step for a stronger you" else "$saved of 6 check-in entries saved", 18f, Color.WHITE, true), 8)
        val metrics = adaptiveHeader()
        listOf("Water" to "${prefs.getString("$date-water", "0").orEmpty().ifBlank { "0" }} L",
            "Protein" to "${prefs.getString("$date-protein", "0").orEmpty().ifBlank { "0" }} g",
            "Sleep" to "${prefs.getString("$date-sleep", "0").orEmpty().ifBlank { "0" }} h").forEach { (label, value) ->
            val metric = column().apply { setPadding(dp(4), dp(10), dp(4), dp(6)) }
            add(metric, text(value, 19f, lime, true)); add(metric, text(label, 12f, Color.rgb(205, 227, 194)), 4)
            metrics.addView(metric, headerLabelParams())
        }
        add(card, metrics, 8)
        add(card, text("Finish with a progress photo, or see how your routine is adding up.", 13f, Color.rgb(218, 236, 208)), 10)
        add(card, profileButton("Daily Photo Album", { openCatalogue() }), 14)
        add(card, pill("View my progress", { tab = 2; pauseTimer(); render() }, true), 8)
    }
    private fun formField(parent: LinearLayout, label: String, key: String, default: String, numeric: Boolean = false): EditText {
        val cell = adaptiveHeader().apply { background = outlined(Color.rgb(226, 239, 216)); setPadding(dp(12), dp(4), dp(12), dp(4)) }
        profileDrafts.putIfAbsent(key, prefs.getString(key, default) ?: default)
        val input = EditText(this).apply {
            id = View.generateViewId(); setTextColor(INK); textSize = 15f; minimumHeight = dp(48)
            background = rounded(Color.rgb(240, 248, 233), 10); setPadding(dp(10), dp(8), dp(10), dp(8))
            inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(profileValue(key, default)); contentDescription = label
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { profileDrafts[key] = s.toString() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        val fieldLabel = text(label, 12f, MUTED, true).apply { labelFor = input.id }
        if (compact) { add(cell, fieldLabel); add(cell, input) }
        else {
            cell.addView(fieldLabel, LinearLayout.LayoutParams(0, -2, 0.9f))
            cell.addView(input, LinearLayout.LayoutParams(0, -2, 1.1f))
        }
        add(parent, cell, 7)
        return input
    }
    private fun choices(parent: LinearLayout, label: String, key: String, default: String, values: List<String>, multi: Boolean = false, changed: (() -> Unit)? = null) {
        add(parent, text(label, 14f, INK, true), 16)
        val container = column()
        fun draw() {
            container.removeAllViews()
            val selected = profileValue(key, default).split(",").map { it.trim() }.toSet()
            values.chunked(if (compact) 2 else 3).forEach { group ->
                val line = row()
                group.forEach { value ->
                    val active = value in selected
                    val chip = text(value, 12f, if (active) Color.WHITE else INK, active).apply {
                        minimumHeight = dp(48); gravity = Gravity.CENTER; isFocusable = true; isSelected = active
                        setPadding(dp(5), dp(8), dp(5), dp(8)); background = rounded(if (active) Color.rgb(29, 77, 13) else Color.rgb(210, 229, 197), 22)
                        contentDescription = "$value, ${if (active) "selected" else "not selected"}"
                        setOnClickListener {
                            profileDrafts[key] = if (multi) { val next = selected.toMutableSet(); if (!next.add(value)) next.remove(value); next.filter { it.isNotBlank() }.joinToString(", ") } else value
                            draw(); changed?.invoke()
                        }
                    }
                    line.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
                }
                add(container, line, 6)
            }
        }
        add(parent, container, 4); draw()
    }
    private fun saveProfile(keys: List<String>, numeric: Set<String> = emptySet(), positive: Set<String> = emptySet()) {
        val invalid = numeric.firstOrNull { key ->
            val value = profileDrafts[key] ?: prefs.getString(key, "") ?: ""; val number = value.toDoubleOrNull()
            (value.isBlank() && key in positive) || (value.isNotBlank() && (number == null || !number.isFinite() || if (key in positive) number <= 0 else number < 0))
        }
        if (invalid != null) { Toast.makeText(this, "Please enter valid numbers. Measurements must be greater than zero.", Toast.LENGTH_LONG).show(); return }
        if ("profile-name" in keys && profileValue("profile-name", "My Fitness Journey").isBlank()) { Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show(); return }
        if ("profile-watergoal" in keys && profileValue("profile-watergoal", "4000").toIntOrNull()?.let { it in 250..10000 } != true) {
            Toast.makeText(this, "Enter a water goal from 250 to 10,000 ml", Toast.LENGTH_LONG).show(); return
        }
        val edit = prefs.edit(); keys.forEach { key -> profileDrafts[key]?.let { edit.putString(key, it.trim()) } }; edit.apply()
        if ("profile-timer" in keys) { pauseTimer(); timerInitialized = false }
        keys.forEach { profileDrafts.remove(it) }; Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show(); render()
    }
    private fun personalForm() {
        val photo = column().apply { gravity = Gravity.CENTER }
        photo.addView(portrait(108), LinearLayout.LayoutParams(dp(108), dp(108)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        add(photo, text("Tap to change photo", 12f, MUTED).apply { gravity = Gravity.CENTER }, 8); add(page, photo, 14)
        val card = panel(page, SURFACE, 16, 12)
        val fields = listOf(Triple("Full name", "profile-name", "My Fitness Journey"), Triple("Age (years)", "profile-age", ""), Triple("Height (cm)", "profile-height", ""), Triple("Starting weight (kg)", "profile-startweight", ""), Triple("Current weight (kg)", "profile-currentweight", ""), Triple("Target weight (kg)", "profile-targetweight", ""))
        fields.forEach { (label, key, default) -> formField(card, label, key, default, key != "profile-name") }
        choices(card, "Gender", "profile-gender", "Not specified", listOf("Male", "Female", "Not specified"))
        val numbers = fields.map { it.second }.filter { it != "profile-name" }.toSet()
        add(page, profileButton("Save Changes", { saveProfile(fields.map { it.second } + "profile-gender", numbers, numbers) }), 20)
    }
    private fun profilePhotoPage() {
        val photo = column().apply { gravity = Gravity.CENTER }
        photo.addView(portrait(200), LinearLayout.LayoutParams(dp(200), dp(200)).apply { gravity = Gravity.CENTER_HORIZONTAL }); add(page, photo, 18)
        val buttons = adaptiveHeader()
        buttons.addView(profileButton("Choose from Gallery", { choosePhoto("profile-photo") }), headerLabelParams())
        buttons.addView(pill("Take Photo", { takeProfilePhoto() }, true), headerLabelParams()); add(page, buttons, 20)
        val note = panel(page, Color.rgb(232, 243, 225))
        add(note, text("Make it yours", 16f, INK, true))
        add(note, text("Use a clear portrait for your profile. Workout photos are kept separately in Progress Photos & Measurements. Your selected profile photo saves automatically.", 13f, MUTED), 8)
        add(page, profileButton("Done", { openProfile("Edit Profile") }), 18)
    }
    private fun takeProfilePhoto() = takePhoto("profile-photo")
    private fun takePhoto(key:String) {
        pendingPhotoKey=key
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA),6);return }
        launchCamera()
    }
    private fun launchCamera() {
        val file=java.io.File(filesDir,"profile-photos/portrait-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        val uri=Uri.parse("content://$packageName.profilephotos/${file.name}")
        val intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);clipData=ClipData.newRawUri("Fit100 photo",uri) }
        try { pendingCameraUri=uri.toString();startActivityForResult(intent,72) }
        catch(_:Exception) { pendingCameraUri=null;pendingPhotoKey=null;file.delete();Toast.makeText(this,"Camera unavailable. Use Choose from gallery.",Toast.LENGTH_LONG).show() }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==5) { scheduleReminders(this);return }
        if(requestCode==6) {
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)launchCamera()
            else { pendingPhotoKey=null;AlertDialog.Builder(this).setTitle("Camera access needed").setMessage("Allow Camera in Android Settings to take photos. You can still choose photos from your gallery.").setNegativeButton("Not now",null).setPositiveButton("Open Settings") { _,_->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }.show() }
        }
    }
    private fun goalsForm() {
        heading(page, "Target & journey", "Set a routine that works for you")
        val card = panel(page)
        choices(card, "Goal type", "profile-goal", "Fat loss + muscle retention", listOf("Fat loss + muscle retention", "Build muscle", "Maintain fitness"))
        formField(card, "Target weight (kg)", "profile-targetweight", "", true)
        choices(card, "Activity level", "profile-activity", "Moderate", listOf("Light", "Moderate", "Very active"))
        settingRow(card,"Challenge started",challengeLabel()) { Toast.makeText(this,if(challengeStart()==null)"Use Start Challenge on Home or Profile when you are ready" else "The start time is fixed for this challenge",Toast.LENGTH_LONG).show() }
        add(card,text("100 full days from your start time. Progress uses elapsed time, including across time zones.",13f,MUTED),8)
        heading(page, "Daily targets", "Gym and rest days have separate calorie targets")
        val targets = panel(page)
        val fields = listOf(Triple("Gym-day calories (kcal)", "profile-gymcal", "2400"), Triple("Rest-day calories (kcal)", "profile-restcal", "2200"), Triple("Protein (g)", "profile-protein", "140"), Triple("Carbohydrates (g)", "profile-carbs", "250"), Triple("Fats (g)", "profile-fats", "70"), Triple("Water target (ml)", "profile-watergoal", "4000"))
        fields.forEach { (label, key, value) -> formField(targets, label, key, value, true) }
        val keys = fields.map { it.second } + "profile-targetweight"
        add(page, profileButton("Save Changes", { saveProfile(keys + listOf("profile-goal", "profile-activity"), keys.toSet(), keys.toSet()) }), 20)
    }
    private fun dietForm() {
        val card = panel(page, top = 14)
        choices(card, "Food preference", "profile-food", "Non-vegetarian", listOf("Vegetarian", "Non-vegetarian", "Eggetarian", "Vegan"))
        choices(card, "Dairy products", "profile-dairy", "Dairy-free", listOf("Include", "Dairy-free"))
        formField(card, "Daily food budget (INR)", "profile-budget", "200", true)
        heading(page, "Food allergies / exclusions", "Separate multiple foods with commas")
        val diet = panel(page)
        formField(diet, "Allergies / exclusions", "profile-allergies", "Dairy")
        formField(diet, "Preferred foods", "profile-preferred", "Eggs, chicken, rice, dal, soy")
        add(page, profileButton("Save Changes", { saveProfile(listOf("profile-food", "profile-dairy", "profile-budget", "profile-allergies", "profile-preferred"), setOf("profile-budget")) }), 20)
    }
    private fun workoutForm() {
        val card = panel(page, top = 14)
        choices(card, "Gym frequency (days per week)", "profile-gymdays", "5", (1..7).map { it.toString() })
        choices(card, "Rest days", "profile-rest", "Wednesday, Sunday", listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"), true)
        formField(card, "Workout duration (minutes)", "profile-duration", "60", true)
        formField(card, "Default rest timer (seconds)", "profile-timer", "60", true)
        choices(card, "Preferred workout type", "profile-workout-type", "Strength", listOf("Strength", "Cardio", "Both"))
        formField(card, "Training goal", "profile-training-goal", "Fat loss + muscle retention")
        formField(card, "Gym location (optional)", "profile-gym-location", "")
        add(page, profileButton("Save Changes", { saveProfile(listOf("profile-gymdays", "profile-rest", "profile-duration", "profile-timer", "profile-workout-type", "profile-training-goal", "profile-gym-location"), setOf("profile-duration", "profile-timer"), setOf("profile-duration", "profile-timer")) }), 20)
    }
    private fun remindersForm() {
        val master = panel(page, Color.rgb(232, 243, 225))
        val enabled = Switch(this).apply {
            text = "Daily reminders"; setTextColor(INK); minimumHeight = dp(56)
            isChecked = prefs.getBoolean("alarms", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("alarms", checked).apply();if(checked && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),5) else scheduleReminders(this@MainActivity) }
        }
        add(master, enabled)
        add(master, text("Times and individual switches save automatically.", 12f, MUTED), 4)
        val card = panel(page, top = 14, padding = 12)
        reminders.forEachIndexed { index, item ->
            val hour = prefs.getInt("alarm-$index", item.second); val minute = prefs.getInt("alarm-minute-$index", 0)
            val line = row().apply { minimumHeight = dp(76) }
            val labels = column().apply { minimumHeight = dp(56); gravity = Gravity.CENTER_VERTICAL; isFocusable = true; setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    prefs.edit().putInt("alarm-$index", h).putInt("alarm-minute-$index", m).apply()
                    if (prefs.getBoolean("alarms", false)) scheduleReminders(this@MainActivity)
                    render()
                }, hour, minute, android.text.format.DateFormat.is24HourFormat(this@MainActivity)).show()
            } }
            add(labels, text(item.first, 15f, INK, true)); add(labels, text("%02d:%02d  - Tap to change".format(hour, minute), 12f, MUTED), 5)
            line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(Switch(this).apply {
                minimumHeight = dp(48); minimumWidth = dp(48); contentDescription = "${item.first} reminder"
                isChecked = prefs.getBoolean("reminder-enabled-$index", true); thumbTintList = android.content.res.ColorStateList.valueOf(lime)
                setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("reminder-enabled-$index", value).apply(); if (prefs.getBoolean("alarms", false)) scheduleReminders(this@MainActivity) }
            })
            add(card, line); divider(card)
        }
        add(page, profileButton(if (prefs.getBoolean("alarms", false)) "Save & Schedule Reminders" else "Enable Reminders", {
                prefs.edit().putBoolean("alarms", true).apply(); scheduleReminders(this)
            Toast.makeText(this, "Reminders scheduled", Toast.LENGTH_SHORT).show(); render()
        }), 20)
        settingRow(page, "Notification permissions", "Open Android notification settings") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
    }
    private fun measurementsPage() {
        choices(page, "View", "journal-tab", photoJournalTab, listOf("Photos", "Measurements", "Weight History"), changed = {
            photoJournalTab = profileValue("journal-tab", photoJournalTab); pageScroll?.scrollTo(0, 0); render()
        })
        when (photoJournalTab) {
            "Measurements" -> {
                heading(page, "Body measurements", "Record today's measurements in centimetres")
                val card = panel(page)
                val date = LocalDate.now()
                val keys = listOf("Chest", "Waist", "Hips", "Upper arm", "Thigh").map { label ->
                    val key = "$date-measurement-${label.lowercase().replace(" ", "-")}"
                    formField(card, "$label (cm)", key, "", true); key
                }
                add(page, profileButton("Save Measurements", { saveProfile(keys, keys.toSet(), keys.toSet()) }), 18)
                val history = prefs.all.keys.filter { it.contains("-measurement-") }.sortedDescending()
                if (history.isNotEmpty()) {
                    val saved = panel(page); add(saved, text("Saved measurements", 17f, INK, true))
                    history.take(30).forEach { key -> add(saved, text("${key.take(10)} - ${key.substringAfter("-measurement-").replace("-", " ")}: ${prefs.getString(key, "")} cm", 12f, MUTED), 8) }
                }
            }
            "Weight History" -> {
                heading(page, "Your weight history", "All saved weigh-ins, newest first")
                val card = panel(page)
                val keys = prefs.all.keys.filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}-weight")) }.sortedDescending()
                if (keys.isEmpty()) add(card, text("No weigh-ins yet. Add your first daily check-in.", 14f, MUTED))
                keys.forEach { key -> add(card, text("${key.removeSuffix("-weight")}    ${prefs.getString(key, "")} kg", 15f, INK), 10) }
                add(page, profileButton("Log Today's Weight", { openProfile("Today's Check-in") }), 14)
            }
            else -> {
                heading(page, "Daily workout photos", "Compare your before and after workout images")
                add(page, catalogueShortcut(), 12)
                heading(page, "Body progress photos", "Use similar lighting and background each time")
                val card = panel(page)
                listOf("Front", "Side", "Back").forEach { angle ->
                    val key = "body-photo-${LocalDate.now()}-${angle.lowercase()}"
                    val slot = column()
                    add(slot, text(angle, 15f, INK, true))
                    prefs.getString(key, null)?.let { uri ->
                        val image = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER; background = rounded(BG)
                            contentDescription = "$angle progress photo. Tap to view."; isFocusable = true; setOnClickListener { openPhoto(Uri.parse(uri)) }
                        }
                        slot.addView(image, LinearLayout.LayoutParams(-1, dp(160)))
                        if (!prefs.getBoolean("hide-progress-photos", false)) loadProfileImage(image, uri)
                        else add(slot, text("Preview hidden. Tap photo area to view.", 12f, MUTED), 5)
                    }
                    add(slot, profileButton(if (prefs.contains(key)) "Change $angle Photo" else "Add $angle Photo", { choosePhoto(key) }), 8)
                    add(card, slot, 12)
                }
                val older = prefs.all.keys.filter { it.startsWith("body-photo-") && !it.startsWith("body-photo-${LocalDate.now()}") }.sortedDescending()
                older.forEach { key -> settingRow(card, key.removePrefix("body-photo-"), "View saved body progress photo") { prefs.getString(key, null)?.let { openPhoto(Uri.parse(it)) } } }
            }
        }
    }
    private fun checkInForm() {
        heading(page, "Today's check-in", LocalDate.now().toString())
        val card = panel(page)
        val fields = listOf("Weight (kg)" to "weight", "Calories eaten" to "cal", "Protein (g)" to "protein", "Food spend (INR)" to "spent", "Sleep (hours)" to "sleep", "Water (litres)" to "water")
        val keys = fields.map { (label, key) -> val storage = "${LocalDate.now()}-$key"; formField(card, label, storage, "", true); storage }
        add(page, profileButton("Save Today's Check-in", { saveProfile(keys, keys.toSet()) }), 20)
        dailySnapshot(page)
    }
    private fun appSettingsPage() {
        val card = panel(page, top = 14)
        settingRow(card, "Privacy & Security", "Photo visibility and local storage") { openProfile("Privacy & Security") }; divider(card)
        settingRow(card, "Export My Data (CSV)", "Save your profile, targets and daily logs") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "Fit100-${LocalDate.now()}.csv")
            }, 81)
        }; divider(card)
        settingRow(card,"Camera permission",if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)"Allowed" else "Not allowed") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) };divider(card)
        settingRow(card, "Notifications", "Manage Fit100 alerts in Android") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }; divider(card)
        add(card, text("Units: metric (kg, cm)", 14f, MUTED), 12)
        settingRow(card, "About Fit100", "Version 1.5 - 100-day fitness journey") {
            AlertDialog.Builder(this).setTitle("Fit100 1.0").setMessage("Track your routine, meals, recovery and photos. Your settings and logs are saved on this device.").setPositiveButton("OK", null).show()
        }
    }
    private fun privacyPage() {
        val card = panel(page, top = 14)
        val line = row()
        line.addView(text("Hide progress thumbnails", 15f, INK, true), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Switch(this).apply {
            minimumHeight = dp(48); contentDescription = "Hide progress photo thumbnails"; isChecked = prefs.getBoolean("hide-progress-photos", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("hide-progress-photos", checked).apply() }
        })
        add(card, line)
        add(card, text("Hide previews on Today and photo pages. You can still tap saved photos to open them. This does not lock the app.", 12f, MUTED), 8); divider(card)
        add(card, text("Your data stays on this device", 16f, INK, true), 12)
        add(card, text("Fit100 saves logs locally. Gallery photos stay with the selected document provider; camera portraits are stored in the app. Uninstalling can remove local data. CSV exports go to the location you choose.", 13f, MUTED), 8)
    }

    @Deprecated("Legacy picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 81 && resultCode == RESULT_OK && data?.data != null) {
            try {
                fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { writer ->
                    writer.appendLine("Key,Value")
                    prefs.all.toSortedMap().forEach { (key, value) ->
                        if (!key.contains("photo")) writer.appendLine("${csv(key)},${csv(value.toString())}")
                    }
                } ?: error("No export destination")
                Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this, "Could not export. Please choose another location.", Toast.LENGTH_LONG).show() }
        }
        if (requestCode == 72) {
            if (resultCode == RESULT_OK) pendingCameraUri?.let { uri ->
                if (runCatching { val o=BitmapFactory.Options().apply { inJustDecodeBounds=true };contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,o) };o.outWidth>0 && o.outHeight>0 }.getOrDefault(false)) prefs.edit().putString(pendingPhotoKey?:"profile-photo", uri).apply() else Toast.makeText(this,"Camera did not save a valid photo. Please try again.",Toast.LENGTH_LONG).show()
            }
            if(resultCode!=RESULT_OK)pendingCameraUri?.let { java.io.File(filesDir,"profile-photos/${Uri.parse(it).lastPathSegment}").delete() }
            pendingCameraUri = null
            pendingPhotoKey = null
            render()
        }
        if (requestCode == 71 && resultCode == RESULT_OK && data?.data != null) {
            val source=data.data!!
            val key=pendingPhotoKey?:"photo-$photoDay"
            pendingPhotoKey=null
            photoLoader.execute {
                var file:java.io.File?=null
                val saved=runCatching {
                    file=java.io.File(filesDir,"profile-photos/portrait-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                    contentResolver.openInputStream(source)?.use { input->file!!.outputStream().use { output->val buffer=ByteArray(8192);var total=0L;while(true) { val count=input.read(buffer);if(count<0)break;total+=count;require(total<=30L*1024*1024);output.write(buffer,0,count) } } }?:error("Unavailable image")
                    val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file!!.path,options);require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=100_000_000)
                    "content://$packageName.profilephotos/${file!!.name}"
                }.getOrNull()
                if(saved==null)file?.delete() else prefs.edit().putString(key,saved).apply()
                runOnUiThread { if(!isDestroyed) { Toast.makeText(this,if(saved==null)"Could not import image. Use a photo under 30 MB." else "Photo saved",Toast.LENGTH_LONG).show();render() } }
            }
        }
        if (requestCode == 71) pendingPhotoKey = null
    }
}

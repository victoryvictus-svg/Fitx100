package com.fit100.tracker
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import java.time.LocalDate

object SportsIntegration {
 fun completed(c:Context,date:LocalDate=LocalDate.now())=SportsHub.repo(c).history().any { it.date==date && it.completion>=100 } || c.getSharedPreferences("fit100",0).getBoolean("sports-recovery-$date",false)
 fun status(c:Context):String {val records=SportsHub.repo(c).history();val today=records.filter { it.date==LocalDate.now() };val week=records.filter { it.date>=LocalDate.now().minusDays(6) && it.date<=LocalDate.now() };val active=SportsHub.repo(c).active();val live=active?.takeIf { it.date==LocalDate.now() };return "Today: ${today.sumOf { it.completedExercises }+(live?.completedExercises?:0)} exercises · ${(today.sumOf { it.timer.accumulated }+(live?.timer?.elapsed(SportsHub.now(c))?:0))/60000} active min\nLast 7 days: ${week.size} sessions · %.2f km\n%d day workout / recovery streak".format(week.sumOf { it.distance?:0.0 }/1000,SportsHub.streak(c))+(active?.let { "\n${it.name}: ${it.status.lowercase()} · ${it.completion}%" }?:"") }
 fun statusView(c:Context,color:Int)=TextView(c).apply { textSize=13f;setTextColor(color);text=status(c);val h=Handler(Looper.getMainLooper());val update:()->Unit={h.post { if(isAttachedToWindow)text=status(c) }};addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener { override fun onViewAttachedToWindow(v:View){SportsHub.listeners.add(update);text=status(c)};override fun onViewDetachedFromWindow(v:View){SportsHub.listeners.remove(update);h.removeCallbacksAndMessages(null)} }) }
}

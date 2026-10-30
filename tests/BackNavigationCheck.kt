package com.fit100.tracker

fun main() {
    val back = BackNavigation()
    // Every Profile subpage (including its photo catalogue) returns to Profile.
    repeat(11) { check(back.onBack(false, it * 10L, true) == BackAction.PROFILE) }
    // Profile overview and other tabs return home.
    check(back.onBack(false, 120) == BackAction.HOME)
    repeat(5) { check(back.onBack(false, it * 100L) == BackAction.HOME) }
    check(back.onBack(true, 500) == BackAction.PROMPT_EXIT)
    check(back.onBack(true, 1500) == BackAction.EXIT)
    // An expired confirmation starts a fresh double-press window.
    check(back.onBack(true, 3000) == BackAction.PROMPT_EXIT)
    check(back.onBack(true, 5001) == BackAction.PROMPT_EXIT)
    check(back.onBack(true, 7001) == BackAction.EXIT)
    // Leaving home or pausing the app must clear an armed exit.
    check(back.onBack(true, 8000) == BackAction.PROMPT_EXIT)
    check(back.onBack(false, 8100) == BackAction.HOME)
    check(back.onBack(true, 8200) == BackAction.PROMPT_EXIT)
    back.reset()
    check(back.onBack(true, 8300) == BackAction.PROMPT_EXIT)
    check(back.onBack(true, 8400) == BackAction.EXIT)
    check(back.onBack(true, 9000) == BackAction.PROMPT_EXIT)
    check(back.onBack(false, 9100, true) == BackAction.PROFILE)
    check(back.onBack(false, 9200) == BackAction.HOME)
    check(back.onBack(true, 9300) == BackAction.PROMPT_EXIT)
    check(back.onBack(true, 9400) == BackAction.EXIT)
    check(back.onBack(false,10000,isProgressDetail=true)==BackAction.PROGRESS)
    check(back.onBack(false,10100)==BackAction.HOME)
    check(back.onBack(true,10200)==BackAction.PROMPT_EXIT)
    check(back.onBack(false,10300,isProgressDetail=true)==BackAction.PROGRESS)
    check(back.onBack(true,10400)==BackAction.PROMPT_EXIT)
    println("Back navigation checks passed")
}

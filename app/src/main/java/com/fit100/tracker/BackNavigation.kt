package com.fit100.tracker

internal enum class BackAction { PROGRESS, PROFILE, HOME, PROMPT_EXIT, EXIT }

internal class BackNavigation {
    private var lastHomeBack: Long? = null

    fun reset() { lastHomeBack = null }

    fun onBack(isHome: Boolean, now: Long, isProfileDetail: Boolean = false, isProgressDetail: Boolean = false): BackAction {
        if (isProfileDetail) {
            reset()
            return BackAction.PROFILE
        }
        if (isProgressDetail) { reset(); return BackAction.PROGRESS }
        if (!isHome) {
            reset()
            return BackAction.HOME
        }
        val previous = lastHomeBack
        if (previous != null && now - previous in 0..2000) {
            reset()
            return BackAction.EXIT
        }
        lastHomeBack = now
        return BackAction.PROMPT_EXIT
    }
}

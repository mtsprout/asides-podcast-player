package com.asides.app.service

/** Shared names for the sleep timer, which the service runs and the UI asks for. */
object SleepTimer {
    /** Custom session command; the "minutes" argument is >0 for a timer, -1 for end of episode, 0 to cancel. */
    const val COMMAND = "com.asides.app.SLEEP_TIMER"
    const val ARG_MINUTES = "minutes"

    // Stored in the "prefs" file so the UI can show what is running without asking the service.
    const val PREF_AT = "sleepAt"
    const val PREF_END_OF_EPISODE = "sleepEnd"
}

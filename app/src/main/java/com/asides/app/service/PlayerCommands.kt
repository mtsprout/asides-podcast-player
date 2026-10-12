package com.asides.app.service

/** Custom commands behind the buttons on the lock-screen card and notification. */
object PlayerCommands {
    const val BACK_10 = "com.asides.app.BACK_10"
    const val FORWARD_30 = "com.asides.app.FORWARD_30"

    /** Back to the beginning of the current episode. */
    const val RESTART = "com.asides.app.RESTART"

    /** On to the next unplayed episode (the same choice autoplay makes). */
    const val NEXT_EPISODE = "com.asides.app.NEXT_EPISODE"
}

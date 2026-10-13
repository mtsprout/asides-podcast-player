package com.asides.app.service

/** Custom commands behind the buttons on the lock-screen card and notification. */
object PlayerCommands {
    const val BACK_10 = "com.asides.app.BACK_10"
    const val FORWARD_30 = "com.asides.app.FORWARD_30"

    /** Back to the beginning of the current episode. */
    const val RESTART = "com.asides.app.RESTART"

    /** On to the next unplayed episode (the same choice autoplay makes). */
    const val NEXT_EPISODE = "com.asides.app.NEXT_EPISODE"

    /** Play an aside in context without losing your place; carries [ARG_EPISODE_ID] and [ARG_POSITION_MS]. */
    const val START_REVIEW = "com.asides.app.START_REVIEW"

    /** Leave a review and go back to where you were, paused. */
    const val END_REVIEW = "com.asides.app.END_REVIEW"

    const val ARG_EPISODE_ID = "episodeId"
    const val ARG_POSITION_MS = "positionMs"
}

package com.asides.app.ui

import com.asides.app.data.AnnotationRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Searching asides and turning them into text for sharing or saving. Pure, so it can be unit tested. */
object AsidesText {
    /** Every word of [query] appears (any case) in the aside's text, its episode's title or its podcast's name. */
    fun matches(row: AnnotationRow, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = "${row.transcript.orEmpty()} ${row.episodeTitle} ${row.podcastTitle}".lowercase()
        return words.all { it in haystack }
    }

    private fun text(row: AnnotationRow) = row.transcript?.trim()?.takeIf { it.isNotEmpty() } ?: "(no transcript)"

    /** One aside as a short message: where it was made, then what was said. */
    fun plain(row: AnnotationRow): String =
        "Aside at ${formatTime(row.positionMs)} in \"${row.episodeTitle}\" (${row.podcastTitle}):\n${text(row)}"

    /** Asides as a Markdown document: podcast, then episode (oldest first, as in the list), then each aside by time. */
    fun markdown(rows: List<AnnotationRow>): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = StringBuilder("# Asides\n")
        rows.groupBy { it.podcastTitle }.forEach { (podcast, byPodcast) ->
            out.append("\n## ").append(podcast).append('\n')
            byPodcast.groupBy { it.episodeId }.values.forEach { episode ->
                val head = episode.first()
                out.append("\n### ").append(head.episodeTitle)
                if (head.episodePubDate > 0) out.append(" (").append(date.format(Date(head.episodePubDate))).append(')')
                out.append("\n\n")
                episode.sortedBy { it.positionMs }.forEach {
                    out.append("- **").append(formatTime(it.positionMs)).append("** ").append(text(it).replace("\n", " ")).append('\n')
                }
            }
        }
        return out.toString()
    }
}

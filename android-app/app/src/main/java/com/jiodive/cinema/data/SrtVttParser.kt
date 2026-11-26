package com.jiodive.cinema.data

import com.jiodive.cinema.model.SubtitleCue

object SrtVttParser {
    private val timePattern = Regex(
        """(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})"""
    )
    private val tagRegex = Regex("""<[^>]+>|\{[^}]+\}""")

    fun parse(raw: String): List<SubtitleCue> {
        val normalized = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val blocks = normalized.split(Regex("""\n{2,}"""))
        val cues = ArrayList<SubtitleCue>(blocks.size)

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val first = lines.first().trim()
            if (first.startsWith("WEBVTT") || first.startsWith("NOTE") || first.startsWith("STYLE")) {
                continue
            }

            val timeIdx = lines.indexOfFirst { timePattern.containsMatchIn(it) }
            if (timeIdx < 0) continue
            val match = timePattern.find(lines[timeIdx]) ?: continue

            val startMs = toMillis(
                hours = match.groupValues[1].toLongOrNull() ?: 0L,
                minutes = match.groupValues[2].toLongOrNull() ?: 0L,
                seconds = match.groupValues[3].toLongOrNull() ?: 0L,
                millis = match.groupValues[4].toLongOrNull() ?: 0L,
            )
            val endMs = toMillis(
                hours = match.groupValues[5].toLongOrNull() ?: 0L,
                minutes = match.groupValues[6].toLongOrNull() ?: 0L,
                seconds = match.groupValues[7].toLongOrNull() ?: 0L,
                millis = match.groupValues[8].toLongOrNull() ?: 0L,
            )
            if (endMs <= startMs) continue

            val text = lines.drop(timeIdx + 1)
                .joinToString("\n") { line -> line.replace(tagRegex, "").trim() }
                .trim()
            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = text))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    fun activeCueAt(
        cues: List<SubtitleCue>,
        positionMs: Long,
        offsetMs: Long = 0L,
    ): String? {
        val adjustedPos = positionMs - offsetMs
        if (cues.isEmpty() || adjustedPos < 0L) return null
        var low = 0
        var high = cues.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= adjustedPos) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        // Check backward nearby overlapping cues just in case.
        for (idx in candidate downTo maxOf(0, candidate - 4)) {
            val cue = cues[idx]
            if (adjustedPos in cue.startMs..cue.endMs) {
                return cue.text
            }
        }
        return null
    }

    private fun toMillis(hours: Long, minutes: Long, seconds: Long, millis: Long): Long =
        ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
}

package org.every.nook.api.application.processing

import java.time.Duration
import java.time.Instant

enum class ParsingProgressStage(
    val startPercent: Int,
    private val endPercent: Int,
    private val estimatedDuration: Duration,
) {
    CONTENT_FETCH(15, 25, Duration.ofSeconds(20)),
    CONTENT_COVER_TITLE(25, 32, Duration.ofSeconds(10)),
    CONTENT_INFERENCE(32, 42, Duration.ofSeconds(15)),
    CONTENT_SAVE(42, 45, Duration.ofSeconds(5)),
    PLACE_TEXT_CLUES(60, 66, Duration.ofSeconds(15)),
    PLACE_TEXT_RESOLUTION(66, 74, Duration.ofSeconds(20)),
    PLACE_IMAGE_OCR(74, 82, Duration.ofSeconds(25)),
    PLACE_IMAGE_CLUES(82, 87, Duration.ofSeconds(15)),
    PLACE_IMAGE_RESOLUTION(87, 93, Duration.ofSeconds(20)),
    TITLE_FINALIZATION(93, 98, Duration.ofSeconds(10)),
    PLACE_SAVE(98, 99, Duration.ofSeconds(5)),
    ;

    fun percentAt(startedAt: Instant?, now: Instant, floorPercent: Int): Int {
        if (startedAt == null || !now.isAfter(startedAt)) {
            return maxOf(floorPercent, startPercent)
        }
        val estimatedMillis = estimatedDuration.toMillis()
        val elapsedMillis = Duration.between(startedAt, now).toMillis()
        val interpolated = startPercent + ((endPercent - startPercent) * elapsedMillis / estimatedMillis).toInt()
        return maxOf(floorPercent, interpolated.coerceAtMost(endPercent))
    }
}

data class ParsingProgress(val stage: ParsingProgressStage?, val stageStartedAt: Instant?, val persistedPercent: Int) {
    fun percentAt(now: Instant): Int = stage?.percentAt(stageStartedAt, now, persistedPercent) ?: persistedPercent
}

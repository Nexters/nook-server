package org.every.nook.api.application.post.model

import org.every.nook.api.application.processing.ParsingProgress
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import java.time.Duration
import java.time.Instant

data class PostProcessingView(
    val status: PostProcessingStatusView,
    val stage: PostProcessingStageView?,
    val processingPercent: Int = defaultPercent(status, stage),
) {
    companion object {
        fun from(
            contentStatus: PostContentParsingStatus,
            placeStatus: PlaceParsingStatus?,
            contentStartedAt: Instant? = null,
            placeStartedAt: Instant? = null,
            now: Instant? = null,
            contentProgress: ParsingProgress? = null,
            placeProgress: ParsingProgress? = null,
        ): PostProcessingView = when (contentStatus) {
            PostContentParsingStatus.PENDING -> PostProcessingView(
                PostProcessingStatusView.PENDING,
                PostProcessingStageView.CONTENT,
                processingPercent = contentProgress.persistedOr(CONTENT_PENDING_PERCENT),
            )

            PostContentParsingStatus.PROCESSING -> PostProcessingView(
                PostProcessingStatusView.PROCESSING,
                PostProcessingStageView.CONTENT,
                processingPercent = contentProgress?.percentAt(requireNotNull(now)) ?: progressingPercent(
                    startedAt = contentStartedAt,
                    now = now,
                    range = PercentRange(
                        start = CONTENT_PROCESSING_START_PERCENT,
                        end = CONTENT_PROCESSING_END_PERCENT,
                        estimatedDuration = CONTENT_ESTIMATED_DURATION,
                    ),
                ),
            )

            PostContentParsingStatus.FAILED -> PostProcessingView(
                PostProcessingStatusView.FAILED,
                PostProcessingStageView.CONTENT,
            )

            PostContentParsingStatus.COMPLETED -> fromPlace(
                status = placeStatus,
                placeStartedAt = placeStartedAt,
                now = now,
                progress = placeProgress,
            )
        }

        private fun fromPlace(
            status: PlaceParsingStatus?,
            placeStartedAt: Instant?,
            now: Instant?,
            progress: ParsingProgress?,
        ): PostProcessingView = when (status) {
            null,
            PlaceParsingStatus.PENDING,
            -> PostProcessingView(
                PostProcessingStatusView.PENDING,
                PostProcessingStageView.PLACE,
                processingPercent = progress.persistedOr(PLACE_PENDING_PERCENT),
            )

            PlaceParsingStatus.PROCESSING ->
                PostProcessingView(
                    PostProcessingStatusView.PROCESSING,
                    PostProcessingStageView.PLACE,
                    processingPercent = progress?.percentAt(requireNotNull(now)) ?: progressingPercent(
                        startedAt = placeStartedAt,
                        now = now,
                        range = PercentRange(
                            start = PLACE_PROCESSING_START_PERCENT,
                            end = PLACE_PROCESSING_END_PERCENT,
                            estimatedDuration = PLACE_ESTIMATED_DURATION,
                        ),
                    ),
                )

            PlaceParsingStatus.COMPLETED -> PostProcessingView(PostProcessingStatusView.COMPLETED, null)

            PlaceParsingStatus.FAILED ->
                PostProcessingView(PostProcessingStatusView.COMPLETED, null)
        }

        private fun progressingPercent(startedAt: Instant?, now: Instant?, range: PercentRange): Int {
            if (startedAt == null || now == null || !now.isAfter(startedAt)) {
                return range.start
            }
            val elapsedMillis = Duration.between(startedAt, now).toMillis()
            val estimatedMillis = range.estimatedDuration.toMillis()
            if (estimatedMillis <= 0) {
                return range.end
            }
            val progress = elapsedMillis.toDouble() / estimatedMillis
            val percent = range.start + ((range.end - range.start) * progress).toInt()
            return percent.coerceIn(range.start, range.end)
        }

        private data class PercentRange(val start: Int, val end: Int, val estimatedDuration: Duration)

        private fun ParsingProgress?.persistedOr(fallback: Int): Int = this?.persistedPercent ?: fallback

        private val CONTENT_ESTIMATED_DURATION = Duration.ofSeconds(CONTENT_ESTIMATED_SECONDS)
        private val PLACE_ESTIMATED_DURATION = Duration.ofSeconds(PLACE_ESTIMATED_SECONDS)
    }
}

private fun defaultPercent(status: PostProcessingStatusView, stage: PostProcessingStageView?): Int = when (status) {
    PostProcessingStatusView.PENDING -> when (stage) {
        PostProcessingStageView.CONTENT -> CONTENT_PENDING_PERCENT
        PostProcessingStageView.PLACE -> PLACE_PENDING_PERCENT
        null -> COMPLETED_PERCENT
    }

    PostProcessingStatusView.PROCESSING -> when (stage) {
        PostProcessingStageView.CONTENT -> CONTENT_PROCESSING_START_PERCENT
        PostProcessingStageView.PLACE -> PLACE_PROCESSING_START_PERCENT
        null -> COMPLETED_PERCENT
    }

    PostProcessingStatusView.COMPLETED -> COMPLETED_PERCENT

    PostProcessingStatusView.FAILED -> when (stage) {
        PostProcessingStageView.CONTENT -> CONTENT_PROCESSING_END_PERCENT
        PostProcessingStageView.PLACE -> PLACE_PROCESSING_END_PERCENT
        null -> COMPLETED_PERCENT
    }
}

private const val CONTENT_PENDING_PERCENT = 5
private const val CONTENT_PROCESSING_START_PERCENT = 15
private const val CONTENT_PROCESSING_END_PERCENT = 45
private const val CONTENT_ESTIMATED_SECONDS = 45L
private const val PLACE_PENDING_PERCENT = 50
private const val PLACE_PROCESSING_START_PERCENT = 60
private const val PLACE_PROCESSING_END_PERCENT = 95
private const val PLACE_ESTIMATED_SECONDS = 90L
private const val COMPLETED_PERCENT = 100

enum class PostProcessingStatusView {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class PostProcessingStageView {
    CONTENT,
    PLACE,
}

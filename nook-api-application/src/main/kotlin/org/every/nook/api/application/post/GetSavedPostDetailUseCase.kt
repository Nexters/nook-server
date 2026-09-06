package org.every.nook.api.application.post

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.port.SavedPostQueryPort

class GetSavedPostDetailUseCase(
    private val savedPostQueryPort: SavedPostQueryPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(query: Query): SavedPostDetail {
        val result = savedPostQueryPort.findDetail(query.userId, query.postId) ?: throw PostNotFoundException()
        analyticsRecorder.record(
            UserAnalyticsRecord(
                eventName = UserAnalyticsEventName.POST_VIEW,
                memberId = query.userId,
                targetType = UserAnalyticsTargetType.POST,
                targetId = query.postId,
            ),
        )
        return result
    }

    data class Query(val userId: Long, val postId: Long)
}

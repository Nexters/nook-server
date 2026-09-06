package org.every.nook.api.application.post

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort

class ListSavedPostsUseCase(
    private val savedPostQueryPort: SavedPostQueryPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(query: Query): SavedPostPage {
        val result = savedPostQueryPort.findAll(query.userId, query.page, query.size)
        if (query.page == 0) {
            analyticsRecorder.record(
                UserAnalyticsRecord(UserAnalyticsEventName.ARCHIVE_VIEW, query.userId),
            )
        }
        return result
    }

    data class Query(val userId: Long, val page: Int, val size: Int)
}

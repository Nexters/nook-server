package org.every.nook.api.application.post

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.model.PostProcessingView
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.domain.post.Post

class CreatePostUseCase(
    private val groupOwnershipPort: GroupOwnershipPort,
    private val postSourceResolver: PostSourceResolver,
    private val findExistingPostPort: FindExistingPostPort,
    private val reusePostPort: ReusePostPort,
    private val createPostPort: CreatePostPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(command: Command): Result {
        val groupIds = command.groupIds.toSet()
        validateGroups(command.userId, groupIds)
        val source = postSourceResolver.resolve(command.url) ?: throw UnsupportedPostUrlException()
        val existingPost = findExistingPostPort.find(source)
        val savedPost = if (existingPost != null) {
            reusePostPort.reuse(
                userId = command.userId,
                source = source,
                memo = command.memo,
                groupIds = groupIds,
            )
        } else {
            createPostPort.create(
                userId = command.userId,
                post = Post(
                    source = source,
                    canonicalUrl = command.url.toCanonicalUrl(),
                ),
                memo = command.memo,
                groupIds = groupIds,
            )
        }

        if (savedPost.saveChanged) {
            analyticsRecorder.record(
                UserAnalyticsRecord(
                    eventName = UserAnalyticsEventName.POST_SAVE,
                    memberId = command.userId,
                    targetType = UserAnalyticsTargetType.POST,
                    targetId = savedPost.postId,
                ),
            )
        }
        return savedPost.toResult()
    }

    private fun CreatedPost.toResult(): Result {
        val processing = PostProcessingView.from(contentParsingStatus, placeParsingStatus)
        return Result(
            postId = postId,
            placeParsingStatus = placeParsingStatus
                ?.let(PlaceParsingStatusView::from)
                ?: PlaceParsingStatusView.PENDING,
            processingStatus = processing.status,
            processingStage = processing.stage,
            processingPercent = processing.processingPercent,
        )
    }

    private fun validateGroups(userId: Long, groupIds: Set<Long>) {
        if (groupIds.isEmpty()) {
            throw InvalidGroupException(IllegalArgumentException("At least one group is required"))
        }
        if (!groupOwnershipPort.ownsAll(userId, groupIds)) {
            throw GroupNotFoundException()
        }
    }

    private fun String.toCanonicalUrl(): String = substringBefore('?').trimEnd('/') + "/"

    data class Command(val userId: Long, val url: String, val memo: String? = null, val groupIds: List<Long>)

    data class Result(
        val postId: Long,
        val placeParsingStatus: PlaceParsingStatusView,
        val processingStatus: PostProcessingStatusView = PostProcessingStatusView.PENDING,
        val processingStage: PostProcessingStageView? = PostProcessingStageView.CONTENT,
        val processingPercent: Int = 5,
    )
}

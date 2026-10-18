package org.every.nook.api.application.post.share

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostMedia
import java.math.BigDecimal
import java.time.Instant

data class PostShareLink(val token: String, val sharedAt: Instant)

data class PostShareView(val post: PostShareSnapshot, val sharedAt: Instant, val viewerSavedPostId: Long?)

data class SavePostShareResult(val postId: Long, val alreadySaved: Boolean)

/** Only public content is snapshotted: personal memos, groups and bookmarks have no fields here. */
data class PostShareSnapshot(
    val title: String?,
    val body: String?,
    val authorIdentifier: String?,
    val canonicalUrl: String,
    val publishedAt: Instant?,
    val media: List<SavedPostMedia>,
    val hashtags: List<String>,
    val places: List<PostSharePlace>,
) {
    companion object {
        fun from(detail: SavedPostDetail) = PostShareSnapshot(
            detail.title,
            detail.body,
            detail.authorIdentifier,
            detail.canonicalUrl,
            detail.publishedAt,
            detail.media,
            detail.hashtags,
            detail.places.map { place ->
                PostSharePlace(
                    place.id, place.provider, place.externalPlaceId, place.name, place.address,
                    place.latitude, place.longitude, place.category, place.phoneNumber,
                    place.thumbnailUrl, place.thumbnailParsingStatus, place.tags, place.sequence,
                )
            },
        )
    }
}

data class PostSharePlace(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val thumbnailUrl: String?,
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView,
    val tags: List<String>,
    val sequence: Int,
)

interface PostSharePort {
    fun issue(ownerId: Long, savedPostId: Long): PostShareLink
    fun find(token: String, viewerId: Long?): PostShareView
    fun save(userId: Long, token: String, groupIds: Set<Long>): SavePostShareResult
}

class IssuePostShareUseCase(private val port: PostSharePort) {
    operator fun invoke(ownerId: Long, savedPostId: Long): PostShareLink {
        require(ownerId > 0 && savedPostId > 0)
        return port.issue(ownerId, savedPostId)
    }
}

class GetPostShareUseCase(private val port: PostSharePort) {
    operator fun invoke(token: String, viewerId: Long?): PostShareView = port.find(token, viewerId)
}

class SavePostShareUseCase(private val port: PostSharePort) {
    operator fun invoke(userId: Long, token: String, groupIds: List<Long>): SavePostShareResult {
        require(userId > 0 && groupIds.all { it > 0 })
        if (groupIds.isEmpty()) throw InvalidGroupException(IllegalArgumentException("At least one group is required"))
        return port.save(userId, token, groupIds.toSet())
    }
}

class PostShareException(code: PostShareError) : NookException(code)

enum class PostShareError(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    NOT_FOUND("POST_SHARE_NOT_FOUND", "공유 링크를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    EXPIRED("POST_SHARE_EXPIRED", "원본 게시물이 삭제되어 만료된 공유 링크입니다.", ErrorType.GONE),
    NOT_READY("POST_SHARE_NOT_READY", "저장 처리가 완료된 게시물만 공유할 수 있습니다.", ErrorType.CONFLICT),
}

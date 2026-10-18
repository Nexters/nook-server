package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.post.share.GetPostShareUseCase
import org.every.nook.api.application.post.share.PostSharePlace
import org.every.nook.api.application.post.share.PostShareSnapshot
import org.every.nook.api.presentation.post.response.SavedPostMediaResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.every.nook.api.presentation.response.toSeoulOffsetDateTime
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/public/v1/post-shares")
@Tag(name = "Post Share")
class PublicPostShareController(private val getShare: GetPostShareUseCase) {
    @GetMapping("/{token}")
    @Operation(summary = "게시물 공유 스냅샷 공개 조회; 로그인 시 내 저장 여부도 반환")
    fun get(
        @PathVariable token: String,
        @Parameter(
            hidden = true,
        ) authentication: Authentication?,
    ): ApiResponse<Response> {
        val viewerId = authentication?.takeIf { it.isAuthenticated }?.name?.toLongOrNull()?.takeIf { it > 0 }
        val view = getShare(token, viewerId)
        return ApiResponse.success(
            Response(SnapshotResponse.from(view.post), view.sharedAt.toSeoulOffsetDateTime(), view.viewerSavedPostId),
        )
    }

    data class Response(val post: SnapshotResponse, val sharedAt: OffsetDateTime, val viewerSavedPostId: Long?)

    data class SnapshotResponse(
        val title: String?,
        val body: String?,
        val authorIdentifier: String?,
        val canonicalUrl: String,
        val publishedAt: OffsetDateTime?,
        val media: List<SavedPostMediaResponse>,
        val hashtags: List<String>,
        val places: List<PostSharePlace>,
    ) {
        companion object {
            fun from(snapshot: PostShareSnapshot) = SnapshotResponse(
                snapshot.title,
                snapshot.body,
                snapshot.authorIdentifier,
                snapshot.canonicalUrl,
                snapshot.publishedAt?.toSeoulOffsetDateTime(),
                snapshot.media.map(SavedPostMediaResponse::from),
                snapshot.hashtags,
                snapshot.places,
            )
        }
    }
}

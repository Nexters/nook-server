package org.every.nook.api.infrastructure.persistence.postshare

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.application.post.share.PostShareError
import org.every.nook.api.application.post.share.PostShareException
import org.every.nook.api.application.post.share.PostShareLink
import org.every.nook.api.application.post.share.PostSharePort
import org.every.nook.api.application.post.share.PostShareSnapshot
import org.every.nook.api.application.post.share.PostShareView
import org.every.nook.api.application.post.share.SavePostShareResult
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Component
class PostSharePersistenceAdapter(
    private val links: PostShareLinkJpaRepository,
    private val savedPosts: UserSavedPostJpaRepository,
    private val savedPostLocks: UserSavedPostLockJpaRepository,
    private val details: SavedPostQueryPort,
    private val copy: PostShareCopyAdapter,
    private val mapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : PostSharePort {
    @Transactional
    override fun issue(ownerId: Long, savedPostId: Long): PostShareLink {
        val saved = savedPostLocks.findByIdAndUserIdForUpdate(savedPostId, ownerId) ?: throw PostNotFoundException()
        val detail = findDetail(ownerId, savedPostId)
        if (detail.processingStatus != PostProcessingStatusView.COMPLETED) {
            throw PostShareException(PostShareError.NOT_READY)
        }
        val token = PostShareTokens.issue()
        val capturedAt = clock.instant()
        links.save(
            PostShareLinkEntity(
                tokenHash = PostShareTokens.hash(token),
                sourceSavedPostId = savedPostId,
                sourcePostId = saved.postId,
                sourceGeneration = saved.shareGeneration,
                snapshot = mapper.writeValueAsString(PostShareSnapshot.from(detail)),
                capturedAt = capturedAt,
            ),
        )
        return PostShareLink(token, capturedAt)
    }

    @Transactional(readOnly = true)
    override fun find(token: String, viewerId: Long?): PostShareView {
        val link = findLink(token)
        requirePostShareSource(link, savedPosts.findById(link.sourceSavedPostId).orElse(null))
        val viewerSaved = viewerId?.let { savedPosts.findByUserIdAndPostId(it, link.sourcePostId) }
        return PostShareView(readSnapshot(link), link.capturedAt, viewerSaved?.id)
    }

    @Transactional
    override fun save(userId: Long, token: String, groupIds: Set<Long>): SavePostShareResult {
        val link = findLink(token)
        return copy.save(userId, link, readSnapshot(link), groupIds)
    }

    private fun findLink(token: String): PostShareLinkEntity {
        if (!PostShareTokens.valid(token)) throw PostShareException(PostShareError.NOT_FOUND)
        return links.findByTokenHash(PostShareTokens.hash(token)) ?: throw PostShareException(PostShareError.NOT_FOUND)
    }

    private fun findDetail(ownerId: Long, savedPostId: Long) =
        details.findDetail(ownerId, savedPostId) ?: throw PostNotFoundException()

    private fun readSnapshot(link: PostShareLinkEntity): PostShareSnapshot =
        mapper.readValue(link.snapshot, PostShareSnapshot::class.java)
}

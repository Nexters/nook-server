package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.post.SavedPostRecovery
import org.every.nook.api.application.post.SavedPostRecoveryPort
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PostProcessingView
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.processing.PostProcessingDispositionStore
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class SavedPostRecoveryAdapter(
    private val savedPosts: UserSavedPostJpaRepository,
    private val groups: GroupPostJpaRepository,
    private val contentJobs: PostContentParsingJobJpaRepository,
    private val placeJobs: PlaceParsingJobJpaRepository,
    private val disposition: PostProcessingDispositionStore,
    private val clock: Clock = Clock.systemUTC(),
) : SavedPostRecoveryPort {
    @Transactional(readOnly = true)
    override fun find(userId: Long, savedPostId: Long): SavedPostRecovery {
        val saved = ownedPost(userId, savedPostId)
        val job = contentJobs.findByPostId(saved.postId) ?: throw PostNotFoundException()
        return result(savedPostId, job)
    }

    @Transactional
    override fun request(userId: Long, savedPostId: Long): SavedPostRecovery {
        val saved = ownedPost(userId, savedPostId)
        // The shared content job lock serializes recovery requests from all users of this source post.
        val job = contentJobs.findByPostIdForUpdate(saved.postId) ?: throw PostNotFoundException()
        if (job.status != PostContentParsingStatus.FAILED) return result(savedPostId, job)
        if (groups.findActiveSavedPostIdsWithActiveGroup(listOf(savedPostId)).isEmpty()) {
            throw GroupNotFoundException()
        }
        disposition.requireOpen(saved.postId)
        job.status = PostContentParsingStatus.PENDING
        job.retryAttemptCount = 0
        job.nextAttemptAt = clock.instant()
        // Keep the total attempt counter for stale execution fencing and failure history.
        // The durable worker dispatcher picks up PENDING jobs after this transaction commits.
        return result(savedPostId, job).copy(requested = true)
    }

    private fun ownedPost(userId: Long, savedPostId: Long): UserSavedPostEntity =
        savedPosts.findByIdAndUserId(savedPostId, userId) ?: throw PostNotFoundException()

    private fun result(savedPostId: Long, job: PostContentParsingJobEntity) = SavedPostRecovery(
        postId = savedPostId,
        processing = PostProcessingView.from(job.status, placeJobs.findByPostId(job.postId)?.status),
    )
}

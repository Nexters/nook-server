package org.every.nook.api.infrastructure.persistence.post

import org.every.nook.api.application.post.port.UpdatePostMediaUrlPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdatePostMediaUrlPersistenceAdapter(private val mediaRepository: PostMediaJpaRepository) :
    UpdatePostMediaUrlPort {
    @Transactional
    override fun update(postId: Long, sequence: Int, sourceUrl: String, storedUrl: String) {
        mediaRepository.findByPostIdAndSequence(postId, sequence)
            ?.updateUrlIfCurrent(sourceUrl, storedUrl)
    }
}

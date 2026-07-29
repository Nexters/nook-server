package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostPlaceJpaRepository : JpaRepository<PostPlaceEntity, Long> {
    fun findByPostIdAndPlaceId(postId: Long, placeId: Long): PostPlaceEntity?

    fun findAllByPostIdOrderBySequenceAsc(postId: Long): List<PostPlaceEntity>

    fun findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds: Collection<Long>): List<PostPlaceEntity>
}

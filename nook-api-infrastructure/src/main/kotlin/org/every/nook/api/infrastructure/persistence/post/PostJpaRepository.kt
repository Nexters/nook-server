package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostJpaRepository : JpaRepository<PostEntity, Long> {
    fun findBySourceTypeAndExternalPostId(sourceType: String, externalPostId: String): PostEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
            SELECT post
            FROM PostEntity post
            WHERE post.sourceType = :sourceType
              AND post.externalPostId = :externalPostId
        """,
    )
    fun findBySourceForUpdate(
        @Param("sourceType") sourceType: String,
        @Param("externalPostId") externalPostId: String,
    ): PostEntity?
}

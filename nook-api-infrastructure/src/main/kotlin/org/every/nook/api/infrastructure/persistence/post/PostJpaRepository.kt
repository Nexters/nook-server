package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostJpaRepository : JpaRepository<PostEntity, Long> {
    fun findBySourceTypeAndExternalPostId(sourceType: String, externalPostId: String): PostEntity?
}

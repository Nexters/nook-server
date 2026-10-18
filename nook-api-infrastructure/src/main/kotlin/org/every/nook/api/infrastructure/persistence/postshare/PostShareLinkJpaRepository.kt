package org.every.nook.api.infrastructure.persistence.postshare

import org.springframework.data.jpa.repository.JpaRepository

interface PostShareLinkJpaRepository : JpaRepository<PostShareLinkEntity, Long> {
    fun findByTokenHash(tokenHash: String): PostShareLinkEntity?
}

package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostMediaJpaRepository : JpaRepository<PostMediaEntity, Long>

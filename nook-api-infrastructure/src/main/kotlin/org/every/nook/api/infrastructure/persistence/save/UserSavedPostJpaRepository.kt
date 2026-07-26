package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.jpa.repository.JpaRepository

interface UserSavedPostJpaRepository : JpaRepository<UserSavedPostEntity, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): UserSavedPostEntity?
}

package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.JpaRepository

interface GroupPostJpaRepository : JpaRepository<GroupPostEntity, Long> {
    fun countByGroupId(groupId: Long): Long

    fun findAllByUserSavedPostId(userSavedPostId: Long): List<GroupPostEntity>

    fun deleteAllByUserSavedPostId(userSavedPostId: Long)
}

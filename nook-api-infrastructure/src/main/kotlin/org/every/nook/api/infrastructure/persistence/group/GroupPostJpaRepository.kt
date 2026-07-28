package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface GroupPostJpaRepository : JpaRepository<GroupPostEntity, Long> {
    fun countByGroupId(groupId: Long): Long

    fun findAllByUserSavedPostId(userSavedPostId: Long): List<GroupPostEntity>

    @Modifying
    @Query("DELETE FROM GroupPostEntity groupPost WHERE groupPost.groupId = :groupId")
    fun deleteAllByGroupId(groupId: Long): Int

    fun deleteAllByUserSavedPostId(userSavedPostId: Long)
}

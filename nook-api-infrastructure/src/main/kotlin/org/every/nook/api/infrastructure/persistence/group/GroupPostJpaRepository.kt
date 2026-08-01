package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface GroupPostJpaRepository : JpaRepository<GroupPostEntity, Long> {
    fun countByGroupId(groupId: Long): Long

    fun findAllByUserSavedPostId(userSavedPostId: Long): List<GroupPostEntity>

    fun findAllByUserSavedPostIdIn(userSavedPostIds: Collection<Long>): List<GroupPostEntity>

    @Modifying
    @Query("UPDATE GroupPostEntity groupPost SET groupPost.deletedAt = :now WHERE groupPost.groupId = :groupId")
    fun softDeleteAllByGroupId(groupId: Long, now: Instant): Int

    @Modifying
    @Query(
        "UPDATE GroupPostEntity groupPost SET groupPost.deletedAt = :now " +
            "WHERE groupPost.userSavedPostId = :userSavedPostId",
    )
    fun softDeleteAllByUserSavedPostId(userSavedPostId: Long, now: Instant): Int

    @Modifying
    @Query(
        value = """
            UPDATE group_posts
            SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE group_id = :groupId AND user_saved_post_id = :userSavedPostId
        """,
        nativeQuery = true,
    )
    fun restore(groupId: Long, userSavedPostId: Long): Int
}

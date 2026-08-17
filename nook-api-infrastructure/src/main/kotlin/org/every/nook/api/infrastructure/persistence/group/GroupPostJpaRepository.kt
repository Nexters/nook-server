package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GroupPostJpaRepository : JpaRepository<GroupPostEntity, Long> {
    fun existsByGroupIdAndUserSavedPostId(groupId: Long, userSavedPostId: Long): Boolean

    @Query(
        value = """
            SELECT COUNT(group_post.id)
            FROM group_posts group_post
            INNER JOIN user_saved_posts saved_post
                ON saved_post.id = group_post.user_saved_post_id
                AND saved_post.deleted_at IS NULL
            INNER JOIN post_content_parsing_jobs content_parsing_job
                ON content_parsing_job.post_id = saved_post.post_id
                AND content_parsing_job.status <> 'FAILED'
            WHERE group_post.group_id = :groupId
              AND group_post.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun countByGroupId(@Param("groupId") groupId: Long): Long

    fun findAllByUserSavedPostId(userSavedPostId: Long): List<GroupPostEntity>

    fun findAllByUserSavedPostIdIn(userSavedPostIds: Collection<Long>): List<GroupPostEntity>

    @Query(
        value = """
            SELECT group_post.user_saved_post_id
            FROM group_posts group_post
            WHERE group_post.group_id = :groupId
              AND group_post.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findActiveSavedPostIdsByGroupId(@Param("groupId") groupId: Long): List<Long>

    @Query(
        value = """
            SELECT DISTINCT group_post.user_saved_post_id
            FROM group_posts group_post
            INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
            WHERE group_post.user_saved_post_id IN (:savedPostIds)
              AND group_post.deleted_at IS NULL
              AND user_group.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findActiveSavedPostIdsWithActiveGroup(@Param("savedPostIds") savedPostIds: Collection<Long>): List<Long>

    @Modifying
    @Query(
        "UPDATE GroupPostEntity groupPost SET groupPost.deletedAt = :now " +
            "WHERE groupPost.groupId = :groupId AND groupPost.deletedAt IS NULL",
    )
    fun softDeleteAllByGroupId(groupId: Long, now: Instant): Int

    @Modifying
    @Query(
        "UPDATE GroupPostEntity groupPost SET groupPost.deletedAt = :now " +
            "WHERE groupPost.userSavedPostId = :userSavedPostId",
    )
    fun softDeleteAllByUserSavedPostId(userSavedPostId: Long, now: Instant): Int

    @Modifying
    @Query(
        "UPDATE GroupPostEntity groupPost SET groupPost.deletedAt = :now " +
            "WHERE groupPost.userSavedPostId IN (:userSavedPostIds) AND groupPost.deletedAt IS NULL",
    )
    fun softDeleteAllByUserSavedPostIdIn(userSavedPostIds: Collection<Long>, now: Instant): Int

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

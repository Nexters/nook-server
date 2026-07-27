package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.domain.group.GroupColor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface GroupJpaRepository : JpaRepository<GroupEntity, Long> {
    @Query(
        value = """
            SELECT
                user_group.id AS id,
                user_group.name AS name,
                user_group.color AS color,
                COUNT(group_post.id) AS postCount
            FROM user_groups user_group
            LEFT JOIN group_posts group_post ON group_post.group_id = user_group.id
            WHERE user_group.user_id = :userId
            GROUP BY user_group.id, user_group.name, user_group.color
            ORDER BY user_group.id
        """,
        nativeQuery = true,
    )
    fun findAllSummaries(userId: Long): List<GroupSummaryProjection>

    fun findByIdAndUserId(id: Long, userId: Long): GroupEntity?

    fun findAllByUserIdAndIdIn(userId: Long, ids: Set<Long>): List<GroupEntity>

    fun existsByIdAndUserId(id: Long, userId: Long): Boolean

    fun existsByUserIdAndName(userId: Long, name: String): Boolean

    fun existsByUserIdAndNameAndIdNot(userId: Long, name: String, id: Long): Boolean

    @Modifying
    @Transactional
    @Query(
        """
            UPDATE GroupEntity userGroup
            SET userGroup.name = :name, userGroup.color = :color
            WHERE userGroup.id = :id AND userGroup.userId = :userId
        """,
    )
    fun updateByIdAndUserId(id: Long, userId: Long, name: String, color: GroupColor): Int

    fun deleteByIdAndUserId(id: Long, userId: Long): Long
}

interface GroupSummaryProjection {
    val id: Long
    val name: String
    val color: GroupColor
    val postCount: Long
}

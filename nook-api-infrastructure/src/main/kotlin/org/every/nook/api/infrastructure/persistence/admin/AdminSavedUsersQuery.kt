package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminSavedUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class AdminSavedUsersQuery(private val jdbc: NamedParameterJdbcTemplate) {
    fun findByPostIds(postIds: Collection<Long>): Map<Long, List<AdminSavedUser>> {
        if (postIds.isEmpty()) return emptyMap()
        return jdbc.query(SQL, mapOf("postIds" to postIds)) { row, _ ->
            row.getLong("postId") to AdminSavedUser(row.getLong("id"), row.getString("nickname"))
        }.groupBy({ it.first }, { it.second })
    }

    companion object {
        const val SQL = """
            SELECT DISTINCT saved_post.post_id AS postId, member.id AS id, member.nickname AS nickname
            FROM user_saved_posts saved_post
            INNER JOIN members member ON member.id = saved_post.user_id
            WHERE saved_post.post_id IN (:postIds)
              AND saved_post.deleted_at IS NULL
            ORDER BY saved_post.post_id, member.id
        """
    }
}

package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class PostProcessingDispositionStore(private val jdbc: NamedParameterJdbcTemplate) {
    fun enrich(posts: List<AdminPostRecovery>): List<AdminPostRecovery> {
        if (posts.isEmpty()) return posts
        val metadata = jdbc.queryForList(
            "SELECT id, title, processing_disposition, processing_disposition_reason, " +
                "processing_disposition_changed_at " +
                "FROM posts WHERE id IN (:ids)",
            mapOf("ids" to posts.map { it.postId }),
        ).associateBy { (it["id"] as Number).toLong() }
        return posts.map { post ->
            val row = metadata[post.postId]
            post.copy(
                title = row?.get("title") as String?,
                disposition = row?.get("processing_disposition") as String? ?: "OPEN",
                dispositionReason = row?.get("processing_disposition_reason") as String?,
                dispositionChangedAt = (
                    row?.get(
                        "processing_disposition_changed_at",
                    ) as java.sql.Timestamp?
                    )?.toInstant(),
            )
        }
    }

    fun lock(postId: Long): String = jdbc.queryForList(
        "SELECT processing_disposition FROM posts WHERE id = :id FOR UPDATE",
        mapOf("id" to postId),
    ).singleOrNull()?.get("processing_disposition") as String?
        ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)

    fun requireOpen(postId: Long) {
        if (lock(postId) != "OPEN") throw ParsingRecoveryException(ParsingRecoveryError.DISPOSITION_CONFLICT)
    }
}

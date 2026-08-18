package org.every.nook.api.application.admin

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

class ListAdminPostsUseCase(private val port: AdminPostQueryPort) {
    operator fun invoke(query: Query): AdminPage<AdminPostSummary> = port.listPosts(
        query.query?.trim()?.takeIf(String::isNotEmpty),
        query.parsingStatus,
        query.offset.validOffset(),
        query.limit.validLimit(),
    )

    data class Query(val query: String?, val parsingStatus: String?, val offset: Int, val limit: Int)
}

class GetAdminPostUseCase(private val port: AdminPostQueryPort) {
    operator fun invoke(postId: Long): AdminPostDetail = port.find(postId) ?: throw AdminPostNotFoundException()
}

class SearchAdminPlacesUseCase(private val port: AdminPlaceQueryPort) {
    operator fun invoke(query: String, limit: Int): List<AdminPlaceSummary> {
        require(query.isNotBlank()) { "Place search query must not be blank" }
        return port.search(query.trim(), limit.validLimit())
    }
}

class ReplaceAdminPostPlacesUseCase(private val port: AdminPostPlaceCorrectionPort) {
    operator fun invoke(command: Command): AdminPostDetail {
        require(command.postId > 0) { "Post id must be positive" }
        require(command.placeIds.all { it > 0 }) { "Place ids must be positive" }
        require(command.placeIds.distinct().size == command.placeIds.size) { "Place ids must be unique" }
        require(command.reason.isNotBlank()) { "Correction reason must not be blank" }
        return port.replace(
            AdminPostPlaceCorrectionPort.ReplaceCommand(
                postId = command.postId,
                placeIds = command.placeIds,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        ) ?: throw AdminPostNotFoundException()
    }

    data class Command(
        val postId: Long,
        val placeIds: List<Long>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

class ListAdminAuditLogsUseCase(private val port: AdminAuditLogPort) {
    operator fun invoke(targetType: String?, targetId: String?, offset: Int, limit: Int): AdminPage<AdminAuditLog> =
        port.listAuditLogs(targetType, targetId, offset.validOffset(), limit.validLimit())
}

enum class AdminErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    POST_NOT_FOUND("ADMIN_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
}

class AdminPostNotFoundException : NookException(AdminErrorCode.POST_NOT_FOUND)

private fun Int.validOffset(): Int = coerceAtLeast(0)

private fun Int.validLimit(): Int = coerceIn(1, MAX_ADMIN_PAGE_SIZE)

private const val MAX_ADMIN_PAGE_SIZE = 100

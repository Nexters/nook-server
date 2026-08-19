package org.every.nook.api.application.admin

interface AdminPostQueryPort {
    fun listPosts(query: String?, parsingStatus: String?, offset: Int, limit: Int): AdminPage<AdminPostSummary>

    fun find(postId: Long): AdminPostDetail?
}

interface AdminPlaceQueryPort {
    fun search(query: String, limit: Int): List<AdminPlaceSummary>

    fun listPlaces(query: String?, offset: Int, limit: Int): AdminPage<AdminPlaceSummary>

    fun findPlace(placeId: Long): AdminPlaceDetail?
}

interface AdminPlaceCorrectionPort {
    fun update(command: UpdateCommand): AdminPlaceDetail?

    data class UpdateCommand(
        val placeId: Long,
        val name: String,
        val address: String,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

interface AdminPostPlaceCorrectionPort {
    fun replace(command: ReplaceCommand): AdminPostDetail?

    data class ReplaceCommand(
        val postId: Long,
        val placeIds: List<Long>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

interface AdminAuditLogPort {
    fun listAuditLogs(targetType: String?, targetId: String?, offset: Int, limit: Int): AdminPage<AdminAuditLog>

    fun append(entry: Entry)

    data class Entry(
        val actor: AdminActor,
        val action: String,
        val targetType: String,
        val targetId: String,
        val reason: String,
        val beforeValue: String?,
        val afterValue: String?,
        val requestId: String?,
    )
}

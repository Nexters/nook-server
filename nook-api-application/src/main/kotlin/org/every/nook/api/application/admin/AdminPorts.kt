package org.every.nook.api.application.admin

import org.every.nook.api.domain.place.PlaceTagCategory

interface AdminPostQueryPort {
    fun listPosts(query: String?, parsingStatus: String?, offset: Int, limit: Int): AdminPage<AdminPostSummary>

    fun find(postId: Long): AdminPostDetail?
}

interface AdminPostCorrectionPort {
    fun update(command: UpdateCommand): AdminPostDetail?

    data class UpdateCommand(
        val postId: Long,
        val authorIdentifier: String?,
        val title: String?,
        val body: String?,
        val sourceLocationTag: String?,
        val hashtags: List<String>,
        val media: List<AdminPostMedia>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

interface AdminPostTitleRegenerationPort {
    fun findSource(postId: Long): Source?

    fun updateTitle(command: UpdateCommand): AdminPostDetail?

    data class Source(
        val postId: Long,
        val body: String?,
        val hashtags: List<String>,
        val sourceLocationTag: String?,
        val firstImageUrl: String?,
    )

    data class UpdateCommand(
        val postId: Long,
        val title: String?,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
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
        val city: String? = null,
        val category: String? = null,
        val phoneNumber: String? = null,
        val thumbnailUrl: String? = null,
        val photoUrls: List<String> = emptyList(),
        val representativeTags: List<String> = emptyList(),
        val openingHours: org.every.nook.api.application.place.PlaceOpeningHours? = null,
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

interface AdminPlaceTagCatalogPort {
    fun list(
        category: PlaceTagCategory?,
        enabled: Boolean?,
        offset: Int,
        limit: Int,
    ): AdminPage<AdminPlaceTagDefinition>

    fun update(command: UpdateCommand): AdminPlaceTagDefinition?

    fun create(command: CreateCommand): AdminPlaceTagDefinition

    fun reorder(command: ReorderCommand)

    fun deleteAndReplace(command: DeleteCommand): Boolean

    data class CreateCommand(
        val tagCode: String,
        val category: PlaceTagCategory,
        val displayName: String,
        val matchingKeywords: List<String>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )

    data class ReorderCommand(
        val tagCodes: List<String>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )

    data class DeleteCommand(
        val tagCode: String,
        val replacementTagCode: String,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )

    data class UpdateCommand(
        val tagCode: String,
        val category: PlaceTagCategory,
        val displayName: String,
        val matchingKeywords: List<String>,
        val enabled: Boolean,
        val sortOrder: Int,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

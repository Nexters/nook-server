package org.every.nook.api.application.admin

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.post.CoverTitleExtractor
import org.every.nook.api.application.post.PostTitleInference
import org.every.nook.api.application.post.resolvePostTitle
import org.every.nook.api.application.post.validatedCoverTitle
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceTagCategory
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import java.util.UUID

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

class GetAdminParsingPipelineUseCase(private val port: AdminParsingPipelinePort) {
    operator fun invoke(postId: Long?): AdminParsingPipeline {
        require(postId == null || postId > 0) { "Post id must be positive" }
        return port.get(postId)
    }
}

class UpdateAdminPostUseCase(private val port: AdminPostCorrectionPort) {
    operator fun invoke(command: Command): AdminPostDetail {
        require(command.postId > 0)
        require(command.title == null || command.title.length <= Post.MAX_TITLE_LENGTH)
        require(
            command.authorIdentifier == null ||
                command.authorIdentifier.length <= Post.MAX_AUTHOR_IDENTIFIER_LENGTH,
        )
        require(
            command.sourceLocationTag == null ||
                command.sourceLocationTag.length <= Post.MAX_SOURCE_LOCATION_TAG_LENGTH,
        )
        require(
            command.hashtags.size <= MAX_ADMIN_HASHTAG_COUNT && command.hashtags.all {
                it.isNotBlank() && it.length <= Post.MAX_HASHTAG_LENGTH
            },
        )
        require(
            command.media.size <= MAX_ADMIN_MEDIA_COUNT && command.media.all {
                it.mediaType in PostMedia.MediaType.entries.map(PostMedia.MediaType::name) &&
                    it.mediaUrl.isNotBlank() && it.mediaUrl.length <= PostMedia.MAX_MEDIA_URL_LENGTH
            },
        )
        require(command.reason.isNotBlank())
        return port.update(
            AdminPostCorrectionPort.UpdateCommand(
                command.postId,
                command.authorIdentifier?.trim()?.ifEmpty { null },
                command.title?.trim()?.ifEmpty { null },
                command.body?.trim()?.ifEmpty { null },
                command.sourceLocationTag?.trim()?.ifEmpty { null },
                command.hashtags.map(String::trim).distinct(),
                command.media,
                command.actor,
                command.reason.trim(),
                command.requestId,
            ),
        ) ?: throw AdminPostNotFoundException()
    }

    data class Command(
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

class RegenerateAdminPostTitleUseCase(
    private val port: AdminPostTitleRegenerationPort,
    private val imageTextExtractor: ImageTextExtractor,
    private val coverTitleExtractor: CoverTitleExtractor,
    private val titleInference: PostTitleInference,
) {
    operator fun invoke(command: Command): AdminPostDetail {
        require(command.postId > 0) { "Post id must be positive" }
        require(command.reason.isNotBlank()) { "Regeneration reason must not be blank" }
        val source = port.findSource(command.postId) ?: throw AdminPostNotFoundException()
        val coverTitle = source.firstImageUrl?.let { imageUrl ->
            runCatching {
                imageTextExtractor.extract(
                    ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, imageUrl))),
                ).firstOrNull { it.imageIndex == 1 }
                    ?.validatedCoverTitle(coverTitleExtractor)
            }.getOrNull()
        }
        val title = resolvePostTitle(source.body, coverTitle, null) ?: resolvePostTitle(
            source.body,
            coverTitle,
            titleInference.infer(PostTitleInference.Request(source.body, source.hashtags, source.sourceLocationTag)),
        )
        return port.updateTitle(
            AdminPostTitleRegenerationPort.UpdateCommand(
                postId = command.postId,
                title = title,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        ) ?: throw AdminPostNotFoundException()
    }

    data class Command(val postId: Long, val actor: AdminActor, val reason: String, val requestId: String?)
}

class SearchAdminPlacesUseCase(private val port: AdminPlaceQueryPort) {
    operator fun invoke(query: String, limit: Int): List<AdminPlaceSummary> {
        require(query.isNotBlank()) { "Place search query must not be blank" }
        return port.search(query.trim(), limit.validLimit())
    }
}

class ListAdminPlacesUseCase(private val port: AdminPlaceQueryPort) {
    operator fun invoke(query: Query): AdminPage<AdminPlaceSummary> = port.listPlaces(
        query.query?.trim()?.takeIf(String::isNotEmpty),
        query.offset.validOffset(),
        query.limit.validLimit(),
    )

    data class Query(val query: String?, val offset: Int, val limit: Int)
}

class GetAdminPlaceUseCase(private val port: AdminPlaceQueryPort) {
    operator fun invoke(placeId: Long): AdminPlaceDetail =
        port.findPlace(placeId) ?: throw AdminPlaceNotFoundException()
}

class UpdateAdminPlaceUseCase(
    private val port: AdminPlaceCorrectionPort,
    private val tagCatalogPort: PlaceTagCatalogQueryPort = PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
) {
    operator fun invoke(command: Command): AdminPlaceDetail {
        require(command.placeId > 0) { "Place id must be positive" }
        val name = command.name.trim()
        val address = command.address.trim()
        require(name.isNotEmpty()) { "Place name must not be blank" }
        require(name.length <= Place.MAX_NAME_LENGTH) { "Place name is too long" }
        require(address.isNotEmpty()) { "Place address must not be blank" }
        require(address.length <= Place.MAX_ADDRESS_LENGTH) { "Place address is too long" }
        require(command.city == null || command.city.length <= Place.MAX_CITY_LENGTH)
        require(command.category == null || command.category.length <= Place.MAX_CATEGORY_LENGTH)
        require(command.phoneNumber == null || command.phoneNumber.length <= Place.MAX_PHONE_NUMBER_LENGTH)
        require(command.thumbnailUrl == null || command.thumbnailUrl.length <= MAX_ADMIN_URL_LENGTH)
        require(command.photoUrls.size <= MAX_ADMIN_PLACE_PHOTO_COUNT)
        require(command.photoUrls.all { it.isNotBlank() && it.length <= MAX_ADMIN_URL_LENGTH })
        require(command.reason.isNotBlank()) { "Correction reason must not be blank" }
        val enabledTags = tagCatalogPort.findAll().filter { it.enabled }.map { it.tag }.toSet()
        val representativeTags = command.representativeTags
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_ADMIN_PLACE_TAG_COUNT)
        require(representativeTags.all(enabledTags::contains)) { "Only enabled place tags can be selected" }
        return port.update(
            AdminPlaceCorrectionPort.UpdateCommand(
                placeId = command.placeId,
                name = name,
                address = address,
                city = command.city?.trim()?.ifEmpty { null },
                category = command.category?.trim()?.ifEmpty { null },
                phoneNumber = command.phoneNumber?.trim()?.ifEmpty { null },
                thumbnailUrl = command.thumbnailUrl?.trim()?.ifEmpty { null },
                photoUrls = command.photoUrls.map(String::trim).filter(String::isNotEmpty).distinct(),
                representativeTags = representativeTags,
                openingHours = command.openingHours,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        ) ?: throw AdminPlaceNotFoundException()
    }

    data class Command(
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

class ListAdminPlaceTagsUseCase(private val port: AdminPlaceTagCatalogPort) {
    operator fun invoke(query: Query): AdminPage<AdminPlaceTagDefinition> = port.list(
        category = query.category?.takeIf(String::isNotBlank)?.let(PlaceTagCategory::valueOf),
        enabled = query.enabled,
        offset = query.offset.validOffset(),
        limit = query.limit.coerceIn(1, MAX_ADMIN_PLACE_TAG_CATALOG_SIZE),
    )

    data class Query(val category: String?, val enabled: Boolean?, val offset: Int, val limit: Int)
}

class UpdateAdminPlaceTagUseCase(private val port: AdminPlaceTagCatalogPort) {
    operator fun invoke(command: Command): AdminPlaceTagDefinition {
        val tagCode = command.tagCode
        val category = PlaceTagCategory.valueOf(command.category)
        val displayName = command.displayName.trim()
        val matchingKeywords = command.matchingKeywords.map(String::trim).filter(String::isNotEmpty).distinct()
        require(tagCode.isNotBlank()) { "Place tag id must not be blank" }
        require(displayName.isNotEmpty()) { "Display name must not be blank" }
        require(displayName.length <= MAX_ADMIN_PLACE_TAG_DISPLAY_NAME_LENGTH)
        require(matchingKeywords.isNotEmpty()) { "At least one matching keyword is required" }
        require(matchingKeywords.size <= MAX_ADMIN_PLACE_TAG_KEYWORD_COUNT)
        require(matchingKeywords.all { it.length <= MAX_ADMIN_PLACE_TAG_KEYWORD_LENGTH })
        require(command.sortOrder > 0) { "Sort order must be positive" }
        require(command.reason.isNotBlank()) { "Correction reason must not be blank" }
        return port.update(
            AdminPlaceTagCatalogPort.UpdateCommand(
                tagCode = tagCode,
                category = category,
                displayName = displayName,
                matchingKeywords = matchingKeywords,
                enabled = command.enabled,
                sortOrder = command.sortOrder,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        ) ?: throw AdminPlaceTagNotFoundException()
    }

    data class Command(
        val tagCode: String,
        val category: String,
        val displayName: String,
        val matchingKeywords: List<String>,
        val enabled: Boolean,
        val sortOrder: Int,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

class CreateAdminPlaceTagUseCase(private val port: AdminPlaceTagCatalogPort) {
    operator fun invoke(command: Command): AdminPlaceTagDefinition {
        val displayName = command.displayName.trim()
        val keywords = command.matchingKeywords.map(String::trim).filter(String::isNotEmpty).distinct()
        require(displayName.isNotEmpty() && displayName.length <= MAX_ADMIN_PLACE_TAG_DISPLAY_NAME_LENGTH)
        require(keywords.isNotEmpty() && keywords.size <= MAX_ADMIN_PLACE_TAG_KEYWORD_COUNT)
        require(keywords.all { it.length <= MAX_ADMIN_PLACE_TAG_KEYWORD_LENGTH })
        require(command.reason.isNotBlank())
        return port.create(
            AdminPlaceTagCatalogPort.CreateCommand(
                tagCode = "T_${UUID.randomUUID().toString().replace("-", "").take(CUSTOM_TAG_ID_SUFFIX_LENGTH)}",
                category = PlaceTagCategory.valueOf(command.category),
                displayName = displayName,
                matchingKeywords = keywords,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        )
    }

    data class Command(
        val category: String,
        val displayName: String,
        val matchingKeywords: List<String>,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

class ReorderAdminPlaceTagsUseCase(private val port: AdminPlaceTagCatalogPort) {
    operator fun invoke(command: Command) {
        require(command.tagCodes.isNotEmpty() && command.tagCodes.distinct().size == command.tagCodes.size)
        require(command.reason.isNotBlank())
        port.reorder(
            AdminPlaceTagCatalogPort.ReorderCommand(
                command.tagCodes,
                command.actor,
                command.reason.trim(),
                command.requestId,
            ),
        )
    }

    data class Command(val tagCodes: List<String>, val actor: AdminActor, val reason: String, val requestId: String?)
}

class DeleteAdminPlaceTagUseCase(private val port: AdminPlaceTagCatalogPort) {
    operator fun invoke(command: Command) {
        require(command.tagCode != command.replacementTagCode) { "Replacement tag must be different" }
        require(command.reason.isNotBlank())
        if (!port.deleteAndReplace(
                AdminPlaceTagCatalogPort.DeleteCommand(
                    command.tagCode,
                    command.replacementTagCode,
                    command.actor,
                    command.reason.trim(),
                    command.requestId,
                ),
            )
        ) {
            throw AdminPlaceTagNotFoundException()
        }
    }

    data class Command(
        val tagCode: String,
        val replacementTagCode: String,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}

enum class AdminErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    POST_NOT_FOUND("ADMIN_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    PLACE_NOT_FOUND("ADMIN_PLACE_NOT_FOUND", "장소를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    PLACE_TAG_NOT_FOUND("ADMIN_PLACE_TAG_NOT_FOUND", "장소 태그를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
}

class AdminPostNotFoundException : NookException(AdminErrorCode.POST_NOT_FOUND)

class AdminPlaceNotFoundException : NookException(AdminErrorCode.PLACE_NOT_FOUND)

class AdminPlaceTagNotFoundException : NookException(AdminErrorCode.PLACE_TAG_NOT_FOUND)

private fun Int.validOffset(): Int = coerceAtLeast(0)

private fun Int.validLimit(): Int = coerceIn(1, MAX_ADMIN_PAGE_SIZE)

private const val MAX_ADMIN_PAGE_SIZE = 100
private const val MAX_ADMIN_HASHTAG_COUNT = 30
private const val MAX_ADMIN_MEDIA_COUNT = 20
private const val MAX_ADMIN_PLACE_TAG_COUNT = 4
private const val MAX_ADMIN_PLACE_PHOTO_COUNT = 6
private const val MAX_ADMIN_URL_LENGTH = 2048
private const val MAX_ADMIN_PLACE_TAG_DISPLAY_NAME_LENGTH = 50
private const val MAX_ADMIN_PLACE_TAG_KEYWORD_COUNT = 20
private const val MAX_ADMIN_PLACE_TAG_KEYWORD_LENGTH = 100
private const val MAX_ADMIN_PLACE_TAG_CATALOG_SIZE = 500
private const val CUSTOM_TAG_ID_SUFFIX_LENGTH = 28

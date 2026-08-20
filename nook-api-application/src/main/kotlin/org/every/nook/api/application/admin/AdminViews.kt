package org.every.nook.api.application.admin

import org.every.nook.api.application.place.PlaceOpeningHours
import java.time.Instant

data class AdminPage<T>(val items: List<T>, val total: Long)

data class AdminPostSummary(
    val id: Long,
    val canonicalUrl: String,
    val authorIdentifier: String?,
    val title: String?,
    val contentParsingStatus: String,
    val placeParsingStatus: String?,
    val placeCount: Int,
    val savedUserCount: Long,
    val mappingReviewed: Boolean,
    val createdAt: Instant,
)

data class AdminPostDetail(
    val id: Long,
    val canonicalUrl: String,
    val authorIdentifier: String?,
    val title: String?,
    val body: String?,
    val sourceLocationTag: String?,
    val contentParsingStatus: String,
    val contentParsingFailureReason: String?,
    val placeParsingStatus: String?,
    val placeParsingFailureReason: String?,
    val savedUserCount: Long,
    val mappingReviewed: Boolean,
    val hashtags: List<String> = emptyList(),
    val media: List<AdminPostMedia> = emptyList(),
    val manuallyOverridden: Boolean = false,
    val places: List<AdminMappedPlace> = emptyList(),
)

data class AdminPostMedia(val mediaType: String, val mediaUrl: String, val sequence: Int)

data class AdminMappedPlace(
    val id: Long,
    val name: String,
    val address: String,
    val provider: String,
    val externalPlaceId: String,
    val thumbnailUrl: String? = null,
    val representativeTags: List<String> = emptyList(),
    val sequence: Int,
)

data class AdminPlaceSummary(
    val id: Long,
    val name: String,
    val address: String,
    val provider: String,
    val externalPlaceId: String,
    val thumbnailUrl: String?,
    val representativeTags: List<String>,
    val linkedPostCount: Long = 0,
    val affectedUserCount: Long = 0,
)

data class AdminPlaceDetail(
    val id: Long,
    val name: String,
    val address: String,
    val provider: String,
    val externalPlaceId: String,
    val city: String? = null,
    val latitude: String = "",
    val longitude: String = "",
    val category: String? = null,
    val phoneNumber: String? = null,
    val thumbnailUrl: String? = null,
    val photoUrls: List<String> = emptyList(),
    val representativeTags: List<String> = emptyList(),
    val openingHours: PlaceOpeningHours? = null,
    val linkedPostCount: Long = 0,
    val affectedUserCount: Long = 0,
    val posts: List<AdminLinkedPost> = emptyList(),
)

data class AdminLinkedPost(
    val id: Long,
    val title: String?,
    val authorIdentifier: String?,
    val canonicalUrl: String,
    val createdAt: Instant,
)

data class AdminAuditLog(
    val id: Long,
    val actorSubject: String,
    val actorEmail: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val beforeValue: String?,
    val afterValue: String?,
    val requestId: String?,
    val createdAt: Instant,
)

data class AdminPlaceTagDefinition(
    val id: String,
    val tagCode: String,
    val category: String,
    val displayName: String,
    val matchingKeywords: List<String>,
    val enabled: Boolean,
    val sortOrder: Int,
    val updatedAt: Instant,
)

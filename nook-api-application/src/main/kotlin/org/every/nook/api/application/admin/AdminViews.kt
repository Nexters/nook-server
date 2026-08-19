package org.every.nook.api.application.admin

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
    val places: List<AdminMappedPlace>,
)

data class AdminMappedPlace(
    val id: Long,
    val name: String,
    val address: String,
    val provider: String,
    val externalPlaceId: String,
    val sequence: Int,
)

data class AdminPlaceSummary(
    val id: Long,
    val name: String,
    val address: String,
    val provider: String,
    val externalPlaceId: String,
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

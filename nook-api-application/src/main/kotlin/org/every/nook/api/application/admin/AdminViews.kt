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

data class AdminParsingPipeline(
    val nodes: List<AdminParsingNode>,
    val edges: List<AdminParsingEdge>,
    val configurations: List<AdminRuntimeConfiguration>,
    val execution: AdminParsingExecution? = null,
)

data class AdminParsingNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val lane: String,
    val kind: String,
    val position: AdminParsingPosition,
    val summary: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val stages: List<String> = emptyList(),
    val configurationKeys: List<String> = emptyList(),
    val decisions: List<AdminParsingDecisionStep> = emptyList(),
    val sections: List<AdminParsingRuleSection> = emptyList(),
    val examples: List<String> = emptyList(),
)

data class AdminParsingPosition(val x: Int, val y: Int)

data class AdminParsingEdge(
    val id: String,
    val source: String,
    val target: String,
    val label: String? = null,
    val kind: String = "default",
)

data class AdminParsingRuleSection(
    val title: String,
    val description: String? = null,
    val rules: List<AdminParsingRule>,
)

data class AdminParsingRule(val label: String, val value: String, val description: String? = null)

data class AdminParsingDecisionStep(
    val order: Int,
    val title: String,
    val condition: String,
    val expression: String? = null,
    val onPass: String,
    val onFail: String,
    val source: String,
)

data class AdminRuntimeConfiguration(
    val key: String,
    val configuredValue: String?,
    val effectiveValue: String,
    val source: String,
    val description: String,
    val warnings: List<String> = emptyList(),
)

data class AdminParsingExecution(
    val postId: Long,
    val title: String?,
    val content: AdminParsingJobExecution,
    val place: AdminParsingJobExecution?,
    val traces: List<AdminProcessingTrace> = emptyList(),
)

data class AdminProcessingTrace(
    val id: Long,
    val flow: String,
    val stage: String,
    val action: String,
    val outcome: String,
    val attempt: Int?,
    val durationMs: Long?,
    val details: Map<String, String>,
    val createdAt: Instant,
)

data class AdminParsingJobExecution(
    val status: String,
    val stage: String?,
    val progressPercent: Int,
    val attemptCount: Int,
    val failureReason: String?,
    val nextAttemptAt: Instant?,
)

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

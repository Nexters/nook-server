package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApifyInstagramRecord(
    val type: String?,
    val shortCode: String?,
    val caption: String?,
    val hashtags: List<String>?,
    val url: String?,
    val displayUrl: String?,
    val images: List<String>?,
    val videoUrl: String?,
    val timestamp: String?,
    val childPosts: List<ChildPost>?,
    val ownerUsername: String?,
    val locationName: String?,
    val error: String?,
    val errorDescription: String?,
    val taggedUsers: List<TaggedUser>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChildPost(val type: String?, val displayUrl: String?, val videoUrl: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TaggedUser(
        @JsonProperty("full_name")
        val fullName: String?,
        val username: String?,
    )
}

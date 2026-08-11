package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class BrightDataInstagramRecord(
    val url: String?,
    @JsonProperty("user_posted")
    val userPosted: String?,
    val description: String?,
    val hashtags: List<String>?,
    val photos: List<String>?,
    val videos: List<String>?,
    val location: List<String?>?,
    @JsonProperty("location_details")
    val locationDetails: LocationDetails?,
    @JsonProperty("content_type")
    val contentType: String?,
    val thumbnail: String?,
    @JsonProperty("post_content")
    val postContent: List<PostContent>?,
    @JsonProperty("video_url")
    val videoUrl: String?,
    @JsonProperty("date_posted")
    val datePosted: String?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LocationDetails(
        val pk: JsonNode?,
        val name: String?,
        val lat: JsonNode?,
        val lng: JsonNode?,
        @JsonProperty("profile_pic_url")
        val profilePicUrl: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PostContent(
        val index: Int?,
        val type: String?,
        val url: String?,
        @JsonProperty("alt_text")
        val altText: String?,
    )
}

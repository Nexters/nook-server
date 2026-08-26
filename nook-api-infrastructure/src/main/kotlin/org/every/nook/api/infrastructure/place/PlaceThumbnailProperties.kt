package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("external.place-thumbnail")
data class PlaceThumbnailProperties(
    val provider: Provider = Provider.POST_MEDIA,
    val fixedUrl: String = DEFAULT_FIXED_URL,
) {
    init {
        if (provider == Provider.FIXED) {
            require(fixedUrl.isNotBlank()) { "Fixed place thumbnail URL must not be blank" }
        }
    }

    enum class Provider {
        POST_MEDIA,
        FIXED,
        DISABLED,
    }

    companion object {
        const val DEFAULT_FIXED_URL =
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/3d/" +
                "3dd7bb6919492e58a76fe9012b82c0a44e4bfdf7bf0784cddd90dc9bbbee17f9.jpg"
    }
}

fun PlaceThumbnailProperties.Provider.toProviderChain(): List<PlaceThumbnailProviderType> = when (this) {
    PlaceThumbnailProperties.Provider.POST_MEDIA -> listOf(PlaceThumbnailProviderType.POST_MEDIA)
    PlaceThumbnailProperties.Provider.FIXED -> listOf(PlaceThumbnailProviderType.FIXED)
    PlaceThumbnailProperties.Provider.DISABLED -> emptyList()
}

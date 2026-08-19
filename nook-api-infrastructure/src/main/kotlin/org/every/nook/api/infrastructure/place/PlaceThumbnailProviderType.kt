package org.every.nook.api.infrastructure.place

enum class PlaceThumbnailProviderType {
    POST_MEDIA,
    APIFY_NAVER,
    GOOGLE,
    FIXED,
    DISABLED,
    ;

    companion object {
        const val CONFIGURATION_KEY = "place.thumbnail.provider-chain"

        fun parse(value: String?): List<PlaceThumbnailProviderType> = value.orEmpty()
            .split(',')
            .mapNotNull { token -> entries.firstOrNull { it.name == token.trim().uppercase() } }
            .distinct()
    }
}

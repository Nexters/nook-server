package org.every.nook.api.infrastructure.instagram

enum class InstagramScrapingProviderMode {
    BRIGHT_DATA_ONLY,
    APIFY_ONLY,
    BRIGHT_DATA_WITH_APIFY_FALLBACK,
    ;

    companion object {
        const val CONFIGURATION_KEY = "instagram.scraping.provider-mode"
        val DEFAULT = BRIGHT_DATA_WITH_APIFY_FALLBACK

        fun from(value: String?): InstagramScrapingProviderMode = entries.firstOrNull {
            it.name == value?.trim()?.uppercase()
        } ?: DEFAULT
    }
}

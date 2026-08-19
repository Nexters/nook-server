package org.every.nook.api.infrastructure.place

enum class PlaceParsingProviderType {
    APIFY_NAVER,
    LEGACY,
    DISABLED,
    ;

    companion object {
        const val CONFIGURATION_KEY = "place.parsing.provider-chain"

        fun parse(value: String?): List<PlaceParsingProviderType> = value.orEmpty().split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { token -> entries.firstOrNull { it.name == token.uppercase() } }
    }
}

package org.every.nook.api.infrastructure.ocr

enum class OcrProviderType {
    COREPIN,
    CLOVA,
    OPENAI,
    ;

    companion object {
        const val CONFIGURATION_KEY = "ocr.image-text.provider-chain"
        val DEFAULT_CHAIN = listOf(OPENAI)

        fun parseChain(value: String?): List<OcrProviderType> = value
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.map { runCatching { valueOf(it.uppercase()) }.getOrNull() }
            ?.filterNotNull()
            ?.distinct()
            ?.takeIf(List<OcrProviderType>::isNotEmpty)
            ?: DEFAULT_CHAIN
    }
}

package org.every.nook.api.infrastructure.ocr

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.slf4j.LoggerFactory

class RuntimeImageTextExtractor(
    private val configurationReader: RuntimeConfigurationReader,
    private val providers: Map<OcrProviderType, ImageTextExtractor>,
) : ImageTextExtractor {
    override fun extract(request: ImageTextExtractor.Request): List<ImageTranscript> {
        val configuredChain = OcrProviderType.parseChain(
            configurationReader.findValue(OcrProviderType.CONFIGURATION_KEY),
        )
        val failures = mutableListOf<String>()
        configuredChain.forEach { providerType ->
            val provider = providers[providerType]
            if (provider == null) {
                failures += "$providerType is not configured"
                logFallback(providerType, "not_configured")
                return@forEach
            }
            val result = runCatching { provider.extract(request) }
            val transcripts = result.getOrNull()
            if (transcripts != null && transcripts.hasTextForEvery(request)) {
                logger.info(
                    ProcessingLogEvent(
                        action = "ocr.provider.selected",
                        flow = "place",
                        stage = "image-transcript",
                        outcome = "success",
                        fields = mapOf(
                            "provider.name" to providerType.name.lowercase(),
                            "content.image_count" to request.images.size,
                        ),
                    ),
                )
                return transcripts
            }
            val reason = result.exceptionOrNull()?.message ?: "empty transcript"
            failures += "$providerType: $reason"
            logFallback(providerType, if (transcripts == null) "error" else "empty_transcript")
        }
        error("Every OCR provider failed: ${failures.joinToString("; ")}")
    }

    private fun logFallback(providerType: OcrProviderType, reason: String) {
        logger.warn(
            ProcessingLogEvent(
                action = "ocr.provider.fallback",
                flow = "place",
                stage = "image-transcript",
                outcome = "failure",
                fields = mapOf(
                    "provider.name" to providerType.name.lowercase(),
                    "fallback.reason" to reason,
                ),
            ),
        )
    }

    private fun List<ImageTranscript>.hasTextForEvery(request: ImageTextExtractor.Request): Boolean {
        val textsByIndex = associateBy(ImageTranscript::imageIndex)
        return request.images.all { image -> textsByIndex[image.imageIndex]?.texts?.any(String::isNotBlank) == true }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(RuntimeImageTextExtractor::class.java)
    }
}

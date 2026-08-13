package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.slf4j.LoggerFactory

class InstagramPostContentExtractor(
    private val brightDataExtractor: PostContentExtractor,
    private val apifyExtractor: PostContentExtractor,
    private val configurationReader: RuntimeConfigurationReader,
) : PostContentExtractor {
    override fun supports(url: String): Boolean = InstagramContentUrl.supports(url)

    override fun extract(url: String): ExtractedPostContent {
        val configuredValue = configurationReader.findValue(InstagramScrapingProviderMode.CONFIGURATION_KEY)
        val mode = InstagramScrapingProviderMode.from(configuredValue)
        if (configuredValue != null && mode.name != configuredValue.trim().uppercase()) {
            logger.warn(
                "Unknown Instagram scraping provider mode '{}'; using {}",
                configuredValue,
                InstagramScrapingProviderMode.DEFAULT,
            )
        }
        logger.info(
            ProcessingLogEvent(
                action = "instagram.provider.mode.selected",
                flow = "post-content",
                stage = "extract",
                outcome = "success",
                fields = mapOf("provider.mode" to mode.name),
            ),
        )
        return when (mode) {
            InstagramScrapingProviderMode.BRIGHT_DATA_ONLY -> brightDataExtractor.extract(url)
            InstagramScrapingProviderMode.APIFY_ONLY -> apifyExtractor.extract(url)
            InstagramScrapingProviderMode.BRIGHT_DATA_WITH_APIFY_FALLBACK -> extractBrightDataWithFallback(url)
            InstagramScrapingProviderMode.APIFY_BRIGHT_WITH_DATA_FALLBACK -> extractApifyWithFallback(url)
        }
    }

    private fun extractBrightDataWithFallback(url: String): ExtractedPostContent = try {
        brightDataExtractor.extract(url)
    } catch (exception: PostContentProviderTimeoutException) {
        logFallback("bright_data", "apify", "timeout", exception)
        logger.warn("Bright Data timed out; falling back to Apify", exception)
        apifyExtractor.extract(url)
    } catch (exception: PostContentProviderException) {
        logFallback("bright_data", "apify", "failure", exception)
        logger.warn("Bright Data failed; falling back to Apify", exception)
        apifyExtractor.extract(url)
    }

    private fun extractApifyWithFallback(url: String): ExtractedPostContent = try {
        apifyExtractor.extract(url)
    } catch (exception: PostContentProviderTimeoutException) {
        logFallback("apify", "bright_data", "timeout", exception)
        logger.warn("Apify timed out; falling back to Bright Data", exception)
        brightDataExtractor.extract(url)
    } catch (exception: PostContentProviderException) {
        logFallback("apify", "bright_data", "failure", exception)
        logger.warn("Apify failed; falling back to Bright Data", exception)
        brightDataExtractor.extract(url)
    }

    private fun logFallback(from: String, to: String, reason: String, exception: Throwable) {
        logger.warn(
            ProcessingLogEvent(
                action = "instagram.provider.fallback",
                flow = "post-content",
                stage = "extract",
                outcome = "fallback",
                fields = mapOf(
                    "provider.name" to from,
                    "provider.fallback_to" to to,
                    "failure.reason" to reason,
                ),
            ),
            exception,
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InstagramPostContentExtractor::class.java)
    }
}

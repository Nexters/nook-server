package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
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
        return when (mode) {
            InstagramScrapingProviderMode.BRIGHT_DATA_ONLY -> brightDataExtractor.extract(url)
            InstagramScrapingProviderMode.APIFY_ONLY -> apifyExtractor.extract(url)
            InstagramScrapingProviderMode.BRIGHT_DATA_WITH_APIFY_FALLBACK -> extractWithFallback(url)
        }
    }

    private fun extractWithFallback(url: String): ExtractedPostContent = try {
        brightDataExtractor.extract(url)
    } catch (exception: PostContentProviderTimeoutException) {
        logger.warn("Bright Data timed out; falling back to Apify", exception)
        apifyExtractor.extract(url)
    } catch (exception: PostContentProviderException) {
        logger.warn("Bright Data failed; falling back to Apify", exception)
        apifyExtractor.extract(url)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InstagramPostContentExtractor::class.java)
    }
}

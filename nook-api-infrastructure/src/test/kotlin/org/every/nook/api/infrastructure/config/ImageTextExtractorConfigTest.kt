package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.infrastructure.openai.OpenAiImageTextExtractor
import org.every.nook.api.infrastructure.vision.GoogleCloudVisionImageTextExtractor
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageTextExtractorConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(OpenAiConfig::class.java, GoogleCloudVisionConfig::class.java)

    @Test
    fun `uses OpenAI image text extractor by default`() {
        contextRunner.run { context ->
            assertEquals(1, context.getBeanNamesForType(ImageTextExtractor::class.java).size)
            assertIs<OpenAiImageTextExtractor>(context.getBean(ImageTextExtractor::class.java))
        }
    }

    @Test
    fun `can restore Google Cloud Vision with configuration`() {
        contextRunner
            .withPropertyValues("place-parsing.image-text-provider=google-cloud-vision")
            .run { context ->
                assertEquals(1, context.getBeanNamesForType(ImageTextExtractor::class.java).size)
                assertIs<GoogleCloudVisionImageTextExtractor>(context.getBean(ImageTextExtractor::class.java))
            }
    }
}

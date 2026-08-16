package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.FixedPlaceThumbnailProvider
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertIs

class PlaceThumbnailConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PlaceThumbnailConfig::class.java)

    @Test
    fun `uses fixed bucket thumbnail without media storage by default`() {
        contextRunner.run { context ->
            assertIs<FixedPlaceThumbnailProvider>(context.getBean(PlaceThumbnailProvider::class.java))
        }
    }

    @Test
    fun `does not use Google when its provider is selected but disabled`() {
        contextRunner
            .withPropertyValues(
                "external.place-thumbnail.provider=google",
                "external.google-place-photo.enabled=false",
            )
            .run { context ->
                assertIs<NoOpPlaceThumbnailProvider>(context.getBean(PlaceThumbnailProvider::class.java))
            }
    }
}

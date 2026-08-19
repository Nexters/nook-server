package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.place.PostMediaPlaceThumbnailProvider
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertIs

class PlaceThumbnailConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PlaceThumbnailConfig::class.java)

    @Test
    fun `uses post media thumbnail provider by default`() {
        contextRunner
            .withBean(
                PostMediaJpaRepository::class.java,
                Supplier { mock(PostMediaJpaRepository::class.java) },
            )
            .withBean(
                PostMediaStoragePort::class.java,
                Supplier { mock(PostMediaStoragePort::class.java) },
            )
            .run { context ->
                assertIs<PostMediaPlaceThumbnailProvider>(context.getBean(PlaceThumbnailProvider::class.java))
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

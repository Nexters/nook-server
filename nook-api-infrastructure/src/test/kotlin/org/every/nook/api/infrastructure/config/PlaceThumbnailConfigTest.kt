package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.place.RuntimePlaceThumbnailProvider
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertIs

class PlaceThumbnailConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PlaceThumbnailConfig::class.java)
        .withBean(RuntimeConfigurationReader::class.java, Supplier { RuntimeConfigurationReader { null } })

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
                assertIs<RuntimePlaceThumbnailProvider>(context.getBean(PlaceThumbnailProvider::class.java))
            }
    }

    @Test
    fun `creates runtime provider when legacy Google provider is selected`() {
        contextRunner
            .withPropertyValues(
                "external.place-thumbnail.provider=google",
            )
            .run { context ->
                assertIs<RuntimePlaceThumbnailProvider>(context.getBean(PlaceThumbnailProvider::class.java))
            }
    }
}

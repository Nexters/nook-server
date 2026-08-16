package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PagedPlaceSearchProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.SearchPlacesUseCase
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.application.place.port.DisconnectPostPlacePort
import org.every.nook.api.infrastructure.auth.JwtProperties
import org.every.nook.api.infrastructure.place.KakaoPlaceSearchProvider
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import java.time.Clock
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ManualPlaceConnectionConfigTest {
    private val contextRunner = WebApplicationContextRunner()
        .withUserConfiguration(ManualPlaceConnectionConfig::class.java)
        .withBean(
            "kakaoPlaceSearchProvider",
            KakaoPlaceSearchProvider::class.java,
            Supplier { mock(KakaoPlaceSearchProvider::class.java) },
        )
        .withBean(
            JwtProperties::class.java,
            Supplier { JwtProperties(accessSecret = "a".repeat(MIN_SECRET_LENGTH)) },
        )
        .withBean(Clock::class.java, Supplier { Clock.systemUTC() })
        .withBean(
            ConnectPostPlacePort::class.java,
            Supplier { mock(ConnectPostPlacePort::class.java) },
        )
        .withBean(
            DisconnectPostPlacePort::class.java,
            Supplier { mock(DisconnectPostPlacePort::class.java) },
        )
        .withBean(PlaceThumbnailProvider::class.java, Supplier { NoOpPlaceThumbnailProvider })

    @Test
    fun `starts with a single paged place search provider`() {
        contextRunner.run { context ->
            assertNotNull(context.getBean(SearchPlacesUseCase::class.java))
            assertEquals(
                listOf("kakaoPlaceSearchProvider"),
                context.getBeanNamesForType(PagedPlaceSearchProvider::class.java).toList(),
            )
        }
    }

    private companion object {
        const val MIN_SECRET_LENGTH = 32
    }
}

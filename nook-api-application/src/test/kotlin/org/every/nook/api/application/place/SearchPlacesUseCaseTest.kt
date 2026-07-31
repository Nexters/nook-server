package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.application.post.error.PostNotFoundException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchPlacesUseCaseTest {
    @Test
    fun `searches a zero based page and signs every candidate`() {
        var providerRequest: PlaceSearchProvider.Request? = null
        val candidate = candidate()
        val useCase = SearchPlacesUseCase(
            provider = PagedPlaceSearchProvider { request ->
                providerRequest = request
                PlaceCandidatePage(listOf(candidate), page = 2, size = 10, hasNext = true)
            },
            selectionTokenPort = tokenPort(candidate),
        )

        val result = useCase(
            SearchPlacesUseCase.Query(
                userId = 7,
                keyword = " 퍼머넌트해비탯 ",
                page = 1,
                size = 10,
                longitude = BigDecimal("127.0"),
                latitude = BigDecimal("37.5"),
            ),
        )

        assertEquals("퍼머넌트해비탯", providerRequest?.query)
        assertEquals(2, providerRequest?.page)
        assertEquals(10, providerRequest?.size)
        assertEquals("token-7", result.items.single().selectionToken)
        assertEquals(true, result.hasNext)
    }

    @Test
    fun `rejects a page outside provider limits`() {
        val useCase = SearchPlacesUseCase(
            provider = PagedPlaceSearchProvider { error("should not search") },
            selectionTokenPort = tokenPort(candidate()),
        )

        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(
                SearchPlacesUseCase.Query(
                    userId = 7,
                    keyword = "장소",
                    page = 45,
                    size = 15,
                    longitude = null,
                    latitude = null,
                ),
            )
        }
    }

    @Test
    fun `connects a signed place to an owned post`() {
        val candidate = candidate()
        val useCase = ConnectPostPlaceUseCase(
            selectionTokenPort = tokenPort(candidate),
            connectPostPlacePort = ConnectPostPlacePort { userId, postId, selected, thumbnailUrl ->
                assertEquals(7, userId)
                assertEquals(11, postId)
                assertEquals(candidate, selected)
                assertEquals("https://cdn.example.com/google-place.jpg", thumbnailUrl)
                ConnectPostPlacePort.Result.Connected(17)
            },
            thumbnailProvider = PlaceThumbnailProvider { "https://cdn.example.com/google-place.jpg" },
        )

        val result = useCase(ConnectPostPlaceUseCase.Command(7, 11, "valid"))

        assertEquals(17, result.placeId)
    }

    @Test
    fun `rejects an invalid place selection token`() {
        val useCase = ConnectPostPlaceUseCase(
            selectionTokenPort = tokenPort(candidate()),
            connectPostPlacePort = ConnectPostPlacePort { _, _, _, _ -> error("should not connect") },
        )

        assertFailsWith<InvalidPlaceSelectionException> {
            useCase(ConnectPostPlaceUseCase.Command(7, 11, "invalid"))
        }
    }

    @Test
    fun `hides a post owned by another user`() {
        val useCase = ConnectPostPlaceUseCase(
            selectionTokenPort = tokenPort(candidate()),
            connectPostPlacePort = ConnectPostPlacePort { _, _, _, _ -> ConnectPostPlacePort.Result.PostNotFound },
        )

        assertFailsWith<PostNotFoundException> {
            useCase(ConnectPostPlaceUseCase.Command(7, 11, "valid"))
        }
    }

    @Test
    fun `rejects manual connection while automatic parsing is running`() {
        val useCase = ConnectPostPlaceUseCase(
            selectionTokenPort = tokenPort(candidate()),
            connectPostPlacePort = ConnectPostPlacePort { _, _, _, _ -> ConnectPostPlacePort.Result.ParsingInProgress },
        )

        assertFailsWith<PlaceParsingInProgressException> {
            useCase(ConnectPostPlaceUseCase.Command(7, 11, "valid"))
        }
    }

    private fun tokenPort(candidate: PlaceCandidate): PlaceSelectionTokenPort = object : PlaceSelectionTokenPort {
        override fun issue(userId: Long, candidate: PlaceCandidate): String = "token-$userId"

        override fun verify(userId: Long, token: String): PlaceCandidate? = candidate.takeIf { token == "valid" }
    }

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = "퍼머넌트해비탯",
        address = "경기 용인시",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        category = "카페",
        phoneNumber = null,
        providerUrl = "https://place.map.kakao.com/1234",
        distanceMeters = 1200,
    )
}

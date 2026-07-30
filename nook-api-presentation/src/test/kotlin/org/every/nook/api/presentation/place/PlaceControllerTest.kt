package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.GetMapPlacesUseCase
import org.every.nook.api.application.place.GetPlaceDetailUseCase
import org.every.nook.api.application.place.GetRecentPlacesUseCase
import org.every.nook.api.application.place.MapPlaceView
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlacePostGroupView
import org.every.nook.api.application.place.PlacePostMediaTypeView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import org.every.nook.api.application.place.PlaceSearchResultView
import org.every.nook.api.application.place.PlaceSearchSliceView
import org.every.nook.api.application.place.RecentPlaceCursor
import org.every.nook.api.application.place.RecentPlaceSliceView
import org.every.nook.api.application.place.RecentPlaceView
import org.every.nook.api.application.place.SearchPlacesUseCase
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PlaceControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var updatePlaceBookmarkUseCase: UpdatePlaceBookmarkUseCase
    private lateinit var getPlaceDetailUseCase: GetPlaceDetailUseCase
    private lateinit var getMapPlacesUseCase: GetMapPlacesUseCase
    private lateinit var getRecentPlacesUseCase: GetRecentPlacesUseCase
    private lateinit var searchPlacesUseCase: SearchPlacesUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        updatePlaceBookmarkUseCase = mock(UpdatePlaceBookmarkUseCase::class.java)
        getPlaceDetailUseCase = mock(GetPlaceDetailUseCase::class.java)
        getMapPlacesUseCase = mock(GetMapPlacesUseCase::class.java)
        getRecentPlacesUseCase = mock(GetRecentPlacesUseCase::class.java)
        searchPlacesUseCase = mock(SearchPlacesUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                PlaceController(
                    updatePlaceBookmarkUseCase,
                    getPlaceDetailUseCase,
                    getMapPlacesUseCase,
                    getRecentPlacesUseCase,
                    searchPlacesUseCase,
                ),
            )
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns place detail and only the current user's related posts`() {
        val query = GetPlaceDetailUseCase.Query(
            userId = TEST_USER_ID,
            placeId = 17,
            page = 1,
            size = 10,
        )
        `when`(getPlaceDetailUseCase(query)).thenReturn(
            PlaceDetailView(
                id = 17,
                provider = "KAKAO",
                externalPlaceId = "1234",
                name = "원동미나리삼겹살",
                address = "서울 용산구 한강대로77길 4-1",
                latitude = BigDecimal("37.1"),
                longitude = BigDecimal("127.1"),
                category = "음식점 > 한식",
                phoneNumber = "02-123-4567",
                bookmarked = true,
                posts = PlacePostPageView(
                    items = listOf(
                        PlacePostView(
                            postId = 21,
                            title = "용산 미나리삼겹살",
                            authorIdentifier = "author",
                            representativeMedia = PlacePostMediaView(
                                PlacePostMediaTypeView.IMAGE,
                                "https://example.com/image.jpg",
                            ),
                            memo = "주말 방문",
                            savedAt = Instant.parse("2026-07-27T00:00:00Z"),
                            groups = listOf(PlacePostGroupView(17, "맛집", "YELLOW")),
                        ),
                    ),
                    page = 1,
                    size = 10,
                    totalElements = 11,
                    totalPages = 2,
                    hasNext = false,
                ),
            ),
        )

        mockMvc.get("/api/v1/places/17?page=1&size=10")
            .andExpect {
                status { isOk() }
                jsonPath("$.success.id") { value(17) }
                jsonPath("$.success.bookmarked") { value(true) }
                jsonPath("$.success.posts.items[0].postId") { value(21) }
                jsonPath("$.success.posts.items[0].representativeMedia.type") { value("IMAGE") }
                jsonPath("$.success.posts.items[0].groups[0].id") { value(17) }
                jsonPath("$.success.posts.items[0].groups[0].name") { value("맛집") }
                jsonPath("$.success.posts.items[0].groups[0].color") { value("YELLOW") }
                jsonPath("$.success.posts.items[0].savedAt") { value("2026-07-27T09:00:00+09:00") }
                jsonPath("$.success.posts.totalElements") { value(11) }
            }

        verify(getPlaceDetailUseCase)(query)
    }

    @Test
    fun `returns lightweight map places inside requested bounds`() {
        val query = GetMapPlacesUseCase.Query(
            userId = TEST_USER_ID,
            northLatitude = BigDecimal("37.6"),
            westLongitude = BigDecimal("126.8"),
            southLatitude = BigDecimal("37.4"),
            eastLongitude = BigDecimal("127.2"),
        )
        `when`(getMapPlacesUseCase(query)).thenReturn(
            listOf(
                MapPlaceView(
                    id = 17,
                    name = "퍼머넌트해비탯",
                    latitude = BigDecimal("37.5"),
                    longitude = BigDecimal("127.0"),
                    color = "BLUE",
                ),
            ),
        )

        mockMvc.get(
            "/api/v1/places/map" +
                "?northLatitude=37.6&westLongitude=126.8&southLatitude=37.4&eastLongitude=127.2",
        ).andExpect {
            status { isOk() }
            jsonPath("$.success[0].id") { value(17) }
            jsonPath("$.success[0].name") { value("퍼머넌트해비탯") }
            jsonPath("$.success[0].latitude") { value(37.5) }
            jsonPath("$.success[0].longitude") { value(127.0) }
            jsonPath("$.success[0].color") { value("BLUE") }
        }

        verify(getMapPlacesUseCase)(query)
    }

    @Test
    fun `returns recent places with an opaque next cursor`() {
        val bookmarkedAt = Instant.parse("2026-07-27T00:00:00Z")
        `when`(
            getRecentPlacesUseCase(
                GetRecentPlacesUseCase.Query(
                    userId = TEST_USER_ID,
                    cursor = null,
                    size = 2,
                ),
            ),
        ).thenReturn(
            RecentPlaceSliceView(
                items = listOf(
                    RecentPlaceView(
                        bookmarkId = 31,
                        bookmarkedAt = bookmarkedAt,
                        id = 17,
                        name = "퍼머넌트해비탯",
                        address = "경기 용인시",
                        category = "카페",
                        latitude = BigDecimal("37.5"),
                        longitude = BigDecimal("127.0"),
                        thumbnailUrl = "https://example.com/place.jpg",
                    ),
                ),
                nextCursor = RecentPlaceCursor(bookmarkedAt, 31),
                hasNext = true,
            ),
        )

        mockMvc.get("/api/v1/places/recent?size=2")
            .andExpect {
                status { isOk() }
                jsonPath("$.success.items[0].id") { value(17) }
                jsonPath("$.success.items[0].name") { value("퍼머넌트해비탯") }
                jsonPath("$.success.items[0].thumbnailUrl") { value("https://example.com/place.jpg") }
                jsonPath("$.success.nextCursor") { isNotEmpty() }
                jsonPath("$.success.hasNext") { value(true) }
            }
    }

    @Test
    fun `returns paged place candidates for manual connection`() {
        val query = SearchPlacesUseCase.Query(
            userId = TEST_USER_ID,
            keyword = "퍼머넌트해비탯",
            page = 1,
            size = 10,
            longitude = BigDecimal("127.0"),
            latitude = BigDecimal("37.5"),
        )
        `when`(searchPlacesUseCase(query)).thenReturn(
            PlaceSearchSliceView(
                items = listOf(
                    PlaceSearchResultView(
                        selectionToken = "signed-token",
                        candidate = PlaceCandidate(
                            provider = "KAKAO",
                            externalPlaceId = "1234",
                            name = "퍼머넌트해비탯",
                            address = "경기 용인시",
                            latitude = BigDecimal("37.5"),
                            longitude = BigDecimal("127.0"),
                            category = "카페",
                            phoneNumber = null,
                            providerUrl = null,
                            distanceMeters = 1200,
                        ),
                    ),
                ),
                page = 1,
                size = 10,
                hasNext = true,
            ),
        )

        mockMvc.get(
            "/api/v1/places/search" +
                "?query=퍼머넌트해비탯&page=1&size=10&longitude=127.0&latitude=37.5",
        ).andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].selectionToken") { value("signed-token") }
            jsonPath("$.success.items[0].name") { value("퍼머넌트해비탯") }
            jsonPath("$.success.items[0].distanceMeters") { value(1200) }
            jsonPath("$.success.hasNext") { value(true) }
        }

        verify(searchPlacesUseCase)(query)
    }

    @Test
    fun `updates a place bookmark without a post identifier`() {
        mockMvc.patch("/api/v1/places/17/bookmark") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"bookmarked":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
        }

        verify(updatePlaceBookmarkUseCase)(
            UpdatePlaceBookmarkUseCase.Command(
                userId = TEST_USER_ID,
                placeId = 17,
                bookmarked = false,
            ),
        )
    }

    private companion object {
        const val TEST_USER_ID = 1L
    }
}

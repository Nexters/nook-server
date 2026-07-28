package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.GetPlaceDetailUseCase
import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlacePostMediaTypeView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

class PlaceControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var updatePlaceBookmarkUseCase: UpdatePlaceBookmarkUseCase
    private lateinit var getPlaceDetailUseCase: GetPlaceDetailUseCase

    @BeforeTest
    fun setUp() {
        updatePlaceBookmarkUseCase = mock(UpdatePlaceBookmarkUseCase::class.java)
        getPlaceDetailUseCase = mock(GetPlaceDetailUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(PlaceController(updatePlaceBookmarkUseCase, getPlaceDetailUseCase))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `returns place detail and only the current user's related posts`() {
        val query = GetPlaceDetailUseCase.Query(
            userId = UserContextArgumentResolver.DUMMY_USER_ID,
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
                jsonPath("$.success.posts.totalElements") { value(11) }
            }

        verify(getPlaceDetailUseCase)(query)
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
                userId = UserContextArgumentResolver.DUMMY_USER_ID,
                placeId = 17,
                bookmarked = false,
            ),
        )
    }
}

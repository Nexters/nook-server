package org.every.nook.api.presentation.post

import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PlaceView
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test

class PostControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var createUseCase: CreatePostUseCase

    @BeforeTest
    fun setUp() {
        createUseCase = mock(CreatePostUseCase::class.java)
        `when`(
            createUseCase(
                CreatePostUseCase.Command(
                    userId = UserContextArgumentResolver.DUMMY_USER_ID,
                    url = "https://www.instagram.com/p/ABC123/",
                    memo = "주말에 방문",
                ),
            ),
        ).thenReturn(
            CreatePostUseCase.Result(
                postId = 11,
                placeParsingStatus = PlaceParsingStatusView.PENDING,
            ),
        )
        val findUseCase = mock(FindPostPlaceParsingUseCase::class.java)
        `when`(
            findUseCase(
                FindPostPlaceParsingUseCase.Query(
                    userId = UserContextArgumentResolver.DUMMY_USER_ID,
                    postId = 11,
                ),
            ),
        ).thenReturn(
            FindPostPlaceParsingUseCase.Result(
                postId = 11,
                placeParsingStatus = PlaceParsingStatusView.COMPLETED,
                failureReason = null,
                places = listOf(
                    PlaceView(
                        id = 17,
                        provider = "KAKAO",
                        externalPlaceId = "123",
                        name = "Nook Cafe",
                        address = "Seoul",
                        latitude = BigDecimal("37.1"),
                        longitude = BigDecimal("127.1"),
                        category = null,
                        phoneNumber = null,
                        bookmarked = true,
                    ),
                ),
            ),
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(PostController(createUseCase, findUseCase))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `creates a post from a URL`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "url": "https://www.instagram.com/p/ABC123/",
                  "memo": "주말에 방문"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.postId") { value(11) }
            jsonPath("$.success.savedPostId") { doesNotExist() }
            jsonPath("$.success.placeParsingStatus") { value("PENDING") }
        }
    }

    @Test
    fun `returns completed parsing with places`() {
        mockMvc.get("/api/v1/posts/11/place-parsing") {
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.postId") { value(11) }
            jsonPath("$.success.savedPostId") { doesNotExist() }
            jsonPath("$.success.placeParsingStatus") { value("COMPLETED") }
            jsonPath("$.success.places[0].id") { value(17) }
            jsonPath("$.success.places[0].name") { value("Nook Cafe") }
            jsonPath("$.success.places[0].bookmarked") { value(true) }
        }
    }

    @Test
    fun `rejects a blank URL`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
            jsonPath("$.error.data.violations[0].field") { value("url") }
        }
    }

    @Test
    fun `rejects an unsupported URL with a source agnostic error`() {
        val unsupportedUrl = "https://example.com/post/1"
        `when`(
            createUseCase(
                CreatePostUseCase.Command(
                    userId = UserContextArgumentResolver.DUMMY_USER_ID,
                    url = unsupportedUrl,
                ),
            ),
        ).thenThrow(UnsupportedPostUrlException())

        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"$unsupportedUrl"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("UNSUPPORTED_POST_URL") }
        }
    }
}

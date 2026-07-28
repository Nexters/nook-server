package org.every.nook.api.presentation.post

import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.ReplaceSavedPostGroupsUseCase
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.GetSavedPostDetailUseCase
import org.every.nook.api.application.post.ListSavedPostsUseCase
import org.every.nook.api.application.post.UpdatePostMemoUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PlaceView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostSummary
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PostControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var createUseCase: CreatePostUseCase
    private lateinit var listUseCase: ListSavedPostsUseCase
    private lateinit var detailUseCase: GetSavedPostDetailUseCase
    private lateinit var updateMemoUseCase: UpdatePostMemoUseCase
    private lateinit var replaceGroupsUseCase: ReplaceSavedPostGroupsUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        createUseCase = mock(CreatePostUseCase::class.java)
        val findUseCase = mock(FindPostPlaceParsingUseCase::class.java)
        listUseCase = mock(ListSavedPostsUseCase::class.java)
        detailUseCase = mock(GetSavedPostDetailUseCase::class.java)
        updateMemoUseCase = mock(UpdatePostMemoUseCase::class.java)
        replaceGroupsUseCase = mock(ReplaceSavedPostGroupsUseCase::class.java)
        stubCreate()
        stubPlaceParsing(findUseCase)
        stubSavedPostList()
        stubSavedPostDetail()
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                PostController(
                    createUseCase,
                    findUseCase,
                    listUseCase,
                    detailUseCase,
                    updateMemoUseCase,
                    replaceGroupsUseCase,
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
    fun `updates my saved post memo`() {
        mockMvc.patch("/api/v1/posts/11/memo") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"memo":"다음 주 평일에 방문"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
        }

        verify(updateMemoUseCase)(
            UpdatePostMemoUseCase.Command(
                userId = TEST_USER_ID,
                postId = 11,
                memo = "다음 주 평일에 방문",
            ),
        )
    }

    @Test
    fun `deletes my saved post memo with null`() {
        mockMvc.patch("/api/v1/posts/11/memo") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"memo":null}"""
        }.andExpect {
            status { isOk() }
        }

        verify(updateMemoUseCase)(
            UpdatePostMemoUseCase.Command(
                userId = TEST_USER_ID,
                postId = 11,
                memo = null,
            ),
        )
    }

    @Test
    fun `rejects an overlong memo`() {
        mockMvc.patch("/api/v1/posts/11/memo") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"memo":"${"x".repeat(2001)}"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
            jsonPath("$.error.data.violations[0].field") { value("memo") }
        }
    }

    @Test
    fun `replaces saved post groups`() {
        mockMvc.put("/api/v1/posts/11/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds":[17,18,17]}"""
        }.andExpect {
            status { isOk() }
        }

        verify(replaceGroupsUseCase)(
            ReplaceSavedPostGroupsUseCase.Command(
                userId = TEST_USER_ID,
                savedPostId = 11,
                groupIds = listOf(17, 18, 17),
            ),
        )
    }

    private fun stubCreate() {
        `when`(
            createUseCase(
                CreatePostUseCase.Command(
                    userId = TEST_USER_ID,
                    url = "https://www.instagram.com/p/ABC123/",
                    memo = "주말에 방문",
                    groupIds = listOf(17, 18),
                ),
            ),
        ).thenReturn(
            CreatePostUseCase.Result(
                postId = 11,
                placeParsingStatus = PlaceParsingStatusView.PENDING,
            ),
        )
    }

    private fun stubPlaceParsing(findUseCase: FindPostPlaceParsingUseCase) {
        `when`(
            findUseCase(
                FindPostPlaceParsingUseCase.Query(
                    userId = TEST_USER_ID,
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
    }

    private fun stubSavedPostList() {
        `when`(
            listUseCase(
                ListSavedPostsUseCase.Query(
                    userId = TEST_USER_ID,
                    page = 0,
                    size = 20,
                ),
            ),
        ).thenReturn(
            SavedPostPage(
                items = listOf(
                    SavedPostSummary(
                        postId = 11,
                        title = "성수 카페",
                        authorIdentifier = "nook",
                        representativeMedia = SavedPostMedia(
                            type = SavedPostMediaType.IMAGE,
                            url = "https://example.com/1.jpg",
                            sequence = 0,
                        ),
                        memo = "주말에 방문",
                        savedAt = Instant.parse("2026-07-27T00:00:00Z"),
                    ),
                ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                hasNext = false,
            ),
        )
    }

    private fun stubSavedPostDetail() {
        `when`(
            detailUseCase(
                GetSavedPostDetailUseCase.Query(
                    userId = TEST_USER_ID,
                    postId = 11,
                ),
            ),
        ).thenReturn(
            SavedPostDetail(
                postId = 11,
                title = "성수 카페",
                body = "본문",
                authorIdentifier = "nook",
                canonicalUrl = "https://www.instagram.com/p/ABC123/",
                publishedAt = Instant.parse("2026-07-20T00:00:00Z"),
                media = listOf(
                    SavedPostMedia(
                        type = SavedPostMediaType.IMAGE,
                        url = "https://example.com/1.jpg",
                        sequence = 0,
                    ),
                ),
                hashtags = listOf("성수"),
                memo = "주말에 방문",
                savedAt = Instant.parse("2026-07-27T00:00:00Z"),
                placeParsingStatus = PlaceParsingStatusView.COMPLETED,
                placeParsingFailureReason = null,
                places = emptyList(),
            ),
        )
    }

    @Test
    fun `creates a post from a URL`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "url": "https://www.instagram.com/p/ABC123/",
                  "memo": "주말에 방문",
                  "groupIds": [17, 18]
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
    fun `returns paged saved posts`() {
        mockMvc.get("/api/v1/posts?page=0&size=20").andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].postId") { value(11) }
            jsonPath("$.success.items[0].representativeMedia.sequence") { value(0) }
            jsonPath("$.success.totalElements") { value(1) }
            jsonPath("$.success.hasNext") { value(false) }
        }
    }

    @Test
    fun `returns a saved post detail`() {
        mockMvc.get("/api/v1/posts/11").andExpect {
            status { isOk() }
            jsonPath("$.success.postId") { value(11) }
            jsonPath("$.success.body") { value("본문") }
            jsonPath("$.success.media[0].url") { value("https://example.com/1.jpg") }
            jsonPath("$.success.hashtags[0]") { value("성수") }
            jsonPath("$.success.places") { isEmpty() }
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
    fun `rejects non-positive group ids`() {
        mockMvc.put("/api/v1/posts/11/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds":[0]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `rejects an unsupported URL with a source agnostic error`() {
        val unsupportedUrl = "https://example.com/post/1"
        `when`(
            createUseCase(
                CreatePostUseCase.Command(
                    userId = TEST_USER_ID,
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
    private companion object {
        const val TEST_USER_ID = 1L
    }
}

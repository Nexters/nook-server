package org.every.nook.api.presentation.post

import org.every.nook.api.application.content.PrivatePostException
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.ReplaceSavedPostGroupsUseCase
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.place.ConnectPostPlaceUseCase
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.DeleteSavedPostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.GetSavedPostDetailUseCase
import org.every.nook.api.application.post.ListSavedPostsUseCase
import org.every.nook.api.application.post.UpdatePostMemoUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PlaceView
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostGroup
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.model.SavedPostSummary
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
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
    private lateinit var connectPostPlaceUseCase: ConnectPostPlaceUseCase
    private lateinit var deleteSavedPostUseCase: DeleteSavedPostUseCase

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
        connectPostPlaceUseCase = mock(ConnectPostPlaceUseCase::class.java)
        deleteSavedPostUseCase = mock(DeleteSavedPostUseCase::class.java)
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
                    connectPostPlaceUseCase,
                    deleteSavedPostUseCase,
                ),
            )
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `deletes my saved post`() {
        mockMvc.delete("/api/v1/posts/11").andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
        }

        verify(deleteSavedPostUseCase)(DeleteSavedPostUseCase.Command(TEST_USER_ID, 11))
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
    fun `connects a searched place to my saved post`() {
        val command = ConnectPostPlaceUseCase.Command(
            userId = TEST_USER_ID,
            postId = 11,
            selectionToken = "signed-token",
        )
        `when`(connectPostPlaceUseCase(command)).thenReturn(ConnectPostPlaceUseCase.Result(17))

        mockMvc.post("/api/v1/posts/11/places") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"selectionToken":"signed-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.placeId") { value(17) }
        }

        verify(connectPostPlaceUseCase)(command)
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
                processingStatus = PostProcessingStatusView.PENDING,
                processingStage = PostProcessingStageView.CONTENT,
                processingPercent = 5,
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
                        thumbnailUrl = "https://example.com/place-thumbnail.jpg",
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
                        processingStatus = PostProcessingStatusView.PROCESSING,
                        processingStage = PostProcessingStageView.CONTENT,
                        processingPercent = 35,
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

    private fun stubSavedPostDetail(publishedAt: Instant? = Instant.parse("2026-07-20T00:00:00Z")) {
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
                publishedAt = publishedAt,
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
                groups = listOf(
                    SavedPostGroup(id = 17, name = "맛집", color = "YELLOW"),
                    SavedPostGroup(id = 18, name = "카페", color = "GREEN"),
                ),
                placeParsingStatus = PlaceParsingStatusView.COMPLETED,
                placeParsingFailureReason = null,
                places = listOf(
                    SavedPostPlace(
                        id = 17,
                        provider = "KAKAO",
                        externalPlaceId = "123",
                        name = "Nook Cafe",
                        address = "Seoul",
                        latitude = BigDecimal("37.1"),
                        longitude = BigDecimal("127.1"),
                        category = null,
                        phoneNumber = null,
                        thumbnailUrl = "https://example.com/place-thumbnail.jpg",
                        bookmarked = true,
                        sequence = 0,
                    ),
                ),
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
            jsonPath("$.success.processingStatus") { value("PENDING") }
            jsonPath("$.success.processingStage") { value("CONTENT") }
            jsonPath("$.success.processingPercent") { value(5) }
        }
    }

    @Test
    fun `rejects a post without group ids`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://www.instagram.com/p/ABC123/"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }

        verifyNoInteractions(createUseCase)
    }

    @Test
    fun `rejects a post with null group ids`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://www.instagram.com/p/ABC123/","groupIds":null}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }

        verifyNoInteractions(createUseCase)
    }

    @Test
    fun `rejects a post with empty group ids`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://www.instagram.com/p/ABC123/","groupIds":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }

        verifyNoInteractions(createUseCase)
    }

    @Test
    fun `rejects a post with an inaccessible group`() {
        val command = CreatePostUseCase.Command(
            userId = TEST_USER_ID,
            url = "https://www.instagram.com/p/ABC123/",
            groupIds = listOf(999),
        )
        `when`(createUseCase(command)).thenThrow(GroupNotFoundException())

        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://www.instagram.com/p/ABC123/","groupIds":[999]}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.errorCode") { value("GROUP_NOT_FOUND") }
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
            jsonPath("$.success.places[0].thumbnailUrl") { value("https://example.com/place-thumbnail.jpg") }
            jsonPath("$.success.places[0].bookmarked") { value(true) }
        }
    }

    @Test
    fun `returns paged saved posts`() {
        mockMvc.get("/api/v1/posts?page=0&size=20").andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].postId") { value(11) }
            jsonPath("$.success.items[0].representativeMedia.sequence") { value(0) }
            jsonPath("$.success.items[0].processingStatus") { value("PROCESSING") }
            jsonPath("$.success.items[0].processingStage") { value("CONTENT") }
            jsonPath("$.success.items[0].processingPercent") { value(35) }
            jsonPath("$.success.items[0].savedAt") { value("2026-07-27T09:00:00+09:00") }
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
            jsonPath("$.success.groups[0].id") { value(17) }
            jsonPath("$.success.groups[0].name") { value("맛집") }
            jsonPath("$.success.groups[0].color") { value("YELLOW") }
            jsonPath("$.success.groups[1].id") { value(18) }
            jsonPath("$.success.places[0].id") { value(17) }
            jsonPath("$.success.places[0].thumbnailUrl") { value("https://example.com/place-thumbnail.jpg") }
            jsonPath("$.success.publishedAt") { value("2026-07-20T09:00:00+09:00") }
            jsonPath("$.success.savedAt") { value("2026-07-27T09:00:00+09:00") }
            jsonPath("$.success.processingStatus") { value("COMPLETED") }
            jsonPath("$.success.processingStage") { doesNotExist() }
            jsonPath("$.success.processingPercent") { value(100) }
        }
    }

    @Test
    fun `keeps an absent published time nullable`() {
        stubSavedPostDetail(publishedAt = null)

        mockMvc.get("/api/v1/posts/11").andExpect {
            status { isOk() }
            jsonPath("$.success.publishedAt") { doesNotExist() }
        }
    }

    @Test
    fun `rejects a blank URL`() {
        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"","groupIds":[17]}"""
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
                    groupIds = listOf(17),
                ),
            ),
        ).thenThrow(UnsupportedPostUrlException())

        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"$unsupportedUrl","groupIds":[17]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("UNSUPPORTED_POST_URL") }
        }
    }

    @Test
    fun `rejects a private post with a dedicated error`() {
        val privatePostUrl =
            "https://www.instagram.com/p/Dbw1jDTBv7du60ZhDSqNH9kSJCYlKc6TzHaACA0/"
        `when`(
            createUseCase(
                CreatePostUseCase.Command(
                    userId = TEST_USER_ID,
                    url = privatePostUrl,
                    groupIds = listOf(17),
                ),
            ),
        ).thenThrow(PrivatePostException())

        mockMvc.post("/api/v1/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"$privatePostUrl","groupIds":[17]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("PRIVATE_POST") }
            jsonPath("$.error.reason") { value("비공개 게시물은 저장할 수 없습니다.") }
        }
    }

    private companion object {
        const val TEST_USER_ID = 1L
    }
}

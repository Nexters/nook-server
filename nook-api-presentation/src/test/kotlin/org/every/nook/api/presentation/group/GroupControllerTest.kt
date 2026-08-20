package org.every.nook.api.presentation.group

import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.GroupPlacePage
import org.every.nook.api.application.group.GroupPlaceSummary
import org.every.nook.api.application.group.GroupPostPage
import org.every.nook.api.application.group.GroupPostSummary
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.ListGroupPlacesUseCase
import org.every.nook.api.application.group.ListGroupPostsUseCase
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GroupControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var listGroupsUseCase: ListGroupsUseCase
    private lateinit var createGroupUseCase: CreateGroupUseCase
    private lateinit var updateGroupUseCase: UpdateGroupUseCase
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase
    private lateinit var listGroupPostsUseCase: ListGroupPostsUseCase
    private lateinit var listGroupPlacesUseCase: ListGroupPlacesUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        listGroupsUseCase = mock(ListGroupsUseCase::class.java)
        createGroupUseCase = mock(CreateGroupUseCase::class.java)
        updateGroupUseCase = mock(UpdateGroupUseCase::class.java)
        deleteGroupUseCase = mock(DeleteGroupUseCase::class.java)
        listGroupPostsUseCase = mock(ListGroupPostsUseCase::class.java)
        listGroupPlacesUseCase = mock(ListGroupPlacesUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                GroupController(
                    listGroupsUseCase,
                    createGroupUseCase,
                    updateGroupUseCase,
                    deleteGroupUseCase,
                    listGroupPostsUseCase,
                    listGroupPlacesUseCase,
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
    fun `lists current users groups with post counts`() {
        `when`(listGroupsUseCase(TEST_USER_ID))
            .thenReturn(
                listOf(
                    GroupView(
                        id = 17,
                        name = "카페",
                        color = "YELLOW",
                        postCount = 3,
                        thumbnailUrls = listOf(
                            "https://example.com/latest.jpg",
                            "https://example.com/second.jpg",
                        ),
                    ),
                ),
            )

        mockMvc.get("/api/v1/groups").andExpect {
            status { isOk() }
            jsonPath("$.success[0].id") { value(17) }
            jsonPath("$.success[0].postCount") { value(3) }
            jsonPath("$.success[0].thumbnailUrls[0]") { value("https://example.com/latest.jpg") }
            jsonPath("$.success[0].thumbnailUrls[1]") { value("https://example.com/second.jpg") }
        }
    }

    @Test
    fun `lists saved posts in an owned group`() {
        val query = ListGroupPostsUseCase.Query(
            userId = TEST_USER_ID,
            groupId = 17,
            page = 0,
            size = 20,
        )
        `when`(listGroupPostsUseCase(query)).thenReturn(
            GroupPostPage(
                ownerNickname = "Purr",
                items = listOf(
                    GroupPostSummary(
                        post = SavedPostSummary(
                            postId = 11,
                            title = "성수 카페",
                            authorIdentifier = "nook",
                            representativeMedia = null,
                            memo = null,
                            savedAt = Instant.parse("2026-07-27T00:00:00Z"),
                        ),
                        placeCount = 3,
                    ),
                ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                hasNext = false,
            ),
        )

        mockMvc.get("/api/v1/groups/17/posts?page=0&size=20").andExpect {
            status { isOk() }
            jsonPath("$.success.ownerNickname") { value("Purr") }
            jsonPath("$.success.items[0].postId") { value(11) }
            jsonPath("$.success.items[0].placeCount") { value(3) }
            jsonPath("$.success.items[0].processingPercent") { value(100) }
            jsonPath("$.success.items[0].savedAt") { value("2026-07-27T09:00:00+09:00") }
            jsonPath("$.success.totalElements") { value(1) }
        }
        verify(listGroupPostsUseCase)(query)
    }

    @Test
    fun `lists distinct places in an owned group`() {
        val query = ListGroupPlacesUseCase.Query(
            userId = TEST_USER_ID,
            groupId = 17,
            page = 0,
            size = 20,
        )
        `when`(listGroupPlacesUseCase(query)).thenReturn(
            GroupPlacePage(
                ownerNickname = "Purr",
                items = listOf(
                    GroupPlaceSummary(
                        id = 31,
                        name = "퍼머넌트해비탯",
                        city = "서울",
                        address = "서울 마포구 연희로1길 55",
                        category = "카페",
                        latitude = BigDecimal("37.5"),
                        longitude = BigDecimal("127.0"),
                        thumbnailUrl = "https://example.com/place.jpg",
                        thumbnailParsingStatus = PlaceThumbnailParsingStatusView.COMPLETED,
                        tags = listOf("작업하기 좋은"),
                    ),
                ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                hasNext = false,
            ),
        )

        mockMvc.get("/api/v1/groups/17/places?page=0&size=20").andExpect {
            status { isOk() }
            jsonPath("$.success.ownerNickname") { value("Purr") }
            jsonPath("$.success.items[0].id") { value(31) }
            jsonPath("$.success.items[0].name") { value("퍼머넌트해비탯") }
            jsonPath("$.success.items[0].city") { value("서울") }
            jsonPath("$.success.items[0].thumbnailUrl") { value("https://example.com/place.jpg") }
            jsonPath("$.success.items[0].thumbnailParsingStatus") { value("COMPLETED") }
            jsonPath("$.success.items[0].tags[0]") { value("작업하기 좋은") }
            jsonPath("$.success.totalElements") { value(1) }
        }
        verify(listGroupPlacesUseCase)(query)
    }

    @Test
    fun `creates a group`() {
        val command = CreateGroupUseCase.Command(
            userId = TEST_USER_ID,
            name = "카페",
            color = "YELLOW",
        )
        `when`(createGroupUseCase(command)).thenReturn(GroupView(17, "카페", "YELLOW", 0))

        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"카페","color":"YELLOW"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success.color") { value("YELLOW") }
        }
        verify(createGroupUseCase)(command)
    }

    @Test
    fun `rejects invalid group name and color`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"${"가".repeat(21)}","color":"ORANGE"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `updates and deletes only through current user commands`() {
        val updateCommand = UpdateGroupUseCase.Command(
            userId = TEST_USER_ID,
            groupId = 17,
            name = "서울 카페",
            color = "GREEN",
        )
        `when`(updateGroupUseCase(updateCommand)).thenReturn(GroupView(17, "서울 카페", "GREEN", 3))

        mockMvc.patch("/api/v1/groups/17") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"서울 카페","color":"GREEN"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.name") { value("서울 카페") }
        }
        mockMvc.delete("/api/v1/groups/17").andExpect {
            status { isNoContent() }
        }

        verify(updateGroupUseCase)(updateCommand)
        verify(deleteGroupUseCase)(
            DeleteGroupUseCase.Command(userId = TEST_USER_ID, groupId = 17),
        )
    }
    private companion object {
        const val TEST_USER_ID = 1L
    }
}

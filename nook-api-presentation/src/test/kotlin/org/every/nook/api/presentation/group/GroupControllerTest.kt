package org.every.nook.api.presentation.group

import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.GroupPostPage
import org.every.nook.api.application.group.GroupPostSummary
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.ListGroupPostsUseCase
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
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

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        listGroupsUseCase = mock(ListGroupsUseCase::class.java)
        createGroupUseCase = mock(CreateGroupUseCase::class.java)
        updateGroupUseCase = mock(UpdateGroupUseCase::class.java)
        deleteGroupUseCase = mock(DeleteGroupUseCase::class.java)
        listGroupPostsUseCase = mock(ListGroupPostsUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                GroupController(
                    listGroupsUseCase,
                    createGroupUseCase,
                    updateGroupUseCase,
                    deleteGroupUseCase,
                    listGroupPostsUseCase,
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
            .thenReturn(listOf(GroupView(17, "카페", "YELLOW", 3)))

        mockMvc.get("/api/v1/groups").andExpect {
            status { isOk() }
            jsonPath("$.success[0].id") { value(17) }
            jsonPath("$.success[0].postCount") { value(3) }
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
            jsonPath("$.success.items[0].savedAt") { value("2026-07-27T09:00:00+09:00") }
            jsonPath("$.success.totalElements") { value(1) }
        }
        verify(listGroupPostsUseCase)(query)
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

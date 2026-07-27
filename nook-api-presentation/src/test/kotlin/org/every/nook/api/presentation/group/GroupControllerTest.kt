package org.every.nook.api.presentation.group

import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class GroupControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var listGroupsUseCase: ListGroupsUseCase
    private lateinit var createGroupUseCase: CreateGroupUseCase
    private lateinit var updateGroupUseCase: UpdateGroupUseCase
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase

    @BeforeTest
    fun setUp() {
        listGroupsUseCase = mock(ListGroupsUseCase::class.java)
        createGroupUseCase = mock(CreateGroupUseCase::class.java)
        updateGroupUseCase = mock(UpdateGroupUseCase::class.java)
        deleteGroupUseCase = mock(DeleteGroupUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                GroupController(
                    listGroupsUseCase,
                    createGroupUseCase,
                    updateGroupUseCase,
                    deleteGroupUseCase,
                ),
            )
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `lists current users groups with post counts`() {
        `when`(listGroupsUseCase(UserContextArgumentResolver.DUMMY_USER_ID))
            .thenReturn(listOf(GroupView(17, "카페", "YELLOW", 3)))

        mockMvc.get("/api/v1/groups").andExpect {
            status { isOk() }
            jsonPath("$.success[0].id") { value(17) }
            jsonPath("$.success[0].postCount") { value(3) }
        }
    }

    @Test
    fun `creates a group`() {
        val command = CreateGroupUseCase.Command(
            userId = UserContextArgumentResolver.DUMMY_USER_ID,
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
            userId = UserContextArgumentResolver.DUMMY_USER_ID,
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
            DeleteGroupUseCase.Command(userId = UserContextArgumentResolver.DUMMY_USER_ID, groupId = 17),
        )
    }
}

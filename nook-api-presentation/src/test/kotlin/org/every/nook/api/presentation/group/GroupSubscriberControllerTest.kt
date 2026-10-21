package org.every.nook.api.presentation.group

import org.every.nook.api.application.group.GroupSubscriberPage
import org.every.nook.api.application.group.GroupSubscriberView
import org.every.nook.api.application.group.ListGroupSubscribersUseCase
import org.every.nook.api.application.group.ListSharedGroupSubscribersUseCase
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GroupSubscriberControllerTest {
    private val list = mock(ListGroupSubscribersUseCase::class.java)
    private val shared = mock(ListSharedGroupSubscribersUseCase::class.java)
    private lateinit var mvc: MockMvc
    private val result = GroupSubscriberPage(listOf(GroupSubscriberView("reader", null)), 0, 20, 1, 1, false)

    @BeforeTest
    fun setup() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("7", "", "ROLE_USER")
        val controller = GroupSubscriberController(list, shared)
        val validation = MethodValidationPostProcessor().apply { afterPropertiesSet() }
        mvc = MockMvcBuilders.standaloneSetup(validation.postProcessAfterInitialization(controller, "subscribers"))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun clear() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `member page exposes only nickname profile and paging fields`() {
        `when`(list(ListGroupSubscribersUseCase.Query(7, 17, 0, 20))).thenReturn(result)
        mvc.get("/api/v1/groups/17/subscribers").andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].nickname") { value("reader") }
            jsonPath("$.success.items[0].profileImageUrl") { doesNotExist() }
            jsonPath("$.success.items[0].memberId") { doesNotExist() }
            jsonPath("$.success.totalElements") { value(1) }
            jsonPath("$.success.hasNext") { value(false) }
        }
        verify(list)(ListGroupSubscribersUseCase.Query(7, 17, 0, 20))
    }

    @Test
    fun `valid public token page does not require authentication`() {
        SecurityContextHolder.clearContext()
        `when`(shared("token", 0, 20)).thenReturn(result)
        mvc.get("/api/public/v1/groups/token/subscribers").andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].nickname") { value("reader") }
        }
    }

    @Test
    fun `private page requires authentication and read access`() {
        `when`(list(ListGroupSubscribersUseCase.Query(7, 17, 0, 20))).thenThrow(GroupNotFoundException())
        mvc.get("/api/v1/groups/17/subscribers").andExpect { status { isNotFound() } }
        SecurityContextHolder.clearContext()
        mvc.get("/api/v1/groups/17/subscribers").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `invalid pagination is rejected before querying users`() {
        listOf("page=-1", "size=0", "size=101").forEach { query ->
            mvc.get("/api/public/v1/groups/token/subscribers?$query").andExpect { status { isBadRequest() } }
        }
        verifyNoInteractions(shared)
    }
}

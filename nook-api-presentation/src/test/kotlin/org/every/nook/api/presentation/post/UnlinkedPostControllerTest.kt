package org.every.nook.api.presentation.post

import org.every.nook.api.application.post.ListUnlinkedSavedPostsUseCase
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostSummary
import org.every.nook.api.application.post.port.UnlinkedSavedPostQueryPort
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
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UnlinkedPostControllerTest {
    private val port = mock(UnlinkedSavedPostQueryPort::class.java)
    private lateinit var mvc: MockMvc

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("7", "credentials", "ROLE_USER")
        mvc = MockMvcBuilders.standaloneSetup(UnlinkedPostController(ListUnlinkedSavedPostsUseCase(port)))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns existing saved post response fields and current user's pagination`() {
        val item = SavedPostSummary(11, "장소를 연결해주세요", null, null, null, Instant.EPOCH)
        `when`(port.findUnlinked(7, 1, 20)).thenReturn(SavedPostPage(listOf(item), 1, 20, 21, 2, false))
        mvc.get("/api/v1/posts/unlinked-places?page=1&size=20").andExpect {
            status { isOk() }
            jsonPath("$.success.items[0].postId") { value(11) }
            jsonPath("$.success.totalElements") { value(21) }
            jsonPath("$.success.page") { value(1) }
            jsonPath("$.success.hasNext") { value(false) }
        }
        verify(port).findUnlinked(7, 1, 20)
    }

    @Test
    fun `invalid pagination is rejected before reading posts`() {
        listOf("page=-1", "size=0", "size=101").forEach { parameters ->
            mvc.get("/api/v1/posts/unlinked-places?$parameters").andExpect { status { isBadRequest() } }
        }
        verifyNoInteractions(port)
    }
}

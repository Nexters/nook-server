package org.every.nook.api.presentation.post

import org.every.nook.api.application.post.GetSavedPostRecoveryUseCase
import org.every.nook.api.application.post.RequestSavedPostRecoveryUseCase
import org.every.nook.api.application.post.SavedPostRecovery
import org.every.nook.api.application.post.SavedPostRecoveryPort
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.model.PostProcessingView
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.AfterTest
import kotlin.test.Test

class PostRecoveryControllerTest {
    private val port = mock(SavedPostRecoveryPort::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(
        PostRecoveryController(GetSavedPostRecoveryUseCase(port), RequestSavedPostRecoveryUseCase(port)),
    ).setCustomArgumentResolvers(UserContextArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    init {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("7", "credentials", "ROLE_USER")
    }

    @AfterTest
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `request needs no url or archive and returns accepted processing state`() {
        `when`(port.request(7, 11)).thenReturn(
            SavedPostRecovery(11, PostProcessingView(PostProcessingStatusView.PENDING, null), true),
        )
        mvc.post("/api/v1/posts/11/recovery").andExpect {
            status { isAccepted() }
            jsonPath("$.success.postId") { value(11) }
            jsonPath("$.success.requested") { value(true) }
            jsonPath("$.success.processingStatus") { value("PENDING") }
        }
        verify(port).request(7, 11)
    }

    @Test
    fun `completed post is returned without another processing request`() {
        `when`(port.request(7, 11)).thenReturn(
            SavedPostRecovery(11, PostProcessingView(PostProcessingStatusView.COMPLETED, null)),
        )
        mvc.post("/api/v1/posts/11/recovery").andExpect {
            status { isOk() }
            jsonPath("$.success.requested") { value(false) }
        }
    }

    @Test
    fun `status requires ownership and reports missing posts`() {
        `when`(port.find(7, 12)).thenThrow(PostNotFoundException())
        mvc.get("/api/v1/posts/12/recovery").andExpect { status { isNotFound() } }
    }

    @Test
    fun `unauthenticated request cannot select another user`() {
        SecurityContextHolder.clearContext()
        mvc.post("/api/v1/posts/11/recovery").andExpect { status { isUnauthorized() } }
    }
}

package org.every.nook.api.auth

import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.RefreshLoginTokenUseCase
import org.every.nook.api.application.auth.SocialAuthenticationResult
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

@WebMvcTest(AuthController::class)
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authenticateSocialUserUseCase: AuthenticateSocialUserUseCase

    @MockitoBean
    private lateinit var refreshLoginTokenUseCase: RefreshLoginTokenUseCase

    @Test
    fun `new social user receives signup required response`() {
        val credential = SocialCredential(
            provider = SocialLoginProvider.KAKAO,
            accessToken = "provider-token",
        )
        `when`(authenticateSocialUserUseCase(credential)).thenReturn(
            SocialAuthenticationResult.SignupRequired("signup-token"),
        )

        mockMvc.post("/api/v1/auth/social") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"KAKAO","accessToken":"provider-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SIGNUP_REQUIRED") }
            jsonPath("$.signupToken") { value("signup-token") }
        }
    }

    @Test
    fun `unsupported provider uses common invalid request response`() {
        mockMvc.post("/api/v1/auth/social") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"UNKNOWN","accessToken":"provider-token"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }
}

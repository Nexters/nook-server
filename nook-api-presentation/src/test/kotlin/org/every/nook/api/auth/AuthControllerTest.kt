package org.every.nook.api.auth

import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.LoginTokens
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
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.status") { value("SIGNUP_REQUIRED") }
            jsonPath("$.success.signupToken") { value("signup-token") }
        }
    }

    @Test
    fun `existing social user receives login tokens`() {
        val credential = SocialCredential(
            provider = SocialLoginProvider.KAKAO,
            accessToken = "provider-token",
        )
        `when`(authenticateSocialUserUseCase(credential)).thenReturn(
            SocialAuthenticationResult.SignedIn(LoginTokens("access-token", "refresh-token")),
        )

        mockMvc.post("/api/v1/auth/social") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"KAKAO","accessToken":"provider-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.status") { value("SIGNED_IN") }
            jsonPath("$.success.accessToken") { value("access-token") }
            jsonPath("$.success.refreshToken") { value("refresh-token") }
        }
    }

    @Test
    fun `refresh returns tokens in common success response`() {
        `when`(refreshLoginTokenUseCase("refresh-token")).thenReturn(
            LoginTokens("new-access-token", "new-refresh-token"),
        )

        mockMvc.post("/api/v1/auth/token/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"refresh-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.accessToken") { value("new-access-token") }
            jsonPath("$.success.refreshToken") { value("new-refresh-token") }
        }
    }

    @Test
    fun `invalid social credential uses common unauthorized response`() {
        val credential = SocialCredential(
            provider = SocialLoginProvider.KAKAO,
            accessToken = "invalid-provider-token",
        )
        `when`(authenticateSocialUserUseCase(credential)).thenThrow(InvalidSocialCredentialException())

        mockMvc.post("/api/v1/auth/social") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"KAKAO","accessToken":"invalid-provider-token"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INVALID_SOCIAL_CREDENTIAL") }
            jsonPath("$.error.reason") { value("인증 정보가 유효하지 않습니다.") }
        }
    }

    @Test
    fun `unsupported provider uses common invalid request response`() {
        mockMvc.post("/api/v1/auth/social") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"UNKNOWN","accessToken":"provider-token"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }
}

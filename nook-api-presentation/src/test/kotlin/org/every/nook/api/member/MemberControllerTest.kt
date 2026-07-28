package org.every.nook.api.member

import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.member.DuplicateNicknameException
import org.every.nook.api.application.member.SignupMemberCommand
import org.every.nook.api.application.member.SignupMemberUseCase
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

@WebMvcTest(MemberController::class)
class MemberControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var signupMemberUseCase: SignupMemberUseCase

    @Test
    fun `signup returns login tokens in common success response`() {
        val command = SignupMemberCommand(
            signupToken = "signup-token",
            nickname = "도현",
            profileImageUrl = "https://example.com/profile.jpg",
        )
        `when`(signupMemberUseCase(command)).thenReturn(
            LoginTokens("access-token", "refresh-token"),
        )

        mockMvc.post("/api/v1/members") {
            header("Authorization", "Bearer signup-token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nickname": "도현",
                  "profileImageUrl": "https://example.com/profile.jpg"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.accessToken") { value("access-token") }
            jsonPath("$.success.refreshToken") { value("refresh-token") }
        }
        verify(signupMemberUseCase)(command)
    }

    @Test
    fun `signup without authorization uses common invalid request response`() {
        mockMvc.post("/api/v1/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"도현"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `duplicate nickname uses common conflict response`() {
        val command = SignupMemberCommand(
            signupToken = "signup-token",
            nickname = "도현",
            profileImageUrl = null,
        )
        `when`(signupMemberUseCase(command)).thenThrow(DuplicateNicknameException())

        mockMvc.post("/api/v1/members") {
            header("Authorization", "Bearer signup-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"도현"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("DUPLICATE_NICKNAME") }
            jsonPath("$.error.reason") { value("이미 사용 중인 닉네임입니다.") }
        }
    }
}

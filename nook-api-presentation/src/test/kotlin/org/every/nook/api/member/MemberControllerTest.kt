package org.every.nook.api.member

import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.member.DuplicateNicknameException
import org.every.nook.api.application.member.GetMyMemberUseCase
import org.every.nook.api.application.member.MemberNotFoundException
import org.every.nook.api.application.member.MemberProvider
import org.every.nook.api.application.member.SignupMemberCommand
import org.every.nook.api.application.member.SignupMemberUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MemberControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var signupMemberUseCase: SignupMemberUseCase
    private lateinit var getMyMemberUseCase: GetMyMemberUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        signupMemberUseCase = mock(SignupMemberUseCase::class.java)
        getMyMemberUseCase = mock(GetMyMemberUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(MemberController(signupMemberUseCase, getMyMemberUseCase))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `get me returns member profile with provider`() {
        `when`(getMyMemberUseCase(TEST_USER_ID)).thenReturn(
            GetMyMemberUseCase.Result(
                id = TEST_USER_ID,
                nickname = "누커",
                profileImageUrl = "https://example.com/profile.png",
                provider = MemberProvider.KAKAO,
            ),
        )

        mockMvc.get("/api/v1/members/me").andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.id") { value(TEST_USER_ID) }
            jsonPath("$.success.nickname") { value("누커") }
            jsonPath("$.success.profileImageUrl") { value("https://example.com/profile.png") }
            jsonPath("$.success.provider") { value("KAKAO") }
        }
        verify(getMyMemberUseCase)(TEST_USER_ID)
    }

    @Test
    fun `get me uses common not found response`() {
        `when`(getMyMemberUseCase(TEST_USER_ID)).thenThrow(MemberNotFoundException())

        mockMvc.get("/api/v1/members/me").andExpect {
            status { isNotFound() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("MEMBER_NOT_FOUND") }
        }
    }

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

    private companion object {
        const val TEST_USER_ID = 7L
    }
}

package org.every.nook.api.member

import org.every.nook.api.application.member.DuplicateNicknameException
import org.every.nook.api.application.member.GetMemberProfileUseCase
import org.every.nook.api.application.member.MemberProfile
import org.every.nook.api.application.member.MemberProvider
import org.every.nook.api.application.member.UpdateMemberProfileCommand
import org.every.nook.api.application.member.UpdateMemberProfileUseCase
import org.every.nook.api.application.member.WithdrawMemberUseCase
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val TEST_USER_ID = 7L

class MemberControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var getMemberProfileUseCase: GetMemberProfileUseCase
    private lateinit var updateMemberProfileUseCase: UpdateMemberProfileUseCase
    private lateinit var withdrawMemberUseCase: WithdrawMemberUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        getMemberProfileUseCase = mock(GetMemberProfileUseCase::class.java)
        updateMemberProfileUseCase = mock(UpdateMemberProfileUseCase::class.java)
        withdrawMemberUseCase = mock(WithdrawMemberUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                MemberController(
                    getMemberProfileUseCase,
                    updateMemberProfileUseCase,
                    withdrawMemberUseCase,
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
    fun `gets current member profile with provider`() {
        `when`(getMemberProfileUseCase(TEST_USER_ID))
            .thenReturn(
                MemberProfile(
                    id = TEST_USER_ID,
                    nickname = "누커",
                    profileImageUrl = null,
                    provider = MemberProvider.KAKAO,
                ),
            )

        mockMvc.get("/api/v1/members/me").andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.id") { value(TEST_USER_ID) }
            jsonPath("$.success.nickname") { value("누커") }
            jsonPath("$.success.provider") { value("KAKAO") }
        }
    }

    @Test
    fun `updates current member profile`() {
        val command = UpdateMemberProfileCommand(
            memberId = TEST_USER_ID,
            nickname = "도현",
            profileImageUrl = "https://example.com/profile.jpg",
        )
        `when`(updateMemberProfileUseCase(command))
            .thenReturn(
                MemberProfile(
                    TEST_USER_ID,
                    "도현",
                    "https://example.com/profile.jpg",
                    MemberProvider.KAKAO,
                ),
            )

        mockMvc.patch("/api/v1/members/me") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nickname": "도현",
                  "profileImageUrl": "https://example.com/profile.jpg"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.nickname") { value("도현") }
            jsonPath("$.success.profileImageUrl") { value("https://example.com/profile.jpg") }
            jsonPath("$.success.provider") { value("KAKAO") }
        }
        verify(updateMemberProfileUseCase)(command)
    }

    @Test
    fun `duplicate nickname uses common conflict response`() {
        val command = UpdateMemberProfileCommand(TEST_USER_ID, "도현", null)
        `when`(updateMemberProfileUseCase(command)).thenThrow(DuplicateNicknameException())

        mockMvc.patch("/api/v1/members/me") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"도현"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("DUPLICATE_NICKNAME") }
        }
    }

    @Test
    fun `withdraws current member`() {
        mockMvc.delete("/api/v1/members/me").andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.completed") { value(true) }
        }
        verify(withdrawMemberUseCase)(TEST_USER_ID)
    }
}

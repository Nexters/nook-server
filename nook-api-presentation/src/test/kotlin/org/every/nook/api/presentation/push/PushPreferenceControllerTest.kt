package org.every.nook.api.presentation.push

import org.every.nook.api.application.push.GetPushPreferenceUseCase
import org.every.nook.api.application.push.PushPreference
import org.every.nook.api.application.push.UpdatePushPreferenceUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PushPreferenceControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var getUseCase: GetPushPreferenceUseCase
    private lateinit var updateUseCase: UpdatePushPreferenceUseCase

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(TEST_USER_ID.toString(), "credentials", "ROLE_USER")
        getUseCase = mock(GetPushPreferenceUseCase::class.java)
        updateUseCase = mock(UpdatePushPreferenceUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(PushPreferenceController(getUseCase, updateUseCase))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `gets current users push preference`() {
        `when`(getUseCase(GetPushPreferenceUseCase.Query(TEST_USER_ID))).thenReturn(PushPreference(true))

        mockMvc.get("/api/v1/me/push-preferences").andExpect {
            status { isOk() }
            jsonPath("$.success.postProcessingEnabled") { value(true) }
        }
    }

    @Test
    fun `updates current users push preference`() {
        val command = UpdatePushPreferenceUseCase.Command(TEST_USER_ID, false)
        `when`(updateUseCase(command)).thenReturn(PushPreference(false))

        mockMvc.patch("/api/v1/me/push-preferences") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"postProcessingEnabled":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.postProcessingEnabled") { value(false) }
        }
        verify(updateUseCase)(command)
    }

    private companion object {
        const val TEST_USER_ID = 7L
    }
}

package org.every.nook.api.presentation.reminder

import org.every.nook.api.application.reminder.GetPlaceReminderUseCase
import org.every.nook.api.application.reminder.PlaceReminderView
import org.every.nook.api.application.reminder.UpdatePlaceReminderUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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

class PlaceReminderControllerTest {
    private val get = mock(GetPlaceReminderUseCase::class.java)
    private val update = mock(UpdatePlaceReminderUseCase::class.java)
    private lateinit var mvc: MockMvc

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("7", "credentials", "ROLE_USER")
        mvc = MockMvcBuilders.standaloneSetup(PlaceReminderController(get, update))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns banner state for current user and accepts an independent OFF setting`() {
        `when`(get(GetPlaceReminderUseCase.Query(7))).thenReturn(PlaceReminderView(true, 2))
        mvc.get("/api/v1/me/place-connection-reminder").andExpect {
            status { isOk() }
            jsonPath("$.success.enabled") { value(true) }
            jsonPath("$.success.unlinkedPostCount") { value(2) }
            jsonPath("$.success.showBanner") { value(true) }
        }
        val command = UpdatePlaceReminderUseCase.Command(7, false)
        `when`(update(command)).thenReturn(PlaceReminderView(false, 2))
        mvc.patch("/api/v1/me/place-connection-reminder") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.enabled") { value(false) }
            jsonPath("$.success.unlinkedPostCount") { value(2) }
            jsonPath("$.success.showBanner") { value(false) }
        }
        verify(update)(command)
    }

    @Test
    fun `rejects omitted null and non boolean settings without mutating preferences`() {
        listOf("{}", """{"enabled":null}""", """{"enabled":"invalid"}""").forEach { body ->
            mvc.patch("/api/v1/me/place-connection-reminder") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
        verifyNoInteractions(update)
    }
}

package org.every.nook.api.presentation.onboarding

import org.every.nook.api.application.onboarding.OnboardingEvent
import org.every.nook.api.application.onboarding.OnboardingEventStorePort
import org.every.nook.api.application.onboarding.RecordOnboardingEventUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingEventControllerTest {
    private val stored = mutableListOf<OnboardingEvent>()
    private val now = Instant.parse("2026-10-07T14:00:00Z")

    @AfterTest
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `uses authenticated identity and returns empty 204 only after persistence`() {
        authenticate()
        mvc().post(PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }
        assertEquals(7L, stored.single().memberId)
    }

    @Test
    fun `rejects missing authentication unsupported names invalid times and ids`() {
        val mvc = mvc()
        mvc.post(PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }
            .andExpect { status { isUnauthorized() } }
        authenticate()
        listOf(
            body().replace("INSTAGRAM_CTA_CLICK", "POST_SAVE"),
            body().replace("2026-10-07", "2026-10-08"),
            body().replace("60d86756-c4cd-4e67-bcca-c6b08cecd4a4", "invalid"),
        ).forEach { invalid ->
            mvc.post(PATH) {
                contentType = MediaType.APPLICATION_JSON
                content = invalid
            }
                .andExpect { status { isBadRequest() } }
        }
        assertEquals(0, stored.size)
    }

    @Test
    fun `storage outage is a retriable server error`() {
        authenticate()
        mvc(OnboardingEventStorePort { error("offline") }).post(PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.resultType") { value("FAIL") }
        }
    }

    private fun mvc(store: OnboardingEventStorePort = OnboardingEventStorePort(stored::add)) =
        MockMvcBuilders.standaloneSetup(
            OnboardingEventController(RecordOnboardingEventUseCase(store, Clock.fixed(now, ZoneOffset.UTC))),
        ).setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler()).build()

    private fun authenticate() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("7", "credentials", "ROLE_USER")
    }

    private fun body() = """
        {"eventId":"60d86756-c4cd-4e67-bcca-c6b08cecd4a4",
         "eventName":"INSTAGRAM_CTA_CLICK", "occurredAt":"2026-10-07T14:00:00Z"}
    """.trimIndent()

    private companion object {
        const val PATH = "/api/v1/onboarding/events"
    }
}

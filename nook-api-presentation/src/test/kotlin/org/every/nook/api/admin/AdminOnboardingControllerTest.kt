package org.every.nook.api.admin

import org.every.nook.api.application.onboarding.GetOnboardingConversionUseCase
import org.every.nook.api.application.onboarding.OnboardingConversionQueryPort
import org.every.nook.api.application.onboarding.OnboardingJourney
import org.every.nook.api.application.onboarding.OnboardingSave
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

class AdminOnboardingControllerTest {
    private val now = Instant.parse("2026-10-07T14:00:00Z")

    @Test
    fun `response distinguishes provisional and completed rates and preserves empty null metrics`() {
        val click = now.minusSeconds(60)
        val journeys = listOf(OnboardingJourney(click, listOf(OnboardingSave(1, click)), emptyList()))
        mvc(journeys).get(PATH).andExpect {
            status { isOk() }
            jsonPath("$.success.clickedUsers") { value(1) }
            jsonPath("$.success.windows[1].provisionalRate") { value(100.0) }
            jsonPath("$.success.windows[1].eligibleUsers") { value(0) }
            jsonPath("$.success.windows[1].completedRate") { value(null) }
            jsonPath("$.success.pendingDayUsers") { value(1) }
            jsonPath("$.success.medianFirstSaveSeconds") { value(null) }
            jsonPath("$.success.extension") { value(null) }
        }
    }

    @Test
    fun `invalid reversed or excessive date range returns 400`() {
        listOf("from=2026-10-07&to=2026-10-01", "from=2026-01-01&to=2026-10-07").forEach { query ->
            mvc(emptyList()).get("/api/admin/v1/onboarding-conversion?$query").andExpect { status { isBadRequest() } }
        }
    }

    private fun mvc(journeys: List<OnboardingJourney>) = MockMvcBuilders.standaloneSetup(
        AdminOnboardingController(
            GetOnboardingConversionUseCase(
                OnboardingConversionQueryPort { _, _ -> journeys },
                Clock.fixed(now, ZoneOffset.UTC),
            ),
        ),
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    private companion object {
        const val PATH = "/api/admin/v1/onboarding-conversion?from=2026-10-01&to=2026-10-07"
    }
}

package org.every.nook.api.presentation.onboarding

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.onboarding.OnboardingEvent
import org.every.nook.api.application.onboarding.OnboardingEventName
import org.every.nook.api.application.onboarding.RecordOnboardingEventUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Tag(name = "OnboardingEvents")
@RestController
@RequestMapping("/api/v1/onboarding/events")
class OnboardingEventController(private val record: RecordOnboardingEventUseCase) {
    @Operation(summary = "온보딩 Instagram 클릭 또는 Share Extension 진입 기록")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun record(@Parameter(hidden = true) userContext: UserContext, @RequestBody request: OnboardingEventRequest) {
        record(OnboardingEvent(userContext.userId, request.eventId, request.eventName, request.occurredAt))
    }
}

data class OnboardingEventRequest(
    @field:Schema(description = "발생마다 생성하는 UUID. 재시도 시 같은 값을 사용합니다.")
    val eventId: UUID,
    @field:Schema(description = "INSTAGRAM_CTA_CLICK 또는 SHARE_EXTENSION_OPEN")
    val eventName: OnboardingEventName,
    @field:Schema(description = "실제 발생 시각 UTC ISO-8601. 서버 현재 시각부터 과거 7일 이내, 미래 불가.")
    val occurredAt: Instant,
)

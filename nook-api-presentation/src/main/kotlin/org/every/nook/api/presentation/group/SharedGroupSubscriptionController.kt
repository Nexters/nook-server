package org.every.nook.api.presentation.group

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.SubscribeSharedGroupUseCase
import org.every.nook.api.application.group.UnsubscribeSharedGroupUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Shared Group")
@Validated
@RestController
@RequestMapping("/api/v1/shared-groups")
class SharedGroupSubscriptionController(
    private val subscribeUseCase: SubscribeSharedGroupUseCase,
    private val unsubscribeUseCase: UnsubscribeSharedGroupUseCase,
) {
    @Operation(summary = "공유 그룹을 내 아카이브에 추가")
    @PutMapping("/{token}")
    fun subscribe(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "공유 링크 토큰") @PathVariable token: String,
    ): ApiResponse<Unit> {
        subscribeUseCase(userContext.userId, token)
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "공유 그룹을 내 아카이브에서 제거")
    @DeleteMapping("/{groupId}")
    fun unsubscribe(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "제거할 공유 그룹 식별자") @PathVariable @Positive groupId: Long,
    ): ApiResponse<Unit> {
        unsubscribeUseCase(userContext.userId, groupId)
        return ApiResponse.success(Unit)
    }
}

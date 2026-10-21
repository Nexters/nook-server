package org.every.nook.api.presentation.group

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.ListGroupSubscribersUseCase
import org.every.nook.api.application.group.ListSharedGroupSubscribersUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.group.response.GroupSubscriberPageResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MAX_SUBSCRIBER_PAGE_SIZE = 100L

@Tag(name = "Group")
@Validated
@RestController
class GroupSubscriberController(
    private val listUseCase: ListGroupSubscribersUseCase,
    private val sharedUseCase: ListSharedGroupSubscribersUseCase,
) {
    @Operation(summary = "내가 열람 가능한 아카이브를 추가한 사람 조회")
    @GetMapping("/api/v1/groups/{groupId}/subscribers")
    fun list(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "아카이브 식별자") @PathVariable @Positive groupId: Long,
        @Parameter(description = "페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,
        @Parameter(description = "페이지당 사용자 수. 최대 100명입니다.")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_SUBSCRIBER_PAGE_SIZE) size: Int,
    ): ApiResponse<GroupSubscriberPageResponse> = ApiResponse.success(
        GroupSubscriberPageResponse.from(
            listUseCase(ListGroupSubscribersUseCase.Query(userContext.userId, groupId, page, size)),
        ),
    )

    @Operation(summary = "공유 아카이브를 추가한 사람 조회")
    @GetMapping("/api/public/v1/groups/{token}/subscribers")
    fun shared(
        @Parameter(description = "유효한 아카이브 공유 토큰") @PathVariable token: String,
        @Parameter(description = "페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,
        @Parameter(description = "페이지당 사용자 수. 최대 100명입니다.")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_SUBSCRIBER_PAGE_SIZE) size: Int,
    ): ApiResponse<GroupSubscriberPageResponse> = ApiResponse.success(
        GroupSubscriberPageResponse.from(sharedUseCase(token, page, size)),
    )
}

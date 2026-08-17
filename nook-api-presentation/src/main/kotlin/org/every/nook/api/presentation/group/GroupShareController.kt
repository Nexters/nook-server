package org.every.nook.api.presentation.group

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.IssueGroupShareLinkUseCase
import org.every.nook.api.application.group.RevokeGroupShareLinkUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.group.response.GroupShareLinkResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Group Share")
@Validated
@RestController
@RequestMapping("/api/v1/groups")
class GroupShareController(
    private val issueUseCase: IssueGroupShareLinkUseCase,
    private val revokeUseCase: RevokeGroupShareLinkUseCase,
) {
    @Operation(summary = "그룹 공유 링크 발급")
    @PutMapping("/{groupId}/share-link")
    fun issue(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "공유할 그룹 식별자") @PathVariable @Positive groupId: Long,
    ): ApiResponse<GroupShareLinkResponse> = ApiResponse.success(
        GroupShareLinkResponse.from(issueUseCase(IssueGroupShareLinkUseCase.Command(userContext.userId, groupId))),
    )

    @Operation(summary = "그룹 공유 링크 해제")
    @DeleteMapping("/{groupId}/share-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "공유를 해제할 그룹 식별자") @PathVariable @Positive groupId: Long,
    ) {
        revokeUseCase(userContext.userId, groupId)
    }
}

package org.every.nook.api.presentation.group

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.ListGroupPostsUseCase
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.group.request.CreateGroupRequest
import org.every.nook.api.presentation.group.request.UpdateGroupRequest
import org.every.nook.api.presentation.group.response.GroupPostPageResponse
import org.every.nook.api.presentation.group.response.GroupResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val MAX_GROUP_POST_PAGE_SIZE = 100L

@Tag(name = "Group")
@Validated
@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val listGroupsUseCase: ListGroupsUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val listGroupPostsUseCase: ListGroupPostsUseCase,
) {
    @Operation(summary = "내 그룹 목록 조회")
    @GetMapping
    fun list(@Parameter(hidden = true) userContext: UserContext): ApiResponse<List<GroupResponse>> =
        ApiResponse.success(listGroupsUseCase(userContext.userId).map(GroupResponse::from))

    @Operation(summary = "그룹 저장 게시물 목록 조회")
    @GetMapping("/{groupId}/posts")
    fun listPosts(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "조회할 그룹 식별자")
        @PathVariable
        @Positive
        groupId: Long,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_GROUP_POST_PAGE_SIZE)
        size: Int,
    ): ApiResponse<GroupPostPageResponse> {
        val result = listGroupPostsUseCase(
            ListGroupPostsUseCase.Query(
                userId = userContext.userId,
                groupId = groupId,
                page = page,
                size = size,
            ),
        )
        return ApiResponse.success(GroupPostPageResponse.from(result))
    }

    @Operation(summary = "그룹 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: CreateGroupRequest,
    ): ApiResponse<GroupResponse> = ApiResponse.success(
        GroupResponse.from(
            createGroupUseCase(
                CreateGroupUseCase.Command(
                    userId = userContext.userId,
                    name = request.name,
                    color = request.color,
                ),
            ),
        ),
    )

    @Operation(summary = "그룹명과 색상 수정")
    @PatchMapping("/{groupId}")
    fun update(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "수정할 그룹 식별자")
        @PathVariable
        @Positive
        groupId: Long,
        @Valid @RequestBody request: UpdateGroupRequest,
    ): ApiResponse<GroupResponse> = ApiResponse.success(
        GroupResponse.from(
            updateGroupUseCase(
                UpdateGroupUseCase.Command(
                    userId = userContext.userId,
                    groupId = groupId,
                    name = request.name,
                    color = request.color,
                ),
            ),
        ),
    )

    @Operation(summary = "그룹 삭제")
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "삭제할 그룹 식별자")
        @PathVariable
        @Positive
        groupId: Long,
    ) {
        deleteGroupUseCase(DeleteGroupUseCase.Command(userId = userContext.userId, groupId = groupId))
    }
}

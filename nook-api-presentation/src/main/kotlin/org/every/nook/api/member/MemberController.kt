package org.every.nook.api.member

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.every.nook.api.application.member.GetMemberProfileUseCase
import org.every.nook.api.application.member.MemberProfile
import org.every.nook.api.application.member.MemberProvider
import org.every.nook.api.application.member.UpdateMemberProfileCommand
import org.every.nook.api.application.member.UpdateMemberProfileUseCase
import org.every.nook.api.application.member.WithdrawMemberUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member", description = "회원 API")
class MemberController(
    private val getMemberProfileUseCase: GetMemberProfileUseCase,
    private val updateMemberProfileUseCase: UpdateMemberProfileUseCase,
    private val withdrawMemberUseCase: WithdrawMemberUseCase,
) {
    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    fun getMe(@Parameter(hidden = true) userContext: UserContext): ResponseEntity<ApiResponse<MemberProfileResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(MemberProfileResponse.from(getMemberProfileUseCase(userContext.userId))),
        )

    @Operation(summary = "내 정보 수정")
    @PatchMapping("/me")
    fun updateMe(
        @Parameter(hidden = true)
        userContext: UserContext,
        @Valid @RequestBody request: UpdateMemberProfileRequest,
    ): ResponseEntity<ApiResponse<MemberProfileResponse>> = ResponseEntity.ok(
        ApiResponse.success(
            MemberProfileResponse.from(
                updateMemberProfileUseCase(
                    UpdateMemberProfileCommand(
                        memberId = userContext.userId,
                        nickname = request.nickname,
                        profileImageUrl = request.profileImageUrl,
                    ),
                ),
            ),
        ),
    )

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) userContext: UserContext,
    ): ResponseEntity<ApiResponse<MemberActionResponse>> {
        withdrawMemberUseCase(userContext.userId)
        return ResponseEntity.ok(ApiResponse.success(MemberActionResponse()))
    }
}

data class UpdateMemberProfileRequest(
    @field:Schema(description = "회원 닉네임", example = "누커", minLength = 2, maxLength = 20)
    @field:NotBlank
    @field:Size(min = 2, max = 20)
    val nickname: String,
    @field:Schema(
        description = "프로필 이미지 URL",
        example = "https://example.com/profile.png",
        maxLength = 2048,
        nullable = true,
    )
    @field:Size(max = 2048)
    @field:Pattern(regexp = "^https://.+")
    val profileImageUrl: String? = null,
)

data class MemberProfileResponse(
    @field:Schema(description = "회원 식별자")
    val id: Long,
    @field:Schema(description = "회원 닉네임")
    val nickname: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true)
    val profileImageUrl: String?,
    @field:Schema(description = "가입한 소셜 로그인 provider")
    val provider: MemberProviderResponse,
) {
    companion object {
        fun from(profile: MemberProfile): MemberProfileResponse = MemberProfileResponse(
            id = profile.id,
            nickname = profile.nickname,
            profileImageUrl = profile.profileImageUrl,
            provider = MemberProviderResponse.from(profile.provider),
        )
    }
}

data class MemberActionResponse(
    @field:Schema(description = "처리 완료 여부")
    val completed: Boolean = true,
)

enum class MemberProviderResponse {
    KAKAO,
    GOOGLE,
    APPLE,
    ;

    companion object {
        fun from(provider: MemberProvider): MemberProviderResponse = valueOf(provider.name)
    }
}

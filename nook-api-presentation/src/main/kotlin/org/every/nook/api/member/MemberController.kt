package org.every.nook.api.member

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.every.nook.api.application.member.CreateProfileImageUploadCommand
import org.every.nook.api.application.member.CreateProfileImageUploadUseCase
import org.every.nook.api.application.member.GetMemberProfileUseCase
import org.every.nook.api.application.member.MemberProfile
import org.every.nook.api.application.member.MemberProvider
import org.every.nook.api.application.member.ProfileImageUrlUpdate
import org.every.nook.api.application.member.UpdateMemberProfileCommand
import org.every.nook.api.application.member.UpdateMemberProfileUseCase
import org.every.nook.api.application.member.WithdrawMemberUseCase
import org.every.nook.api.application.member.port.ProfileImageUpload
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member", description = "회원 API")
class MemberController(
    private val getMemberProfileUseCase: GetMemberProfileUseCase,
    private val updateMemberProfileUseCase: UpdateMemberProfileUseCase,
    private val createProfileImageUploadUseCase: CreateProfileImageUploadUseCase,
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
                        profileImageUrl = request.profileImageUrlUpdate(),
                    ),
                ),
            ),
        ),
    )

    @Operation(summary = "프로필 이미지 업로드 URL 발급")
    @PostMapping("/me/profile-image-upload")
    fun createProfileImageUpload(
        @Parameter(hidden = true)
        userContext: UserContext,
        @Valid @RequestBody request: CreateProfileImageUploadRequest,
    ): ResponseEntity<ApiResponse<ProfileImageUploadResponse>> = ResponseEntity.ok(
        ApiResponse.success(
            ProfileImageUploadResponse.from(
                createProfileImageUploadUseCase(
                    CreateProfileImageUploadCommand(
                        memberId = userContext.userId,
                        contentType = request.contentType,
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

class UpdateMemberProfileRequest {
    @field:Schema(description = "회원 닉네임", example = "누커", minLength = 2, maxLength = 20)
    @field:Size(min = 2, max = 20)
    var nickname: String? = null
        private set

    @field:Schema(
        description = "프로필 이미지 URL",
        example = "https://example.com/profile.png",
        maxLength = 2048,
        nullable = true,
    )
    @field:Size(max = 2048)
    @field:Pattern(regexp = "^https://.+")
    var profileImageUrl: String? = null
        private set

    @get:JsonIgnore
    @field:Schema(hidden = true)
    var nicknameProvided: Boolean = false
        private set

    @get:JsonIgnore
    @field:Schema(hidden = true)
    var profileImageUrlProvided: Boolean = false
        private set

    @JsonSetter("nickname")
    fun setNicknameValue(value: String?) {
        nickname = value
        nicknameProvided = true
    }

    @JsonSetter("profileImageUrl")
    fun setProfileImageUrlValue(value: String?) {
        profileImageUrl = value
        profileImageUrlProvided = true
    }

    @AssertTrue(message = "At least one profile field must be provided")
    fun hasChange(): Boolean = nicknameProvided || profileImageUrlProvided

    @AssertTrue(message = "Nickname must not be blank")
    fun hasValidNickname(): Boolean = !nicknameProvided || !nickname.isNullOrBlank()

    fun profileImageUrlUpdate(): ProfileImageUrlUpdate = if (profileImageUrlProvided) {
        ProfileImageUrlUpdate.Replace(profileImageUrl)
    } else {
        ProfileImageUrlUpdate.Unchanged
    }
}

data class CreateProfileImageUploadRequest(
    @field:Schema(description = "업로드할 이미지 Content-Type", example = "image/jpeg")
    @field:Pattern(regexp = "^image/(jpeg|png|webp|heic|heif)$")
    val contentType: String,
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

data class ProfileImageUploadResponse(
    @field:Schema(description = "이미지를 업로드할 presigned PUT URL")
    val uploadUrl: String,
    @field:Schema(description = "업로드 완료 후 프로필에 저장할 공개 이미지 URL")
    val profileImageUrl: String,
    @field:Schema(description = "업로드 HTTP method")
    val method: String = "PUT",
    @field:Schema(description = "업로드 시 포함할 Content-Type")
    val contentType: String,
    @field:Schema(description = "업로드 PUT 요청에 그대로 포함할 헤더")
    val headers: Map<String, String>,
    @field:Schema(description = "업로드 URL 만료 시각")
    val expiresAt: String,
    @field:Schema(description = "업로드 가능한 최대 바이트")
    val maxBytes: Long,
) {
    companion object {
        fun from(upload: ProfileImageUpload): ProfileImageUploadResponse = ProfileImageUploadResponse(
            uploadUrl = upload.uploadUrl,
            profileImageUrl = upload.profileImageUrl,
            contentType = upload.contentType,
            headers = upload.headers,
            expiresAt = upload.expiresAt.toString(),
            maxBytes = upload.maxBytes,
        )
    }
}

enum class MemberProviderResponse {
    KAKAO,
    GOOGLE,
    APPLE,
    ;

    companion object {
        fun from(provider: MemberProvider): MemberProviderResponse = valueOf(provider.name)
    }
}

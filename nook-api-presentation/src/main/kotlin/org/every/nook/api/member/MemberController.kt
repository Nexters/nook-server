package org.every.nook.api.member

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.every.nook.api.application.auth.InvalidSignupTokenException
import org.every.nook.api.application.member.GetMyMemberUseCase
import org.every.nook.api.application.member.MemberProvider
import org.every.nook.api.application.member.SignupMemberCommand
import org.every.nook.api.application.member.SignupMemberUseCase
import org.every.nook.api.auth.TokenResponse
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val BEARER_PREFIX = "Bearer "

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member", description = "회원 API")
class MemberController(
    private val signupMemberUseCase: SignupMemberUseCase,
    private val getMyMemberUseCase: GetMyMemberUseCase,
) {
    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    fun getMe(@Parameter(hidden = true) userContext: UserContext): ResponseEntity<ApiResponse<MyMemberResponse>> =
        ok(ApiResponse.success(MyMemberResponse.from(getMyMemberUseCase(userContext.userId))))

    @Operation(summary = "회원가입", security = [])
    @PostMapping
    fun signup(
        @Parameter(description = "소셜 로그인 후 발급받은 signup token")
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @Valid @RequestBody request: SignupMemberRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val signupToken = authorization.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf(String::isNotBlank)
            ?: throw InvalidSignupTokenException()
        val tokens = signupMemberUseCase(
            SignupMemberCommand(
                signupToken = signupToken,
                nickname = request.nickname,
                profileImageUrl = request.profileImageUrl,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(TokenResponse.from(tokens)),
        )
    }
}

data class SignupMemberRequest(
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

data class MyMemberResponse(
    @field:Schema(description = "회원 ID", example = "1")
    val id: Long,
    @field:Schema(description = "회원 닉네임", example = "누커")
    val nickname: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true)
    val profileImageUrl: String?,
    @field:Schema(description = "가입한 소셜 로그인 provider")
    val provider: MemberProviderResponse,
) {
    companion object {
        fun from(result: GetMyMemberUseCase.Result): MyMemberResponse = MyMemberResponse(
            id = result.id,
            nickname = result.nickname,
            profileImageUrl = result.profileImageUrl,
            provider = MemberProviderResponse.from(result.provider),
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

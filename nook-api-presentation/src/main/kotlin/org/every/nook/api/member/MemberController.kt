package org.every.nook.api.member

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.every.nook.api.application.auth.InvalidSignupTokenException
import org.every.nook.api.application.member.SignupMemberCommand
import org.every.nook.api.application.member.SignupMemberUseCase
import org.every.nook.api.auth.TokenResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val BEARER_PREFIX = "Bearer "

@RestController
@RequestMapping("/api/v1/members")
class MemberController(private val signupMemberUseCase: SignupMemberUseCase) {
    @PostMapping
    fun signup(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @Valid @RequestBody request: SignupMemberRequest,
    ): ResponseEntity<TokenResponse> {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(TokenResponse.from(tokens))
    }
}

data class SignupMemberRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 20)
    val nickname: String,
    @field:Size(max = 2048)
    @field:Pattern(regexp = "^https://.+")
    val profileImageUrl: String? = null,
)

package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.IssueLoginTokens
import org.every.nook.api.application.auth.RefreshLoginTokenUseCase
import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.SocialIdentityProvider
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.SignupMemberUseCase
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.infrastructure.auth.AppleAuthProperties
import org.every.nook.api.infrastructure.auth.AppleClientSecretGenerator
import org.every.nook.api.infrastructure.auth.AppleSocialIdentityProvider
import org.every.nook.api.infrastructure.auth.CompositeSocialIdentityProvider
import org.every.nook.api.infrastructure.auth.JwtProperties
import org.every.nook.api.infrastructure.auth.JwtTokenProvider
import org.every.nook.api.infrastructure.auth.KakaoAuthProperties
import org.every.nook.api.infrastructure.auth.KakaoSocialIdentityProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestClient
import java.time.Clock
import javax.crypto.spec.SecretKeySpec

private const val APPLE_ISSUER = "https://appleid.apple.com"

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties::class, AppleAuthProperties::class, KakaoAuthProperties::class)
class AuthConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun tokenProvider(properties: JwtProperties, clock: Clock): TokenProvider = JwtTokenProvider(properties, clock)

    @Bean
    fun accessJwtDecoder(properties: JwtProperties): org.springframework.security.oauth2.jwt.JwtDecoder =
        NimbusJwtDecoder.withSecretKey(
            SecretKeySpec(properties.accessSecret.toByteArray(), "HmacSHA256"),
        ).macAlgorithm(MacAlgorithm.HS256).build().apply {
            val issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer)
            val tokenTypeValidator = OAuth2TokenValidator<Jwt> { jwt ->
                if (jwt.getClaimAsString("token_type") == "access") {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Invalid token type", null))
                }
            }
            setJwtValidator(DelegatingOAuth2TokenValidator(issuerValidator, tokenTypeValidator))
        }

    @Bean
    fun socialIdentityProvider(
        restClientBuilder: RestClient.Builder,
        properties: AppleAuthProperties,
        kakaoProperties: KakaoAuthProperties,
        clock: Clock,
    ): SocialIdentityProvider {
        val kakao = KakaoSocialIdentityProvider(
            restClientBuilder.baseUrl("https://kapi.kakao.com").build(),
            kakaoProperties,
        )
        val decoder = NimbusJwtDecoder.withJwkSetUri("$APPLE_ISSUER/auth/keys").build().apply {
            val issuerValidator = JwtValidators.createDefaultWithIssuer(APPLE_ISSUER)
            val audienceValidator = OAuth2TokenValidator<Jwt> { jwt ->
                if (jwt.audience.contains(properties.clientId)) {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Invalid audience", null))
                }
            }
            setJwtValidator(DelegatingOAuth2TokenValidator(issuerValidator, audienceValidator))
        }
        val apple = AppleSocialIdentityProvider(
            restClient = restClientBuilder.baseUrl(APPLE_ISSUER).build(),
            jwtDecoder = decoder,
            clientSecretGenerator = AppleClientSecretGenerator(properties, clock),
            properties = properties,
        )
        return CompositeSocialIdentityProvider(kakao, apple)
    }

    @Bean
    fun issueLoginTokens(tokenProvider: TokenProvider, refreshTokenRepository: RefreshTokenRepository) =
        IssueLoginTokens(tokenProvider, refreshTokenRepository)

    @Bean
    fun authenticateSocialUserUseCase(
        socialIdentityProvider: SocialIdentityProvider,
        memberRepository: MemberRepository,
        tokenProvider: TokenProvider,
        issueLoginTokens: IssueLoginTokens,
        transactionRunner: TransactionRunner,
    ) = AuthenticateSocialUserUseCase(
        socialIdentityProvider,
        memberRepository,
        tokenProvider,
        issueLoginTokens,
        transactionRunner,
    )

    @Bean
    fun signupMemberUseCase(
        tokenProvider: TokenProvider,
        memberRepository: MemberRepository,
        issueLoginTokens: IssueLoginTokens,
        transactionRunner: TransactionRunner,
    ) = SignupMemberUseCase(tokenProvider, memberRepository, issueLoginTokens, transactionRunner)

    @Bean
    fun refreshLoginTokenUseCase(
        tokenProvider: TokenProvider,
        refreshTokenRepository: RefreshTokenRepository,
        memberRepository: MemberRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ) = RefreshLoginTokenUseCase(
        tokenProvider,
        refreshTokenRepository,
        memberRepository,
        transactionRunner,
        clock,
    )
}

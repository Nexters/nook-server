package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.member.CreateProfileImageUploadUseCase
import org.every.nook.api.application.member.GetMemberProfileUseCase
import org.every.nook.api.application.member.LogoutMemberUseCase
import org.every.nook.api.application.member.UpdateMemberProfileUseCase
import org.every.nook.api.application.member.WithdrawMemberUseCase
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.member.port.ProfileImageUploadPort
import org.every.nook.api.application.port.TransactionRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class MemberUseCaseConfig {
    @Bean
    fun getMemberProfileUseCase(memberRepository: MemberRepository) = GetMemberProfileUseCase(memberRepository)

    @Bean
    fun updateMemberProfileUseCase(memberRepository: MemberRepository, transactionRunner: TransactionRunner) =
        UpdateMemberProfileUseCase(memberRepository, transactionRunner)

    @Bean
    fun createProfileImageUploadUseCase(
        memberRepository: MemberRepository,
        profileImageUploadPort: ProfileImageUploadPort,
    ) = CreateProfileImageUploadUseCase(memberRepository, profileImageUploadPort)

    @Bean
    fun logoutMemberUseCase(
        refreshTokenRepository: RefreshTokenRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ) = LogoutMemberUseCase(refreshTokenRepository, transactionRunner, clock)

    @Bean
    fun withdrawMemberUseCase(
        memberRepository: MemberRepository,
        refreshTokenRepository: RefreshTokenRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ) = WithdrawMemberUseCase(memberRepository, refreshTokenRepository, transactionRunner, clock)
}

package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.post.share.GetPostShareUseCase
import org.every.nook.api.application.post.share.IssuePostShareUseCase
import org.every.nook.api.application.post.share.PostSharePort
import org.every.nook.api.application.post.share.SavePostShareUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PostShareConfig {
    @Bean
    fun issuePostShare(port: PostSharePort) = IssuePostShareUseCase(port)

    @Bean
    fun getPostShare(port: PostSharePort) = GetPostShareUseCase(port)

    @Bean
    fun savePostShare(port: PostSharePort) = SavePostShareUseCase(port)
}

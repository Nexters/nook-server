package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.group.GetSharedGroupUseCase
import org.every.nook.api.application.group.GetSharedPlaceDetailUseCase
import org.every.nook.api.application.group.GetSharedPostDetailUseCase
import org.every.nook.api.application.group.IssueGroupShareLinkUseCase
import org.every.nook.api.application.group.ListSharedGroupPlacesUseCase
import org.every.nook.api.application.group.ListSharedGroupPostsUseCase
import org.every.nook.api.application.group.RevokeGroupShareLinkUseCase
import org.every.nook.api.application.group.SubscribeSharedGroupUseCase
import org.every.nook.api.application.group.UnsubscribeSharedGroupUseCase
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.group.port.GroupPlaceQueryPort
import org.every.nook.api.application.group.port.GroupPostQueryPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.group.port.SharedPostViewerQueryPort
import org.every.nook.api.application.place.port.SharedPlaceDetailQueryPort
import org.every.nook.api.application.post.SaveSharedPostUseCase
import org.every.nook.api.application.post.port.SaveSharedPostPort
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GroupShareUseCaseConfig {
    @Bean
    fun issueGroupShareLinkUseCase(port: GroupSharePort) = IssueGroupShareLinkUseCase(port)

    @Bean
    fun revokeGroupShareLinkUseCase(port: GroupSharePort) = RevokeGroupShareLinkUseCase(port)

    @Bean
    fun getSharedGroupUseCase(port: GroupSharePort) = GetSharedGroupUseCase(port)

    @Bean
    fun subscribeSharedGroupUseCase(port: GroupSharePort) = SubscribeSharedGroupUseCase(port)

    @Bean
    fun unsubscribeSharedGroupUseCase(port: GroupSharePort) = UnsubscribeSharedGroupUseCase(port)

    @Bean
    fun listSharedGroupPostsUseCase(port: GroupSharePort, queryPort: GroupPostQueryPort) =
        ListSharedGroupPostsUseCase(port, queryPort)

    @Bean
    fun listSharedGroupPlacesUseCase(port: GroupSharePort, queryPort: GroupPlaceQueryPort) =
        ListSharedGroupPlacesUseCase(port, queryPort)

    @Bean
    fun getSharedPostDetailUseCase(
        port: GroupSharePort,
        queryPort: SavedPostQueryPort,
        viewerQueryPort: SharedPostViewerQueryPort,
    ) = GetSharedPostDetailUseCase(port, queryPort, viewerQueryPort)

    @Bean
    fun getSharedPlaceDetailUseCase(port: GroupSharePort, queryPort: SharedPlaceDetailQueryPort) =
        GetSharedPlaceDetailUseCase(port, queryPort)

    @Bean
    fun saveSharedPostUseCase(
        groupSharePort: GroupSharePort,
        groupOwnershipPort: GroupOwnershipPort,
        saveSharedPostPort: SaveSharedPostPort,
    ) = SaveSharedPostUseCase(groupSharePort, groupOwnershipPort, saveSharedPostPort)
}

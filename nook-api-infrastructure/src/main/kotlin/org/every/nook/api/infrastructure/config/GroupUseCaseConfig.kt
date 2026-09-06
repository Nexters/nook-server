package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.ListGroupPlacesUseCase
import org.every.nook.api.application.group.ListGroupPostsUseCase
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.ReplaceSavedPostGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
import org.every.nook.api.application.group.port.GroupPlaceQueryPort
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.application.group.port.GroupPostQueryPort
import org.every.nook.api.application.group.port.GroupReadAccessPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GroupUseCaseConfig {
    @Bean
    fun listGroupsUseCase(groupPort: GroupPort): ListGroupsUseCase = ListGroupsUseCase(groupPort)

    @Bean
    fun createGroupUseCase(groupPort: GroupPort): CreateGroupUseCase = CreateGroupUseCase(groupPort)

    @Bean
    fun updateGroupUseCase(groupPort: GroupPort): UpdateGroupUseCase = UpdateGroupUseCase(groupPort)

    @Bean
    fun deleteGroupUseCase(groupPort: GroupPort): DeleteGroupUseCase = DeleteGroupUseCase(groupPort)

    @Bean
    fun listGroupPostsUseCase(
        groupPostQueryPort: GroupPostQueryPort,
        groupReadAccessPort: GroupReadAccessPort,
        analyticsRecorder: UserAnalyticsEventRecorder,
    ): ListGroupPostsUseCase = ListGroupPostsUseCase(groupPostQueryPort, groupReadAccessPort, analyticsRecorder)

    @Bean
    fun listGroupPlacesUseCase(
        groupPlaceQueryPort: GroupPlaceQueryPort,
        groupReadAccessPort: GroupReadAccessPort,
        analyticsRecorder: UserAnalyticsEventRecorder,
    ): ListGroupPlacesUseCase = ListGroupPlacesUseCase(groupPlaceQueryPort, groupReadAccessPort, analyticsRecorder)

    @Bean
    fun replaceSavedPostGroupsUseCase(
        groupPostManagementPort: GroupPostManagementPort,
    ): ReplaceSavedPostGroupsUseCase = ReplaceSavedPostGroupsUseCase(groupPostManagementPort)
}

package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.group.CreateGroupUseCase
import org.every.nook.api.application.group.DeleteGroupUseCase
import org.every.nook.api.application.group.ListGroupsUseCase
import org.every.nook.api.application.group.UpdateGroupUseCase
import org.every.nook.api.application.group.port.GroupPort
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
}

package org.every.nook.api.infrastructure.persistence.push

import org.springframework.data.jpa.repository.JpaRepository

interface UserPushPreferenceJpaRepository : JpaRepository<UserPushPreferenceEntity, Long> {
    fun findByUserId(userId: Long): UserPushPreferenceEntity?

    fun findAllByUserIdInAndPostProcessingEnabledFalse(userIds: Collection<Long>): List<UserPushPreferenceEntity>
}

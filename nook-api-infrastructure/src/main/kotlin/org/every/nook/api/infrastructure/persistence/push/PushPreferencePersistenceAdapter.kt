package org.every.nook.api.infrastructure.persistence.push

import org.every.nook.api.application.push.PushPreference
import org.every.nook.api.application.push.PushPreferencePort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PushPreferencePersistenceAdapter(private val repository: UserPushPreferenceJpaRepository) : PushPreferencePort {
    @Transactional(readOnly = true)
    override fun get(userId: Long): PushPreference = repository.findByUserId(userId)
        ?.let { PushPreference(it.postProcessingEnabled) }
        ?: PushPreference(DEFAULT_POST_PROCESSING_ENABLED)

    @Transactional
    override fun update(userId: Long, postProcessingEnabled: Boolean): PushPreference {
        val entity = repository.findByUserId(userId)
            ?: UserPushPreferenceEntity(userId, postProcessingEnabled)
        entity.postProcessingEnabled = postProcessingEnabled
        repository.save(entity)
        return PushPreference(entity.postProcessingEnabled)
    }

    @Transactional(readOnly = true)
    override fun findPostProcessingDisabledUserIds(userIds: Collection<Long>): Set<Long> {
        if (userIds.isEmpty()) {
            return emptySet()
        }
        return repository.findAllByUserIdInAndPostProcessingEnabledFalse(userIds)
            .mapTo(mutableSetOf(), UserPushPreferenceEntity::userId)
    }

    private companion object {
        const val DEFAULT_POST_PROCESSING_ENABLED = true
    }
}

package org.every.nook.api.infrastructure.persistence.reminder

import org.every.nook.api.application.reminder.PlaceReminderPreferencePort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceReminderPreferencePersistenceAdapter(private val repository: UserPlaceReminderPreferenceJpaRepository) :
    PlaceReminderPreferencePort {
    @Transactional(readOnly = true)
    override fun isEnabled(userId: Long): Boolean = repository.findByUserId(userId)?.enabled ?: true

    @Transactional
    override fun update(userId: Long, enabled: Boolean): Boolean {
        repository.upsert(userId, enabled)
        return enabled
    }
}

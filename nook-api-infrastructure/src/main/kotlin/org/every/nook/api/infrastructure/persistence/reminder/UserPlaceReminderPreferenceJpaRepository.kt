package org.every.nook.api.infrastructure.persistence.reminder

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface UserPlaceReminderPreferenceJpaRepository : Repository<UserPlaceReminderPreferenceEntity, Long> {
    fun findByUserId(userId: Long): UserPlaceReminderPreferenceEntity?

    @Modifying
    @Query(
        value = """
            INSERT INTO user_place_reminder_preferences (user_id, enabled, created_at, updated_at)
            VALUES (:userId, :enabled, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE enabled = :enabled, updated_at = CURRENT_TIMESTAMP(6)
        """,
        nativeQuery = true,
    )
    fun upsert(@Param("userId") userId: Long, @Param("enabled") enabled: Boolean): Int
}

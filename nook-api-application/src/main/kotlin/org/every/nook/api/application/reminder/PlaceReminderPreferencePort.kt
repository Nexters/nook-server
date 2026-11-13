package org.every.nook.api.application.reminder

interface PlaceReminderPreferencePort {
    fun isEnabled(userId: Long): Boolean
    fun update(userId: Long, enabled: Boolean): Boolean
}

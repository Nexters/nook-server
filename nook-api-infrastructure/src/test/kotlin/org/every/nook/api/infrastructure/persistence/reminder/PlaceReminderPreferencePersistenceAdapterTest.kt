package org.every.nook.api.infrastructure.persistence.reminder

import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceReminderPreferencePersistenceAdapterTest {
    @Test
    fun `missing settings default to ON and saved OFF remains independent of push settings`() {
        val repository = mock(UserPlaceReminderPreferenceJpaRepository::class.java)
        val adapter = PlaceReminderPreferencePersistenceAdapter(repository)
        assertTrue(adapter.isEnabled(7))
        `when`(repository.findByUserId(7)).thenReturn(UserPlaceReminderPreferenceEntity(7, false))
        assertFalse(adapter.isEnabled(7))
        assertFalse(adapter.update(7, false))
        verify(repository).upsert(7, false)
    }

    @Test
    fun `native upsert preserves one preference per user and keeps other users unchanged`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL")
        source.connection.use {
            val jdbc = JdbcTemplate(source)
            val namedJdbc = NamedParameterJdbcTemplate(jdbc)
            jdbc.execute(
                """CREATE TABLE user_place_reminder_preferences (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNIQUE, enabled BOOLEAN,
                    created_at TIMESTAMP, updated_at TIMESTAMP)""",
            )
            val query = requireNotNull(
                UserPlaceReminderPreferenceJpaRepository::class.java.methods.single { it.name == "upsert" }
                    .getAnnotation(Query::class.java),
            ).value
            namedJdbc.update(query, mapOf("userId" to 7, "enabled" to false))
            namedJdbc.update(query, mapOf("userId" to 8, "enabled" to false))
            namedJdbc.update(query, mapOf("userId" to 7, "enabled" to true))
            assertEquals(
                2L,
                jdbc.queryForObject("SELECT COUNT(*) FROM user_place_reminder_preferences", Long::class.java),
            )
            assertEquals(
                listOf(true, false),
                jdbc.query("SELECT enabled FROM user_place_reminder_preferences ORDER BY user_id") { row, _ ->
                    row.getBoolean("enabled")
                },
            )
            assertEquals(
                2L,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_place_reminder_preferences WHERE created_at IS NOT NULL " +
                        "AND updated_at IS NOT NULL",
                    Long::class.java,
                ),
            )
        }
    }
}

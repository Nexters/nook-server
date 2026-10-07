package org.every.nook.api.infrastructure.persistence.push

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushPreferencePersistenceAdapterTest {
    private val repository = mock(UserPushPreferenceJpaRepository::class.java)
    private val adapter = PushPreferencePersistenceAdapter(repository)

    @Test
    fun `returns enabled when preference has not been stored`() {
        `when`(repository.findByUserId(7)).thenReturn(null)

        assertTrue(adapter.get(7).postProcessingEnabled)
    }

    @Test
    fun `creates and updates preference`() {
        `when`(repository.findByUserId(7)).thenReturn(null)
        `when`(repository.save(any(UserPushPreferenceEntity::class.java))).thenAnswer { it.arguments.first() }

        val created = adapter.update(7, false)

        assertFalse(created.postProcessingEnabled)
        verify(repository).save(any(UserPushPreferenceEntity::class.java))

        val existing = UserPushPreferenceEntity(7, false)
        `when`(repository.findByUserId(7)).thenReturn(existing)

        val updated = adapter.update(7, true)

        assertTrue(updated.postProcessingEnabled)
        assertTrue(existing.postProcessingEnabled)
    }

    @Test
    fun `finds only users who explicitly disabled post processing push`() {
        `when`(repository.findAllByUserIdInAndPostProcessingEnabledFalse(listOf(7L, 8L)))
            .thenReturn(listOf(UserPushPreferenceEntity(7, false)))

        assertEquals(setOf(7L), adapter.findPostProcessingDisabledUserIds(listOf(7, 8)))
    }
}

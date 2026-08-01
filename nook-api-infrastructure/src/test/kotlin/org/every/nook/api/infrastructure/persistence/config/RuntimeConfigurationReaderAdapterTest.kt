package org.every.nook.api.infrastructure.persistence.config

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuntimeConfigurationReaderAdapterTest {
    @Test
    fun `returns configured value`() {
        val repository = mock(RuntimeConfigurationJpaRepository::class.java)
        `when`(repository.findByConfigurationKey("instagram.scraping.provider-mode")).thenReturn(
            RuntimeConfigurationEntity(
                configurationKey = "instagram.scraping.provider-mode",
                configurationValue = "APIFY_ONLY",
            ),
        )

        val result = RuntimeConfigurationReaderAdapter(repository)
            .findValue("instagram.scraping.provider-mode")

        assertEquals("APIFY_ONLY", result)
    }

    @Test
    fun `missing key returns null`() {
        val repository = mock(RuntimeConfigurationJpaRepository::class.java)

        assertNull(RuntimeConfigurationReaderAdapter(repository).findValue("missing"))
    }
}

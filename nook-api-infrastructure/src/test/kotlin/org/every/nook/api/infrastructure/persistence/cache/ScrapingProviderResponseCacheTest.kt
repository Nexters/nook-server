package org.every.nook.api.infrastructure.persistence.cache

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrapingProviderResponseCacheTest {
    @Test
    fun `find separates provider responses`() {
        val repository = mock(ScrapingProviderResponseJpaRepository::class.java)
        `when`(
            repository.findByProviderAndSourceTypeAndExternalPostId("APIFY", "INSTAGRAM", "Post123"),
        ).thenReturn(ScrapingProviderResponseEntity("APIFY", "INSTAGRAM", "Post123", "apify-json"))

        val result = ScrapingProviderResponseCache(repository).find("APIFY", "INSTAGRAM", "Post123")

        assertEquals("apify-json", result)
    }

    @Test
    fun `save stores provider identity with raw response`() {
        val repository = mock(ScrapingProviderResponseJpaRepository::class.java)

        ScrapingProviderResponseCache(repository).save("APIFY", "INSTAGRAM", "Post123", "apify-json")

        verify(repository).saveAndFlush(any(ScrapingProviderResponseEntity::class.java))
    }
}

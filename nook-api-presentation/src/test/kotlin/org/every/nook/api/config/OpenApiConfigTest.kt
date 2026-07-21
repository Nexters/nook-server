package org.every.nook.api.config

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiConfigTest {
    @Test
    fun `open api metadata is configured`() {
        val openAPI = OpenApiConfig().openAPI()

        assertEquals("Nook API", openAPI.info.title)
        assertEquals("v1", openAPI.info.version)
    }
}

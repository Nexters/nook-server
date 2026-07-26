package org.every.nook.api.config

import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenApiConfigTest {
    @Test
    fun `open api metadata is configured`() {
        val openAPI = OpenApiConfig().openAPI()

        assertEquals("Nook API", openAPI.info.title)
        assertEquals("v1", openAPI.info.version)
        assertTrue("ApiResponse" in openAPI.components.schemas)
        assertTrue("ApiError" in openAPI.components.schemas)
    }

    @Test
    fun `api error data is documented as nullable object`() {
        val openAPI = OpenApiConfig().openAPI()

        val apiErrorSchema = openAPI.components.schemas.getValue("ApiError")
        val dataSchema = assertIs<Schema<*>>(apiErrorSchema.properties.getValue("data"))

        assertEquals("object", dataSchema.type)
        assertTrue(dataSchema.nullable)
        assertNull(dataSchema.additionalProperties)
    }
}

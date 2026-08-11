package org.every.nook.api.logging

import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyLogFieldExtractorTest {
    @Test
    fun `stores body as a single redacted json field`() {
        val extractor = BodyLogFieldExtractor(
            properties = HttpLoggingProperties(),
            objectMapper = ObjectMapper(),
            privacyArgumentFieldNames = PrivacyArgumentFieldNames.scan("org.every.nook.api.logging"),
        )

        val fields = extractor.extract(
            prefix = "request.body",
            body = """{"publicValue":"ok","privateValue":"secret","accessToken":"token"}""".toByteArray(),
            contentType = MediaType.APPLICATION_JSON_VALUE,
            charset = Charsets.UTF_8.name(),
        )

        assertEquals("""{"publicValue":"ok","privateValue":"****","accessToken":"****"}""", fields["request.body"])
        assertEquals("false", fields["request.body.truncated"])
        assertTrue(fields.containsKey("request.body.size.bytes"))
        assertFalse(fields.containsKey("request.body.publicvalue"))
    }

    data class AnnotatedBody(
        val publicValue: String,
        @field:PrivacyArgument
        val privateValue: String,
    )
}

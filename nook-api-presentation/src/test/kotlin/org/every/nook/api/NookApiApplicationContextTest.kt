package org.every.nook.api

import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:nook;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "auth.jwt.access-secret=01234567890123456789012345678901",
        "auth.jwt.refresh-secret=01234567890123456789012345678901",
        "parsing.dispatcher-interval=1h",
    ],
)
class NookApiApplicationContextTest {
    @MockitoBean
    private lateinit var findOutstandingPlaceParsingJobs: FindOutstandingPlaceParsingJobsUseCase

    @MockitoBean
    private lateinit var findOutstandingPostContentParsingJobs: FindOutstandingPostContentParsingJobsUseCase

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `application context starts with the Jackson 3 object mapper`() {
        assertNotNull(applicationContext.getBean(ObjectMapper::class.java))
        assertNotNull(applicationContext.getBean("placeParsingPersistenceAdapter"))
        assertNotNull(applicationContext.getBean("postContentParsingPersistenceAdapter"))
    }

    @Test
    fun `OpenAPI preserves required properties from Kotlin response nullability`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/v3/api-docs")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())

        val schemas = objectMapper.readTree(response.body()).path("components").path("schemas")
        val groupRequired = schemas.path("GroupResponse").path("required").values().map { it.asString() }.toSet()
        val savedPostDetailRequired =
            schemas.path("SavedPostDetailResponse").path("required").values().map { it.asString() }.toSet()

        assertEquals(setOf("id", "name", "color", "postCount", "thumbnailUrls"), groupRequired)
        assertTrue(savedPostDetailRequired.containsAll(setOf("postId", "canonicalUrl", "media", "groups", "places")))
        assertFalse(savedPostDetailRequired.contains("title"))
        assertFalse(savedPostDetailRequired.contains("publishedAt"))
    }
}

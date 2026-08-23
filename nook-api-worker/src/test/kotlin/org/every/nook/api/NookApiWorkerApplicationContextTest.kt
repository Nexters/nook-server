package org.every.nook.api

import net.javacrumbs.shedlock.core.LockProvider
import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.processing.ProcessParsingFollowUpJobsUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:nook-worker;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "auth.jwt.access-secret=01234567890123456789012345678901",
        "auth.jwt.refresh-secret=01234567890123456789012345678901",
        "parsing.dispatcher-interval=1h",
        "parsing.follow-up.dispatcher-interval=1h",
    ],
)
class NookApiWorkerApplicationContextTest {
    @MockitoBean
    private lateinit var lockProvider: LockProvider

    @MockitoBean
    private lateinit var findOutstandingPlaceParsingJobs: FindOutstandingPlaceParsingJobsUseCase

    @MockitoBean
    private lateinit var findOutstandingPostContentParsingJobs: FindOutstandingPostContentParsingJobsUseCase

    @MockitoBean
    private lateinit var processFollowUpJobs: ProcessParsingFollowUpJobsUseCase

    @Autowired
    private lateinit var context: ApplicationContext

    @LocalManagementPort
    private var port: Int = 0

    @Test
    fun `worker context owns parsing dispatchers`() {
        assertTrue(context.containsBean("placeParsingEventListener"))
        assertTrue(context.containsBean("postContentParsingEventListener"))
        assertTrue(context.containsBean("parsingFollowUpJobDispatcher"))
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/actuator/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode())
    }
}

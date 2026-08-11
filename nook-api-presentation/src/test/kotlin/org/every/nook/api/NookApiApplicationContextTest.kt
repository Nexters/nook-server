package org.every.nook.api

import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertNotNull

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

    @Test
    fun `application context starts with the Jackson 3 object mapper`() {
        assertNotNull(applicationContext.getBean(ObjectMapper::class.java))
        assertNotNull(applicationContext.getBean("placeParsingPersistenceAdapter"))
        assertNotNull(applicationContext.getBean("postContentParsingPersistenceAdapter"))
    }
}

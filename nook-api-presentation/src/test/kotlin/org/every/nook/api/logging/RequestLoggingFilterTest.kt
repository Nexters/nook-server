package org.every.nook.api.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestLoggingFilterTest {
    @Test
    fun `adds request id response header and logs structured request context`() {
        val appender = attachLogAppender()
        val request = MockHttpServletRequest("POST", "/api/v1/posts/17").apply {
            queryString = "includePlaces=true&tag=cafe&tag=date&accessToken=secret&keyword=coffee%20shop"
            contentType = MediaType.APPLICATION_JSON_VALUE
            setContent("""{"url":"https://example.com/post","accessToken":"secret"}""".toByteArray())
            addHeader(RequestLoggingFields.REQUEST_ID_HEADER, "req-test-1")
            addHeader("User-Agent", "Nook iOS")
            addHeader("X-App-Platform", "IOS")
            addHeader("X-App-Build-Number", "42")
            addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
        }
        val response = MockHttpServletResponse()
        val filter = requestLoggingFilter(bodyLoggingProperties())
        val chain = object : FilterChain {
            override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse) {
                servletRequest.getInputStream().readBytes()
                servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts/{postId}")
                servletRequest.setAttribute(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                    mapOf("postId" to "17"),
                )
                servletResponse.contentType = MediaType.APPLICATION_JSON_VALUE
                servletResponse.writer.write("""{"success":{"id":17,"refreshToken":"secret"}}""")
            }
        }

        try {
            filter.doFilter(request, response, chain)
        } finally {
            detachLogAppender(appender)
        }

        val event = appender.list.single()
        val mdc = event.mdcPropertyMap
        assertEquals(
            "req: POST /api/v1/posts/{postId}\nres: 200 ${mdc[RequestLoggingFields.TRANSACTION_DURATION_MS]}ms",
            event.formattedMessage,
        )
        assertEquals("reqtest1", response.getHeader(RequestLoggingFields.REQUEST_ID_HEADER))
        assertEquals("""{"success":{"id":17,"refreshToken":"secret"}}""", response.contentAsString)
        assertEquals("reqtest1", mdc[RequestLoggingFields.REQUEST_ID])
        assertEquals("POST", mdc[RequestLoggingFields.REQUEST_METHOD])
        assertEquals(
            """{"includePlaces":["true"],"tag":["cafe","date"],"accessToken":["****"],"keyword":["coffee shop"]}""",
            mdc[RequestLoggingFields.REQUEST_QUERY_PARAMS],
        )
        assertFalse(mdc.containsKey("request.query"))
        assertEquals("""{"postId":"17"}""", mdc[RequestLoggingFields.REQUEST_PATH_PARAMS])
        assertEquals("/api/v1/posts/{postId}", mdc[RequestLoggingFields.HTTP_ROUTE])
        assertEquals("POST /api/v1/posts/{postId}", mdc[RequestLoggingFields.TRANSACTION_NAME])
        assertEquals("203.0.113.10", mdc[RequestLoggingFields.REQUEST_CLIENT_IP])
        assertEquals("Nook iOS", mdc["request.headers.user_agent"])
        assertEquals("IOS", mdc["request.headers.x_app_platform"])
        assertEquals("42", mdc["request.headers.x_app_build_number"])
        assertEquals("""{"url":"https://example.com/post","accessToken":"****"}""", mdc["request.body"])
        assertEquals("""{"success":{"id":17,"refreshToken":"****"}}""", mdc["response.body"])
        assertNotNull(mdc[RequestLoggingFields.TRANSACTION_DURATION_MS])
    }

    @Test
    fun `generates a request id when incoming header is invalid`() {
        val request = MockHttpServletRequest("GET", "/api/v1/members/me").apply {
            addHeader(RequestLoggingFields.REQUEST_ID_HEADER, "bad request id\n")
        }
        val response = MockHttpServletResponse()

        requestLoggingFilter(HttpLoggingProperties()).doFilter(request, response, emptyChain())

        val requestId = response.getHeader(RequestLoggingFields.REQUEST_ID_HEADER)
        assertNotNull(requestId)
        assertFalse(requestId.contains("bad request id"))
        assertEquals(16, requestId.length)
    }

    @Test
    fun `uses request uri without query string when route is unavailable`() {
        val appender = attachLogAppender()
        val request = MockHttpServletRequest("GET", "/unknown/17").apply {
            queryString = "keyword=cafe"
        }
        val response = MockHttpServletResponse()

        try {
            requestLoggingFilter(HttpLoggingProperties()).doFilter(request, response, emptyChain())
        } finally {
            detachLogAppender(appender)
        }

        val event = appender.list.single()
        assertEquals(
            "req: GET /unknown/17\nres: 200 ${event.mdcPropertyMap[RequestLoggingFields.TRANSACTION_DURATION_MS]}ms",
            event.formattedMessage,
        )
        assertEquals("""{"keyword":["cafe"]}""", event.mdcPropertyMap[RequestLoggingFields.REQUEST_QUERY_PARAMS])
        assertFalse(event.mdcPropertyMap.containsKey(RequestLoggingFields.REQUEST_PATH_PARAMS))
    }

    @Test
    fun `normalizes incoming request id to 16 alphanumeric characters`() {
        val request = MockHttpServletRequest("GET", "/api/v1/members/me").apply {
            addHeader(RequestLoggingFields.REQUEST_ID_HEADER, "04939fb0-ec31-4ddd-ab13-04cdf5d9d7cd")
        }
        val response = MockHttpServletResponse()

        requestLoggingFilter(HttpLoggingProperties()).doFilter(request, response, emptyChain())

        assertEquals("04939fb0ec314ddd", response.getHeader(RequestLoggingFields.REQUEST_ID_HEADER))
    }

    @Test
    fun `skips ignored actuator paths`() {
        val appender = attachLogAppender()
        val request = MockHttpServletRequest("GET", "/actuator/prometheus")
        val response = MockHttpServletResponse()

        try {
            requestLoggingFilter(HttpLoggingProperties()).doFilter(request, response, emptyChain())
        } finally {
            detachLogAppender(appender)
        }

        assertTrue(appender.list.isEmpty())
    }

    private fun bodyLoggingProperties(): HttpLoggingProperties = HttpLoggingProperties(
        body = HttpLoggingProperties.BodyProperties(
            requestEnabled = true,
            responseEnabled = true,
        ),
    )

    private fun requestLoggingFilter(properties: HttpLoggingProperties): RequestLoggingFilter = RequestLoggingFilter(
        properties = properties,
        bodyLogFieldExtractor = BodyLogFieldExtractor(
            properties = properties,
            objectMapper = ObjectMapper(),
            privacyArgumentFieldNames = PrivacyArgumentFieldNames(setOf("access_token", "refresh_token")),
        ),
        requestParameterLogFieldExtractor = RequestParameterLogFieldExtractor(
            properties = properties,
            objectMapper = ObjectMapper(),
            privacyArgumentFieldNames = PrivacyArgumentFieldNames(setOf("access_token", "refresh_token")),
        ),
    )

    private fun emptyChain(): FilterChain = object : FilterChain {
        override fun doFilter(request: ServletRequest, response: ServletResponse) = Unit
    }

    private fun attachLogAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    private fun detachLogAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        logger.detachAppender(appender)
    }
}

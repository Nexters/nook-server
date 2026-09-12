package org.every.nook.api.presentation.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpRoutingExceptionHandlerTest {
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mockMvc: MockMvc
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger

    @BeforeTest
    fun setUp() {
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            register(RoutingTestConfig::class.java)
            refresh()
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
        context.close()
    }

    @Test
    fun `missing handler returns 404 without server error log`() {
        mockMvc.get("/missing-route").andExpect {
            status { isNotFound() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("NOT_FOUND") }
            jsonPath("$.success") { doesNotExist() }
        }
        assertFalse(appender.list.any { it.level == Level.ERROR })
    }

    @Test
    fun `missing static resource returns 404 without server error log`() {
        mockMvc.get("/assets/missing-resource.txt").andExpect {
            status { isNotFound() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("NOT_FOUND") }
            jsonPath("$.success") { doesNotExist() }
        }
        assertFalse(appender.list.any { it.level == Level.ERROR })
    }

    @Test
    fun `unsupported method returns 405 with allow header without server error log`() {
        mockMvc.get("/test/post-only").andExpect {
            status { isMethodNotAllowed() }
            header { string("Allow", "POST") }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("METHOD_NOT_ALLOWED") }
            jsonPath("$.success") { doesNotExist() }
        }
        assertFalse(appender.list.any { it.level == Level.ERROR })
    }

    @Test
    fun `unexpected failure still returns 500 and logs error`() {
        mockMvc.get("/test/server-error").andExpect {
            status { isInternalServerError() }
            jsonPath("$.error.errorCode") { value("INTERNAL_SERVER_ERROR") }
            jsonPath("$.error.reason") { value("서버 오류가 발생했습니다.") }
        }
        assertTrue(appender.list.any { it.level == Level.ERROR && it.throwableProxy != null })
    }
}

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
@Import(GlobalExceptionHandler::class, RoutingTestController::class)
private class RoutingTestConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/routing-test-assets/")
    }
}

@RestController
private class RoutingTestController {
    @PostMapping("/test/post-only")
    fun postOnly(): Map<String, String> = mapOf("result" to "ok")

    @GetMapping("/test/server-error")
    fun serverError(): Nothing = error("internal secret")
}

package org.every.nook.api.infrastructure.openai

import org.mockito.Mockito.mock
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiRateLimitInterceptorTest {
    @Test
    fun `uses retry after header before configured backoff`() {
        val delays = mutableListOf<Duration>()
        val execution = SequenceExecution(
            response(HttpStatus.TOO_MANY_REQUESTS, "Retry-After" to "7"),
            response(HttpStatus.OK),
        )
        val interceptor = OpenAiRateLimitInterceptor(
            maxConcurrentRequests = 1,
            retryBackoffs = listOf(Duration.ofSeconds(1)),
            sleep = delays::add,
            jitter = { error("configured backoff must not be used") },
        )

        val result = interceptor.intercept(request(), byteArrayOf(), execution)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf(Duration.ofSeconds(7)), delays)
        assertEquals(2, execution.calls)
    }

    @Test
    fun `uses jittered configured backoffs and stops after success`() {
        val delays = mutableListOf<Duration>()
        val execution = SequenceExecution(
            response(HttpStatus.TOO_MANY_REQUESTS),
            response(HttpStatus.TOO_MANY_REQUESTS),
            response(HttpStatus.OK),
        )
        val interceptor = OpenAiRateLimitInterceptor(
            maxConcurrentRequests = 1,
            retryBackoffs = listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)),
            sleep = delays::add,
            jitter = { it.plusMillis(100) },
        )

        val result = interceptor.intercept(request(), byteArrayOf(), execution)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf(Duration.ofMillis(1100), Duration.ofMillis(2100)), delays)
        assertEquals(3, execution.calls)
    }

    @Test
    fun `limits concurrent OpenAI executions`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val calls = AtomicInteger()
        val execution = ClientHttpRequestExecution { _, _ ->
            when (calls.incrementAndGet()) {
                1 -> {
                    firstStarted.countDown()
                    check(releaseFirst.await(3, TimeUnit.SECONDS))
                }

                2 -> secondStarted.countDown()
            }
            response(HttpStatus.OK)
        }
        val interceptor = OpenAiRateLimitInterceptor(1, emptyList())
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<ClientHttpResponse> {
                interceptor.intercept(request(), byteArrayOf(), execution)
            }
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            val second = executor.submit<ClientHttpResponse> {
                interceptor.intercept(request(), byteArrayOf(), execution)
            }

            assertFalse(secondStarted.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertEquals(HttpStatus.OK, first.get(1, TimeUnit.SECONDS).statusCode)
            assertEquals(HttpStatus.OK, second.get(1, TimeUnit.SECONDS).statusCode)
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun request(): HttpRequest = mock(HttpRequest::class.java)

    private fun response(status: HttpStatus, vararg headers: Pair<String, String>): MockClientHttpResponse =
        MockClientHttpResponse(byteArrayOf(), status).apply {
            headers.forEach { (name, value) -> this.headers.add(name, value) }
        }

    private class SequenceExecution(vararg responses: ClientHttpResponse) : ClientHttpRequestExecution {
        private val responses = ArrayDeque(responses.toList())
        var calls: Int = 0
            private set

        override fun execute(request: HttpRequest, body: ByteArray): ClientHttpResponse {
            calls += 1
            return responses.removeFirst()
        }
    }
}

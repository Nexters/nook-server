package org.every.nook.api.config

import org.every.nook.api.logging.MdcTaskDecorator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class PlaceParsingAsyncConfig {
    @Bean
    fun mdcTaskDecorator(): TaskDecorator = MdcTaskDecorator()

    @Bean("placeParsingTaskExecutor")
    fun placeParsingTaskExecutor(
        mdcTaskDecorator: TaskDecorator,
        @Value("\${parsing.place-concurrency:2}") concurrency: Int,
    ): Executor = boundedExecutor(THREAD_NAME_PREFIX, concurrency, mdcTaskDecorator)

    @Bean("postContentParsingTaskExecutor")
    fun postContentParsingTaskExecutor(
        mdcTaskDecorator: TaskDecorator,
        @Value("\${parsing.content-concurrency:2}") concurrency: Int,
    ): Executor = boundedExecutor(POST_CONTENT_THREAD_NAME_PREFIX, concurrency, mdcTaskDecorator)

    @Bean("placeSupplementTaskExecutor")
    fun placeSupplementTaskExecutor(mdcTaskDecorator: TaskDecorator): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = PLACE_SUPPLEMENT_POOL_SIZE
        maxPoolSize = PLACE_SUPPLEMENT_POOL_SIZE
        queueCapacity = PLACE_SUPPLEMENT_QUEUE_CAPACITY
        setThreadNamePrefix(PLACE_SUPPLEMENT_THREAD_NAME_PREFIX)
        setTaskDecorator(mdcTaskDecorator)
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(PLACE_SUPPLEMENT_SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    @Bean("parsingRetryTaskScheduler")
    fun parsingRetryTaskScheduler(mdcTaskDecorator: TaskDecorator): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = RETRY_SCHEDULER_POOL_SIZE
            setThreadNamePrefix(RETRY_THREAD_NAME_PREFIX)
            setTaskDecorator(mdcTaskDecorator)
            setWaitForTasksToCompleteOnShutdown(true)
        }

    private fun boundedExecutor(prefix: String, concurrency: Int, decorator: TaskDecorator): Executor =
        ThreadPoolTaskExecutor().apply {
            require(concurrency > 0) { "Parsing concurrency must be positive" }
            corePoolSize = concurrency
            maxPoolSize = concurrency
            queueCapacity = PARSING_QUEUE_CAPACITY
            setThreadNamePrefix(prefix)
            setTaskDecorator(decorator)
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(PARSING_SHUTDOWN_TIMEOUT_SECONDS)
            initialize()
        }

    private companion object {
        const val THREAD_NAME_PREFIX = "place-parsing-"
        const val POST_CONTENT_THREAD_NAME_PREFIX = "post-content-parsing-"
        const val PLACE_SUPPLEMENT_THREAD_NAME_PREFIX = "place-supplement-"
        const val RETRY_THREAD_NAME_PREFIX = "parsing-retry-"
        const val RETRY_SCHEDULER_POOL_SIZE = 2
        const val PLACE_SUPPLEMENT_POOL_SIZE = 2
        const val PLACE_SUPPLEMENT_QUEUE_CAPACITY = 100
        const val PLACE_SUPPLEMENT_SHUTDOWN_TIMEOUT_SECONDS = 60
        const val PARSING_QUEUE_CAPACITY = 100
        const val PARSING_SHUTDOWN_TIMEOUT_SECONDS = 60
    }
}

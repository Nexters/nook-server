package org.every.nook.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class PlaceParsingAsyncConfig {
    @Bean("placeParsingTaskExecutor")
    fun placeParsingTaskExecutor(): Executor = SimpleAsyncTaskExecutor(THREAD_NAME_PREFIX).apply {
        setVirtualThreads(true)
    }

    @Bean("postContentParsingTaskExecutor")
    fun postContentParsingTaskExecutor(): Executor = SimpleAsyncTaskExecutor(POST_CONTENT_THREAD_NAME_PREFIX).apply {
        setVirtualThreads(true)
    }

    @Bean("parsingRetryTaskScheduler")
    fun parsingRetryTaskScheduler(): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = RETRY_SCHEDULER_POOL_SIZE
        setThreadNamePrefix(RETRY_THREAD_NAME_PREFIX)
        setWaitForTasksToCompleteOnShutdown(true)
    }

    private companion object {
        const val THREAD_NAME_PREFIX = "place-parsing-"
        const val POST_CONTENT_THREAD_NAME_PREFIX = "post-content-parsing-"
        const val RETRY_THREAD_NAME_PREFIX = "parsing-retry-"
        const val RETRY_SCHEDULER_POOL_SIZE = 2
    }
}

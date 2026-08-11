package org.every.nook.api.config

import org.every.nook.api.logging.MdcTaskDecorator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class PlaceParsingAsyncConfig {
    @Bean
    fun mdcTaskDecorator(): TaskDecorator = MdcTaskDecorator()

    @Bean("placeParsingTaskExecutor")
    fun placeParsingTaskExecutor(mdcTaskDecorator: TaskDecorator): Executor =
        SimpleAsyncTaskExecutor(THREAD_NAME_PREFIX).apply {
            setVirtualThreads(true)
            setTaskDecorator(mdcTaskDecorator)
        }

    @Bean("postContentParsingTaskExecutor")
    fun postContentParsingTaskExecutor(mdcTaskDecorator: TaskDecorator): Executor =
        SimpleAsyncTaskExecutor(POST_CONTENT_THREAD_NAME_PREFIX).apply {
            setVirtualThreads(true)
            setTaskDecorator(mdcTaskDecorator)
        }

    @Bean("parsingRetryTaskScheduler")
    fun parsingRetryTaskScheduler(mdcTaskDecorator: TaskDecorator): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = RETRY_SCHEDULER_POOL_SIZE
            setThreadNamePrefix(RETRY_THREAD_NAME_PREFIX)
            setTaskDecorator(mdcTaskDecorator)
            setWaitForTasksToCompleteOnShutdown(true)
        }

    private companion object {
        const val THREAD_NAME_PREFIX = "place-parsing-"
        const val POST_CONTENT_THREAD_NAME_PREFIX = "post-content-parsing-"
        const val RETRY_THREAD_NAME_PREFIX = "parsing-retry-"
        const val RETRY_SCHEDULER_POOL_SIZE = 2
    }
}

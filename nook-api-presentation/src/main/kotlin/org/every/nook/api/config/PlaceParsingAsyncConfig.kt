package org.every.nook.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class PlaceParsingAsyncConfig {
    @Bean("placeParsingTaskExecutor")
    fun placeParsingTaskExecutor(): Executor = SimpleAsyncTaskExecutor(THREAD_NAME_PREFIX).apply {
        setVirtualThreads(true)
    }

    private companion object {
        const val THREAD_NAME_PREFIX = "place-parsing-"
    }
}

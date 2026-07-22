package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.port.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class TransactionConfig {
    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner {
        val template = TransactionTemplate(transactionManager)
        return object : TransactionRunner {
            override fun <T> required(block: () -> T): T = requireNotNull(template.execute { block() })
        }
    }
}

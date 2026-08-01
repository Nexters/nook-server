package org.every.nook.api.infrastructure.persistence.config

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.springframework.stereotype.Repository

@Repository
class RuntimeConfigurationReaderAdapter(private val repository: RuntimeConfigurationJpaRepository) :
    RuntimeConfigurationReader {
    override fun findValue(key: String): String? = repository.findByConfigurationKey(key)?.configurationValue
}

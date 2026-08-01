package org.every.nook.api.infrastructure.persistence.config

import org.springframework.data.jpa.repository.JpaRepository

interface RuntimeConfigurationJpaRepository : JpaRepository<RuntimeConfigurationEntity, Long> {
    fun findByConfigurationKey(configurationKey: String): RuntimeConfigurationEntity?
}

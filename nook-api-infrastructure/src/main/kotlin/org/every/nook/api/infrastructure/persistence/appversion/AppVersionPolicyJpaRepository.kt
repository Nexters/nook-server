package org.every.nook.api.infrastructure.persistence.appversion

import org.every.nook.api.application.appversion.AppPlatform
import org.springframework.data.jpa.repository.JpaRepository

interface AppVersionPolicyJpaRepository : JpaRepository<AppVersionPolicyEntity, Long> {
    fun findByPlatform(platform: AppPlatform): AppVersionPolicyEntity?
}

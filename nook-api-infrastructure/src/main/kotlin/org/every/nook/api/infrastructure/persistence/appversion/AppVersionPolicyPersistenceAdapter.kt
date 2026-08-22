package org.every.nook.api.infrastructure.persistence.appversion

import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy
import org.every.nook.api.application.appversion.AppVersionPolicyPort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class AppVersionPolicyPersistenceAdapter(private val repository: AppVersionPolicyJpaRepository) : AppVersionPolicyPort {
    @Transactional(readOnly = true)
    override fun findByPlatform(platform: AppPlatform): AppVersionPolicy? = repository.findByPlatform(platform)?.let {
        AppVersionPolicy(
            platform = it.platform,
            minimumSupportedBuildNumber = it.minimumSupportedBuildNumber,
            latestBuildNumber = it.latestBuildNumber,
            latestVersion = it.latestVersion,
            storeUrl = it.storeUrl,
        )
    }
}

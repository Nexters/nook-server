package org.every.nook.api.infrastructure.persistence.appversion

import org.every.nook.api.application.admin.AdminAppVersionPolicyPort
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy
import org.every.nook.api.application.appversion.AppVersionPolicyPort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Repository
class AppVersionPolicyPersistenceAdapter(
    private val repository: AppVersionPolicyJpaRepository,
    private val auditLogPort: AdminAuditLogPort,
    private val objectMapper: ObjectMapper,
) : AppVersionPolicyPort,
    AdminAppVersionPolicyPort {
    @Transactional(readOnly = true)
    override fun findByPlatform(platform: AppPlatform): AppVersionPolicy? = repository.findByPlatform(platform)?.let {
        it.toPolicy()
    }

    @Transactional(readOnly = true)
    override fun findAll(): List<AppVersionPolicy> = repository.findAll().map(AppVersionPolicyEntity::toPolicy)

    @Transactional
    override fun upsert(command: AdminAppVersionPolicyPort.UpsertCommand): AppVersionPolicy {
        val entity = repository.findByPlatform(command.platform)
        val before = entity?.toPolicy()
        val updatedPolicy = command.toPolicy()
        val saved = if (entity == null) {
            repository.save(
                AppVersionPolicyEntity(
                    platform = updatedPolicy.platform,
                    minimumSupportedBuildNumber = updatedPolicy.minimumSupportedBuildNumber,
                    latestBuildNumber = updatedPolicy.latestBuildNumber,
                    latestVersion = updatedPolicy.latestVersion,
                    storeUrl = updatedPolicy.storeUrl,
                ),
            )
        } else {
            entity.update(updatedPolicy)
            entity
        }
        val after = saved.toPolicy()
        auditLogPort.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "APP_VERSION_POLICY_UPSERTED",
                targetType = "APP_VERSION_POLICY",
                targetId = command.platform.name,
                reason = command.reason,
                beforeValue = before?.let(objectMapper::writeValueAsString),
                afterValue = objectMapper.writeValueAsString(after),
                requestId = command.requestId,
            ),
        )
        return after
    }
}

private fun AppVersionPolicyEntity.toPolicy() = AppVersionPolicy(
    platform = platform,
    minimumSupportedBuildNumber = minimumSupportedBuildNumber,
    latestBuildNumber = latestBuildNumber,
    latestVersion = latestVersion,
    storeUrl = storeUrl,
)

private fun AdminAppVersionPolicyPort.UpsertCommand.toPolicy() = AppVersionPolicy(
    platform = platform,
    minimumSupportedBuildNumber = minimumSupportedBuildNumber,
    latestBuildNumber = latestBuildNumber,
    latestVersion = latestVersion,
    storeUrl = storeUrl,
)

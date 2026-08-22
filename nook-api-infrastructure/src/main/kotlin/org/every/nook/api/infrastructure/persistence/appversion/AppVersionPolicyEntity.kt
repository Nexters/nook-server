package org.every.nook.api.infrastructure.persistence.appversion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "app_version_policies",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_platform", columnNames = ["platform"]),
    ],
)
class AppVersionPolicyEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = MAX_PLATFORM_LENGTH)
    val platform: AppPlatform,
    @Column(name = "minimum_supported_build_number", nullable = false)
    var minimumSupportedBuildNumber: Long,
    @Column(name = "latest_build_number", nullable = false)
    var latestBuildNumber: Long,
    @Column(name = "latest_version", nullable = false, length = MAX_VERSION_LENGTH)
    var latestVersion: String,
    @Column(name = "store_url", nullable = false, length = MAX_STORE_URL_LENGTH)
    var storeUrl: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun update(policy: AppVersionPolicy) {
        minimumSupportedBuildNumber = policy.minimumSupportedBuildNumber
        latestBuildNumber = policy.latestBuildNumber
        latestVersion = policy.latestVersion
        storeUrl = policy.storeUrl
    }

    companion object {
        const val MAX_PLATFORM_LENGTH = 20
        const val MAX_VERSION_LENGTH = AppVersionPolicy.MAX_VERSION_LENGTH
        const val MAX_STORE_URL_LENGTH = AppVersionPolicy.MAX_STORE_URL_LENGTH
    }
}

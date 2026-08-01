package org.every.nook.api.infrastructure.persistence.cache

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "scraping_provider_responses",
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_source_type_external_post_id",
            columnNames = ["provider", "source_type", "external_post_id"],
        ),
    ],
)
class ScrapingProviderResponseEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "source_type", nullable = false, length = 50)
    val sourceType: String,
    @Column(name = "external_post_id", nullable = false, length = 255)
    val externalPostId: String,
    @Column(name = "response_body", nullable = false, columnDefinition = "LONGTEXT")
    val responseBody: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

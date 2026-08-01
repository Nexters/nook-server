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
    name = "media_url_caches",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_source_url_hash", columnNames = ["source_url_hash"])],
)
class MediaUrlCacheEntity(
    @Column(name = "source_url_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    val sourceUrlHash: String,
    @Column(name = "source_url", nullable = false, length = 2048)
    val sourceUrl: String,
    @Column(name = "stored_url", nullable = false, length = 2048)
    val storedUrl: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

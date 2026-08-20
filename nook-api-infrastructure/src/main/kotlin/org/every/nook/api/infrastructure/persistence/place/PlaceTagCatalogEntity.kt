package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.domain.place.PlaceTagCategory
import org.every.nook.api.domain.place.PlaceTagDefinition
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(
    name = "place_tag_catalogs",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_tag_code", columnNames = ["tag_code"]),
    ],
    indexes = [Index(name = "idx_category_enabled_sort_order", columnList = "category, enabled, sort_order")],
)
class PlaceTagCatalogEntity(
    @Column(name = "tag_code", nullable = false, length = TAG_CODE_LENGTH, updatable = false)
    val tagCode: String,
    @Column(name = "category", nullable = false, length = CATEGORY_LENGTH)
    @Enumerated(EnumType.STRING)
    var category: PlaceTagCategory,
    @Column(name = "display_name", nullable = false, length = DISPLAY_NAME_LENGTH)
    var displayName: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matching_keywords", nullable = false, columnDefinition = "JSON")
    var matchingKeywords: List<String>,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun update(
        category: PlaceTagCategory,
        displayName: String,
        matchingKeywords: List<String>,
        enabled: Boolean,
        sortOrder: Int,
    ) {
        this.category = category
        this.displayName = displayName
        this.matchingKeywords = matchingKeywords
        this.enabled = enabled
        this.sortOrder = sortOrder
    }

    fun toDefinition() = PlaceTagDefinition(
        tag = tagCode,
        category = category,
        displayName = displayName,
        matchingKeywords = matchingKeywords.toSet(),
        enabled = enabled,
        sortOrder = sortOrder,
    )

    companion object {
        const val TAG_CODE_LENGTH = 50
        const val CATEGORY_LENGTH = 30
        const val DISPLAY_NAME_LENGTH = 50
    }
}

interface PlaceTagCatalogJpaRepository : JpaRepository<PlaceTagCatalogEntity, Long> {
    fun findAllByOrderBySortOrderAscTagCodeAsc(): List<PlaceTagCatalogEntity>

    fun findByTagCode(tagCode: String): PlaceTagCatalogEntity?
}

@Component
class PlaceTagCatalogPersistenceAdapter(private val repository: PlaceTagCatalogJpaRepository) :
    PlaceTagCatalogQueryPort {
    @Transactional(readOnly = true)
    override fun findAll(): List<PlaceTagDefinition> = repository.findAllByOrderBySortOrderAscTagCodeAsc()
        .map(PlaceTagCatalogEntity::toDefinition)
}

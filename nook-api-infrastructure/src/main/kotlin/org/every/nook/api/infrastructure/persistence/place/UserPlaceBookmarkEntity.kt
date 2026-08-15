package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "user_place_bookmarks",
    indexes = [
        Index(name = "idx_place_id", columnList = "place_id"),
        Index(name = "idx_user_id_created_at_id", columnList = "user_id, created_at, id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_user_id_place_id", columnNames = ["user_id", "place_id"]),
    ],
)
class UserPlaceBookmarkEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "place_id", nullable = false)
    val placeId: Long,
    @Column(name = "memo", columnDefinition = "TEXT")
    var memo: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

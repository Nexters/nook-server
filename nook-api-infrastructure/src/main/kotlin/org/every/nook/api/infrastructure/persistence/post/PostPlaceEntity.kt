package org.every.nook.api.infrastructure.persistence.post

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
    name = "post_places",
    indexes = [Index(name = "idx_place_id", columnList = "place_id")],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id_place_id", columnNames = ["post_id", "place_id"]),
        UniqueConstraint(name = "idx_u_post_id_display_order", columnNames = ["post_id", "display_order"]),
    ],
)
class PostPlaceEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "place_id", nullable = false)
    val placeId: Long,
    @Column(name = "display_order", nullable = false)
    val sequence: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

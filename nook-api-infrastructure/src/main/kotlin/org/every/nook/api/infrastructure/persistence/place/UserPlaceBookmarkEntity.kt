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
import java.time.Instant

@Entity
@Table(
    name = "user_place_bookmarks",
    comment = "사용자별 지도 장소 북마크",
    indexes = [
        Index(name = "idx_place_id", columnList = "place_id"),
        Index(name = "idx_user_id_last_saved_at_id", columnList = "user_id, last_saved_at DESC, id DESC"),
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
    @Column(
        name = "last_saved_at",
        nullable = false,
        insertable = false,
        updatable = false,
        comment = "이 장소가 마지막으로 저장 게시물에 연결된 시각",
    )
    lateinit var lastSavedAt: Instant
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

package org.every.nook.api.infrastructure.persistence.save

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.save.UserSavedPost
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.util.UUID

@Entity
@DynamicUpdate
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "user_saved_posts",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_post_id", columnList = "post_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_user_id_post_id",
            columnNames = ["user_id", "post_id"],
        ),
    ],
)
class UserSavedPostEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(
        name = "memo",
        nullable = true,
        length = UserSavedPost.MAX_MEMO_LENGTH,
        columnDefinition = "TEXT",
    )
    var memo: String? = null,
) : BaseEntity() {
    @Column(name = "share_generation", nullable = false, length = 36, comment = "삭제 시 갱신하여 이전 공유 링크 영구 만료")
    var shareGeneration: String = UUID.randomUUID().toString()
        protected set

    @Column(name = "place_links_frozen", nullable = false, comment = "공유 복사본의 장소 연결을 공통 파싱 결과와 동기화하지 않음")
    var placeLinksFrozen: Boolean = false

    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun softDelete(now: Instant) {
        if (deletedAt == null) shareGeneration = UUID.randomUUID().toString()
        deletedAt = now
    }

    fun restore() {
        deletedAt = null
    }
}

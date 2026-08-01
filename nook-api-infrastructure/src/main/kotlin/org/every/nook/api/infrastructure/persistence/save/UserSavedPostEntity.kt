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
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
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
    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun softDelete(now: Instant) {
        deletedAt = now
    }

    fun restore() {
        deletedAt = null
    }
}

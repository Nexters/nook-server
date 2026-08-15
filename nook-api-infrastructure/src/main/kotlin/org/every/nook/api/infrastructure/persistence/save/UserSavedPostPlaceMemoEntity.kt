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

@Entity
@Table(
    name = "user_saved_post_place_memos",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_place_id", columnList = "place_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_user_saved_post_id_place_id",
            columnNames = ["user_saved_post_id", "place_id"],
        ),
    ],
)
class UserSavedPostPlaceMemoEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "user_saved_post_id", nullable = false)
    val userSavedPostId: Long,
    @Column(name = "place_id", nullable = false)
    val placeId: Long,
    @Column(
        name = "memo",
        nullable = false,
        length = UserSavedPost.MAX_MEMO_LENGTH,
        columnDefinition = "TEXT",
    )
    var memo: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

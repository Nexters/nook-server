package org.every.nook.api.infrastructure.persistence.save

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "user_saved_posts",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_post_id", columnList = "post_id"),
    ],
)
class UserSavedPostEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "memo", nullable = true, columnDefinition = "TEXT")
    val memo: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

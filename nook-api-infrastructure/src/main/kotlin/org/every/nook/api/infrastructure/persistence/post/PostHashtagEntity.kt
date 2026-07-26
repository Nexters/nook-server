package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.post.Post
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "post_hashtags",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id_hashtag", columnNames = ["post_id", "hashtag"]),
        UniqueConstraint(name = "idx_u_post_id_display_order", columnNames = ["post_id", "display_order"]),
    ],
)
class PostHashtagEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "hashtag", nullable = false, length = Post.MAX_HASHTAG_LENGTH)
    val hashtag: String,
    @Column(name = "display_order", nullable = false)
    val sequence: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

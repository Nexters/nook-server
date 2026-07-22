package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "post_media",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id_display_order", columnNames = ["post_id", "display_order"]),
    ],
)
class PostMediaEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = MEDIA_TYPE_LENGTH)
    val mediaType: PostMedia.MediaType,
    @Column(name = "media_url", nullable = false, length = PostMedia.MAX_MEDIA_URL_LENGTH)
    val mediaUrl: String,
    @Column(name = "display_order", nullable = false)
    val sequence: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val MEDIA_TYPE_LENGTH = 20
    }
}

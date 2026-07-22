package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "posts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_source_type_external_post_id",
            columnNames = ["source_type", "external_post_id"],
        ),
    ],
)
class PostEntity(
    @Column(
        name = "source_type",
        nullable = false,
        length = PostSource.MAX_SOURCE_TYPE_LENGTH,
        columnDefinition = "VARCHAR(50) COLLATE utf8mb4_bin",
    )
    val sourceType: String,
    @Column(
        name = "external_post_id",
        nullable = false,
        length = PostSource.MAX_EXTERNAL_POST_ID_LENGTH,
        columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin",
    )
    val externalPostId: String,
    @Column(name = "canonical_url", nullable = false, length = Post.MAX_CANONICAL_URL_LENGTH)
    val canonicalUrl: String,
    @Column(name = "author_identifier", nullable = true, length = Post.MAX_AUTHOR_IDENTIFIER_LENGTH)
    val authorIdentifier: String? = null,
    @Column(name = "title", nullable = true, length = Post.MAX_TITLE_LENGTH)
    val title: String? = null,
    @Column(name = "body", nullable = true, columnDefinition = "TEXT")
    val body: String? = null,
    @Column(name = "published_at", nullable = true)
    val publishedAt: Instant? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

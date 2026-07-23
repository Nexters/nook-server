package org.every.nook.api.infrastructure.persistence.group

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
    name = "group_posts",
    indexes = [Index(name = "idx_user_saved_post_id", columnList = "user_saved_post_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_group_id_user_saved_post_id",
            columnNames = ["group_id", "user_saved_post_id"],
        ),
    ],
)
class GroupPostEntity(
    @Column(name = "group_id", nullable = false)
    val groupId: Long,
    @Column(name = "user_saved_post_id", nullable = false)
    val userSavedPostId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

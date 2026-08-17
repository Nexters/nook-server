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
    name = "shared_group_subscriptions",
    indexes = [Index(name = "idx_share_link_id", columnList = "share_link_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_member_id_share_link_id",
            columnNames = ["member_id", "share_link_id"],
        ),
    ],
)
class SharedGroupSubscriptionEntity(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "share_link_id", nullable = false)
    val shareLinkId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

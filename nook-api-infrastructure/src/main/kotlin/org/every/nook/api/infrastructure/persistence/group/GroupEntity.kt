package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.group.Group
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "user_groups",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_user_id_name", columnNames = ["user_id", "name"]),
    ],
)
class GroupEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "name", nullable = false, length = Group.MAX_NAME_LENGTH)
    val name: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

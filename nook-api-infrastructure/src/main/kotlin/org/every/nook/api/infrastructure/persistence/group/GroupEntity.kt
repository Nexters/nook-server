package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.every.nook.api.domain.group.Group
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(name = "user_groups")
class GroupEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "name", nullable = false, length = Group.MAX_NAME_LENGTH)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = COLOR_COLUMN_LENGTH)
    var color: GroupColor,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val COLOR_COLUMN_LENGTH = 16
    }
}

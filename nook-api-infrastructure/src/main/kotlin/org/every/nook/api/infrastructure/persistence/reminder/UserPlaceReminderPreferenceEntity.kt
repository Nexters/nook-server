package org.every.nook.api.infrastructure.persistence.reminder

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "user_place_reminder_preferences",
    comment = "사용자별 장소 미연결 게시물 안내 설정",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_user_id", columnNames = ["user_id"])],
)
class UserPlaceReminderPreferenceEntity(
    @Column(name = "user_id", nullable = false, comment = "설정 소유 사용자 식별자")
    val userId: Long,
    @Column(name = "enabled", nullable = false, comment = "장소 연결 리마인드 사용 여부")
    var enabled: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, comment = "장소 연결 리마인드 설정 식별자")
    var id: Long? = null
        protected set
}

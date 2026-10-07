package org.every.nook.api.infrastructure.persistence.onboarding

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.onboarding.OnboardingEventName
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.Comment
import java.time.Instant

@Entity
@Comment("온보딩 CTA 및 Share Extension 클라이언트 이벤트")
@Table(
    name = "onboarding_events",
    indexes = [Index(name = "idx_event_name_member_id_occurred_at", columnList = "event_name, member_id, occurred_at")],
    uniqueConstraints = [UniqueConstraint(name = "idx_u_member_id_event_id", columnNames = ["member_id", "event_id"])],
)
class OnboardingEventEntity(
    @Comment("인증된 회원 식별자")
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Comment("클라이언트가 생성한 재전송 중복 방지 UUID")
    @Column(name = "event_id", nullable = false, length = 36, updatable = false)
    val eventId: String,
    @Comment("온보딩 이벤트 종류")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_name", nullable = false, length = 30, updatable = false)
    val eventName: OnboardingEventName,
    @Comment("클라이언트 실제 발생 시각 UTC")
    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("이벤트 행 식별자")
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

package org.every.nook.api.infrastructure.persistence.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.application.admin.PlaceReprocessingOutcome
import org.every.nook.api.application.admin.PlaceReprocessingStatus
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "place_reprocessing_jobs",
    comment = "관리자가 요청한 장소별 재처리 최신 작업",
    indexes = [Index(name = "idx_status_updated_at", columnList = "status, updated_at")],
)
class PlaceReprocessingJobEntity(
    @Id
    @Column(name = "place_id", nullable = false, comment = "재처리 대상 장소 ID")
    val placeId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, comment = "전체 처리 상태")
    var status: PlaceReprocessingStatus = PlaceReprocessingStatus.PENDING,
    @Column(name = "attempt", nullable = false, comment = "누적 실행 회차 및 오래된 결과 저장 방지 토큰")
    var attempt: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "photos", nullable = false, length = 20, comment = "사진 재수집 결과")
    var photos: PlaceReprocessingOutcome = PlaceReprocessingOutcome.PENDING,
    @Enumerated(EnumType.STRING)
    @Column(name = "tags", nullable = false, length = 20, comment = "태그 재추출 결과")
    var tags: PlaceReprocessingOutcome = PlaceReprocessingOutcome.PENDING,
) : BaseEntity()

package org.every.nook.api.infrastructure.persistence.postshare

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "post_share_links",
    comment = "게시물 단위 공유의 공개 콘텐츠와 장소 목록 스냅샷",
    indexes = [Index(name = "idx_source_saved_post_id", columnList = "source_saved_post_id")],
    uniqueConstraints = [UniqueConstraint(name = "idx_u_token_hash", columnNames = ["token_hash"])],
)
class PostShareLinkEntity(
    @Column(name = "token_hash", nullable = false, length = 64, comment = "공유 토큰 SHA-256; 원문은 저장하지 않음")
    val tokenHash: String,
    @Column(name = "source_saved_post_id", nullable = false, comment = "공유자 저장 게시물 ID")
    val sourceSavedPostId: Long,
    @Column(name = "source_post_id", nullable = false, comment = "공통 게시물 ID")
    val sourcePostId: Long,
    @Column(name = "source_generation", nullable = false, length = 36, comment = "공유 시점 원본의 삭제 세대")
    val sourceGeneration: String,
    @Column(name = "snapshot", nullable = false, columnDefinition = "MEDIUMTEXT", comment = "개인 정보 제외 공개 JSON")
    val snapshot: String,
    @Column(name = "captured_at", nullable = false, columnDefinition = "TIMESTAMP(6)", comment = "공유 스냅샷 생성 시각")
    val capturedAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, comment = "게시물 공유 링크 ID")
    var id: Long? = null
        protected set
}

package org.every.nook.api.infrastructure.persistence.auth

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenIdentifier = :identifier")
    fun findByIdentifierForUpdate(@Param("identifier") identifier: String): RefreshTokenEntity?

    @Modifying
    @Query(
        """
        update RefreshTokenEntity token
        set token.revokedAt = :revokedAt
        where token.member.id = :memberId and token.revokedAt is null
        """,
    )
    fun revokeActiveTokens(@Param("memberId") memberId: Long, @Param("revokedAt") revokedAt: Instant)
}

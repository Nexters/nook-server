package org.every.nook.api.infrastructure.persistence.push

import org.every.nook.api.application.push.PushPlatform
import org.every.nook.api.application.push.PushToken
import org.every.nook.api.application.push.PushTokenPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class PushTokenPersistenceAdapter(
    private val repository: UserPushTokenJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : PushTokenPort {
    @Transactional
    override fun register(userId: Long, token: String, platform: PushPlatform) {
        val now = clock.instant()
        val entity = repository.findByToken(token)
        if (entity == null) {
            repository.saveAndFlush(
                UserPushTokenEntity(
                    userId = userId,
                    token = token,
                    platform = platform,
                    lastRegisteredAt = now,
                ),
            )
        } else {
            entity.register(userId, platform, now)
        }
    }

    @Transactional
    override fun delete(userId: Long, token: String) {
        repository.findByUserIdAndToken(userId, token)
            ?.disable(DELETE_REASON, clock.instant())
    }

    @Transactional(readOnly = true)
    override fun findEnabledTokensByPostId(postId: Long): List<PushToken> = repository
        .findAllEnabledByPostId(postId)
        .map { PushToken(it.token, it.platform) }

    @Transactional
    override fun disable(tokens: Collection<String>, reason: String) {
        if (tokens.isEmpty()) {
            return
        }
        val now = clock.instant()
        repository.findAllByTokenIn(tokens).forEach { token ->
            token.disable(reason, now)
        }
    }

    private companion object {
        const val DELETE_REASON = "Deleted by user"
    }
}

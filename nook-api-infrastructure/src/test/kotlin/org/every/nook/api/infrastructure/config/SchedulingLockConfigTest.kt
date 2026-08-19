package org.every.nook.api.infrastructure.config

import net.javacrumbs.shedlock.core.LockConfiguration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulingLockConfigTest {
    @Test
    fun `a held database lock rejects a concurrent acquisition`() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:shedlock;MODE=MySQL;DB_CLOSE_DELAY=-1")
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(
            """
            CREATE TABLE shedlock (
                name VARCHAR(64) NOT NULL PRIMARY KEY,
                lock_until TIMESTAMP(3) NOT NULL,
                locked_at TIMESTAMP(3) NOT NULL,
                locked_by VARCHAR(255) NOT NULL
            )
            """.trimIndent(),
        )
        val lockProvider = SchedulingLockConfig().schedulingLockProvider(dataSource)
        val configuration = LockConfiguration(
            Instant.now(),
            "test.concurrentLock",
            Duration.ofSeconds(30),
            Duration.ZERO,
        )

        val firstLock = lockProvider.lock(configuration)
        val concurrentLock = lockProvider.lock(configuration)

        assertTrue(firstLock.isPresent)
        assertFalse(concurrentLock.isPresent)
        firstLock.orElseThrow().unlock()
    }
}

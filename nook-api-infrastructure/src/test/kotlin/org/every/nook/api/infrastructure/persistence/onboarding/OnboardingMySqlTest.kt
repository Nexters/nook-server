package org.every.nook.api.infrastructure.persistence.onboarding

import org.every.nook.api.application.onboarding.OnboardingEvent
import org.every.nook.api.application.onboarding.OnboardingEventName
import org.every.nook.api.infrastructure.persistence.analytics.UserAnalyticsEventJpaRepository
import org.hibernate.cfg.Configuration
import org.mockito.Mockito.mock
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingMySqlTest {
    @Test
    fun `MySQL 84 DDL and transactional upsert preserve event identity and support rollback`() {
        MySQLContainer("mysql:8.4").use { mysql ->
            mysql.start()
            val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            val jdbc = JdbcTemplate(dataSource)
            repeat(2) { jdbc.execute(ddl("up.sql")) }
            Configuration().addAnnotatedClass(OnboardingEventEntity::class.java)
                .setProperty("hibernate.connection.url", mysql.jdbcUrl)
                .setProperty("hibernate.connection.username", mysql.username)
                .setProperty("hibernate.connection.password", mysql.password)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.jdbc.time_zone", "UTC")
                .buildSessionFactory().use { factory ->
                    val manager = JpaTransactionManager(factory)
                    val transaction = TransactionTemplate(manager)
                    val entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory)
                    val repository = JpaRepositoryFactory(entityManager)
                        .getRepository(OnboardingEventJpaRepository::class.java)
                    val adapter = OnboardingPersistenceAdapter(
                        repository,
                        mock(UserAnalyticsEventJpaRepository::class.java),
                    )
                    verifyIdempotency(transaction, adapter, jdbc)
                }
            repeat(2) { jdbc.execute(ddl("rollback.sql")) }
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'onboarding_events'",
                    Int::class.java,
                ),
            )
        }
    }

    private fun verifyIdempotency(
        transaction: TransactionTemplate,
        adapter: OnboardingPersistenceAdapter,
        jdbc: JdbcTemplate,
    ) {
        val event = OnboardingEvent(
            7,
            UUID.randomUUID(),
            OnboardingEventName.INSTAGRAM_CTA_CLICK,
            Instant.parse("2026-10-07T14:00:00.123456Z"),
        )
        repeat(2) { transaction.executeWithoutResult { adapter.store(event) } }
        transaction.executeWithoutResult { adapter.store(event.copy(memberId = 8)) }
        assertFailsWith<IllegalArgumentException> {
            transaction.executeWithoutResult { adapter.store(event.copy(occurredAt = event.occurredAt.plusSeconds(1))) }
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_events", Int::class.java))
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                    "WHERE table_schema = DATABASE() AND table_name = 'onboarding_events' " +
                    "AND constraint_type = 'FOREIGN KEY'",
                Int::class.java,
            ),
        )
    }

    private fun ddl(name: String): String = Path.of("..", "docs", "tasks", "NOOK-400", "ddl", name).readText()
}

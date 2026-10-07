package org.every.nook.api.infrastructure.persistence.onboarding

import jakarta.persistence.EntityManagerFactory
import org.every.nook.api.application.onboarding.OnboardingEvent
import org.every.nook.api.application.onboarding.OnboardingEventName
import org.every.nook.api.application.onboarding.OnboardingPeriod
import org.every.nook.api.infrastructure.persistence.analytics.UserAnalyticsEventEntity
import org.every.nook.api.infrastructure.persistence.analytics.UserAnalyticsEventJpaRepository
import org.every.nook.api.infrastructure.persistence.member.MemberEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringJUnitConfig(OnboardingRepositoryTest.Config::class)
@Transactional
class OnboardingRepositoryTest {
    @Autowired
    private lateinit var repository: OnboardingEventJpaRepository

    @Autowired
    private lateinit var analytics: UserAnalyticsEventJpaRepository

    @Autowired
    private lateinit var dataSource: DataSource

    private val start = Instant.parse("2026-10-01T15:00:00Z")
    private val period = OnboardingPeriod(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-02"))

    @Test
    fun `duplicate delivery preserves original event and does not modify other member`() {
        val adapter = OnboardingPersistenceAdapter(repository, analytics)
        val event = OnboardingEvent(7, UUID.randomUUID(), OnboardingEventName.INSTAGRAM_CTA_CLICK, start)
        adapter.store(event)
        adapter.store(event)
        adapter.store(event.copy(memberId = 8))
        assertEquals(2, repository.count())
        assertFailsWith<IllegalArgumentException> { adapter.store(event.copy(occurredAt = start.plusSeconds(1))) }
        assertEquals(start, repository.findByMemberIdAndEventId(7, event.eventId.toString()).occurredAt)
    }

    @Test
    fun `cohort uses first ever click despite repeated clicks and delayed arrival`() {
        click(1, start.minusSeconds(1))
        click(1, start)
        click(2, start.plusSeconds(100))
        click(2, start)
        click(3, start.plusSeconds(86400))
        val cohort = repository.cohort(period.fromInstant, period.toExclusive, start.plusSeconds(86400))
        assertEquals(listOf(2L), cohort.map { it.memberId })
        assertEquals(start, cohort.single().clickedAt)
    }

    @Test
    fun `queries followup saves beyond selected date and ignores unrelated members and event kinds`() {
        val lateClick = start.plusSeconds(86300)
        click(1, lateClick)
        val savedAt = lateClick.plusSeconds(600)
        save(1, savedAt)
        save(2, savedAt)
        save(1, savedAt, "POST_VIEW")
        save(1, lateClick.minusSeconds(1))
        save(1, lateClick.plusSeconds(86401))
        repository.insertIdempotently(1, UUID.randomUUID().toString(), "SHARE_EXTENSION_OPEN", savedAt)
        val journeys = OnboardingPersistenceAdapter(repository, analytics)
            .journeys(period, lateClick.plusSeconds(86400))
        assertEquals(1, journeys.size)
        assertEquals(listOf(savedAt), journeys.single().saves.map { it.occurredAt })
        assertEquals(listOf(savedAt), journeys.single().extensionEntries)
    }

    private fun click(member: Long, at: Instant) {
        repository.insertIdempotently(member, UUID.randomUUID().toString(), "INSTAGRAM_CTA_CLICK", at)
    }

    private fun save(member: Long, at: Instant, name: String = "POST_SAVE") {
        val id = UUID.randomUUID().toString()
        JdbcTemplate(dataSource).update(
            """
            INSERT INTO analytics_events
            (event_id, deduplication_key, event_name, member_id, target_type, target_id,
             occurred_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'POST', 42, ?, ?, ?)
            """.trimIndent(),
            id,
            "RAW:$id",
            name,
            member,
            Timestamp.from(at),
            Timestamp.from(at),
            Timestamp.from(at),
        )
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
        basePackageClasses = [OnboardingEventJpaRepository::class, UserAnalyticsEventJpaRepository::class],
    )
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:onboarding;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan(
                OnboardingEventEntity::class.java.packageName,
                UserAnalyticsEventEntity::class.java.packageName,
                MemberEntity::class.java.packageName,
            )
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop", "hibernate.jdbc.time_zone" to "UTC"))
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }
}

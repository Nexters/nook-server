package org.every.nook.api.infrastructure.persistence.analytics

import jakarta.persistence.EntityManagerFactory
import org.every.nook.api.application.analytics.AnalyticsActivityQuery
import org.every.nook.api.application.analytics.GetUserAnalyticsOverviewUseCase
import org.every.nook.api.application.analytics.UserAnalyticsEvent
import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventQueryPort
import org.every.nook.api.application.analytics.UserAnalyticsPeriod
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringJUnitConfig(AnalyticsActivityRepositoryTest.Config::class)
@Transactional
class AnalyticsActivityRepositoryTest {
    @Autowired
    private lateinit var repository: AnalyticsActivityJpaRepository

    @Autowired
    private lateinit var dataSource: DataSource

    private val date = LocalDate.parse("2026-09-09")
    private val start = Instant.parse("2026-09-08T15:00:00Z")
    private val fixture = mutableListOf<UserAnalyticsEvent>()

    @BeforeTest
    fun seed() {
        val jdbc = JdbcTemplate(dataSource)
        jdbc.update(
            "INSERT INTO members (id, nickname, status) VALUES (1, '테스트 회원', 'ACTIVE'), (2, '탈퇴 회원', 'WITHDRAWN')",
        )
        (1L..105L).forEach { add(it, start) }
        add(1, start.plusSeconds(1), UserAnalyticsEventName.POST_SAVE)
        add(201, start.minusSeconds(6 * DAY_SECONDS))
        add(202, start.minusSeconds(29 * DAY_SECONDS))
        add(203, start.minusSeconds(30 * DAY_SECONDS))
        add(204, start.plusSeconds(DAY_SECONDS))
    }

    @Test
    fun `paged distinct members equal DAU WAU MAU and retain missing or withdrawn members`() {
        val adapter = AnalyticsActivityPersistenceAdapter(repository)
        val overview = GetUserAnalyticsOverviewUseCase(
            UserAnalyticsEventQueryPort { from, to -> fixture.filter { it.occurredAt >= from && it.occurredAt < to } },
            Clock.fixed(start.plusSeconds(1), ZoneOffset.UTC),
        )(UserAnalyticsPeriod(date, date, 3))
        val counts = listOf(overview.activeUsers.daily, overview.activeUsers.weekly, overview.activeUsers.monthly)
        listOf(1L, 7L, 30L).zip(counts).forEach { (days, count) ->
            val result = adapter.members(AnalyticsActivityQuery(date.minusDays(days - 1), date, size = 100))
            assertEquals(count, result.total)
        }
        val first = adapter.members(AnalyticsActivityQuery(date, date, size = 100))
        val next = adapter.members(AnalyticsActivityQuery(date, date, page = 1, size = 100))
        assertEquals(105L, first.total)
        assertEquals(100, first.items.size)
        assertEquals((101L..105L).toList(), next.items.map { it.memberId })
        assertEquals("테스트 회원", first.items.first().nickname)
        assertEquals(2L, first.items.first().events)
        assertEquals(start.plusSeconds(1), first.items.first().lastEventAt)
        assertNull(first.items.single { it.memberId == 2L }.nickname)
        assertNull(first.items.single { it.memberId == 3L }.nickname)
    }

    @Test
    fun `event filters and member timelines share inclusive KST day and exclusive next midnight`() {
        val adapter = AnalyticsActivityPersistenceAdapter(repository)
        val query = AnalyticsActivityQuery(date, date, UserAnalyticsEventName.POST_SAVE, size = 1)
        assertEquals(listOf(1L), adapter.members(query).items.map { it.memberId })
        assertEquals(1L, adapter.members(query).total)
        val filtered = adapter.events(query, 1)
        assertEquals(1L, filtered.total)
        assertEquals(UserAnalyticsEventName.POST_SAVE, filtered.items.single().eventName)
        assertEquals(42L, filtered.items.single().targetId)
        val all = query.copy(eventName = null)
        assertEquals(2L, adapter.events(all, 1).total)
        assertEquals(start.plusSeconds(1), adapter.events(all, 1).items.single().occurredAt)
        assertEquals(start, adapter.events(all.copy(page = 1), 1).items.single().occurredAt)
        assertEquals(0L, adapter.events(all, 204).total)
        assertEquals(0L, adapter.members(query.copy(from = date.minusDays(1), to = date.minusDays(1))).total)
    }

    private fun add(memberId: Long, at: Instant, name: UserAnalyticsEventName = UserAnalyticsEventName.POST_VIEW) {
        val id = "event-${fixture.size}"
        fixture += UserAnalyticsEvent(id, "RAW:$id", name, memberId, null, 42, at)
        JdbcTemplate(dataSource).update(
            """
            INSERT INTO analytics_events
            (event_id, deduplication_key, event_name, member_id, target_id, occurred_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 42, ?, ?, ?)
            """.trimIndent(),
            id,
            "RAW:$id",
            name.name,
            memberId,
            Timestamp.from(at),
            Timestamp.from(at),
            Timestamp.from(at),
        )
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = [AnalyticsActivityJpaRepository::class])
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:analytics-activity;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan(UserAnalyticsEventEntity::class.java.packageName, MemberEntity::class.java.packageName)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop", "hibernate.jdbc.time_zone" to "UTC"))
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }

    private companion object {
        const val DAY_SECONDS = 86400L
    }
}

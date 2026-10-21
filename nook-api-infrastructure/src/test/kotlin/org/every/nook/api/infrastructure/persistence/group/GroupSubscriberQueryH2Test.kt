package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.EntityManagerFactory
import org.every.nook.api.infrastructure.persistence.group.subscriber.GroupSubscriberJpaRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringJUnitConfig(GroupSubscriberQueryH2Test.Config::class)
@Transactional
class GroupSubscriberQueryH2Test {
    @Autowired
    private lateinit var repository: GroupSubscriberJpaRepository

    @Autowired
    private lateinit var dataSource: DataSource

    private val now = Instant.parse("2026-10-21T00:00:00Z")
    private val adapter by lazy { GroupSubscriberQueryAdapter(repository, Clock.fixed(now, ZoneOffset.UTC)) }
    private val jdbc by lazy { JdbcTemplate(dataSource) }

    @BeforeTest
    fun seed() {
        (1..9).forEach { id ->
            jdbc.update(
                "INSERT INTO members (id,nickname,profile_image_url,status,created_at,updated_at) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                "member$id",
                if (id == 8) null else "https://cdn.test/$id.jpg",
                if (id == 4) "WITHDRAWN" else "ACTIVE",
            )
        }
        listOf(11, 12, 13).forEach { id ->
            jdbc.update(
                "INSERT INTO user_groups (id,user_id,name,color,deleted_at,created_at,updated_at) " +
                    "VALUES (?,1,'archive','YELLOW',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id,
                if (id == 13) java.sql.Timestamp.from(now) else null,
            )
        }
        link(101, 11)
        link(102, 11)
        link(103, 11, revoked = true)
        link(104, 11, expired = true)
        link(105, 12)
        link(106, 13)
        listOf(1, 2, 3, 4, 8, 9).forEach { subscribe(it, 101, it) }
        subscribe(2, 102, 10)
        subscribe(5, 104, 11)
        subscribe(6, 103, 12)
        subscribe(2, 105, 13)
        subscribe(2, 106, 14)
    }

    @Test
    fun `summary counts unique active members and returns only three recent profiles per archive`() {
        val summaries = adapter.findSummaries(setOf(11, 12, 13))
        val summary = requireNotNull(summaries[11])
        assertEquals(4, summary.count)
        assertEquals(listOf("member2", "member9", "member8"), summary.preview.map { it.nickname })
        assertNull(summary.preview.last().profileImageUrl)
        assertEquals(1, summaries[12]?.count)
        assertFalse(summaries.containsKey(13))
    }

    @Test
    fun `pages use the same count and order as preview without duplicate members`() {
        val first = adapter.findPage(11, 0, 2)
        val second = adapter.findPage(11, 1, 2)
        assertEquals(listOf("member2", "member9"), first.items.map { it.nickname })
        assertEquals(listOf("member8", "member3"), second.items.map { it.nickname })
        assertEquals(4, first.totalElements)
        assertEquals(2, first.totalPages)
        assertTrue(first.hasNext)
        assertFalse(second.hasNext)
        assertEquals(first.totalElements, second.totalElements)
        assertTrue(adapter.findPage(11, 9, 2).items.isEmpty())
    }

    @Test
    fun `unsubscribe revoke expiration withdrawal and group deletion immediately remove members`() {
        jdbc.update("DELETE FROM shared_group_subscriptions WHERE member_id=2")
        assertEquals(3, adapter.findSummaries(setOf(11))[11]?.count)
        jdbc.update("UPDATE members SET status='WITHDRAWN' WHERE id=9")
        assertEquals(2, adapter.findPage(11, 0, 20).totalElements)
        jdbc.update("UPDATE group_share_links SET revoked_at=CURRENT_TIMESTAMP WHERE id=101")
        assertTrue(adapter.findSummaries(setOf(11)).isEmpty())
        assertEquals(0, adapter.findPage(11, 0, 20).totalElements)
        assertTrue(adapter.findSummaries(emptySet()).isEmpty())
    }

    private fun link(id: Int, group: Int, revoked: Boolean = false, expired: Boolean = false) {
        jdbc.update(
            "INSERT INTO group_share_links " +
                "(id,group_id,token_hash,token_value,revoked_at,expires_at,created_at,updated_at) " +
                "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            id,
            group,
            "hash$id",
            "token$id",
            if (revoked) java.sql.Timestamp.from(now.minusSeconds(1)) else null,
            if (expired) java.sql.Timestamp.from(now) else null,
        )
    }

    private fun subscribe(member: Int, link: Int, order: Int) {
        jdbc.update(
            "INSERT INTO shared_group_subscriptions (member_id,share_link_id,created_at,updated_at) " +
                "VALUES (?,?,?,CURRENT_TIMESTAMP)",
            member,
            link,
            java.sql.Timestamp.from(now.minusSeconds(100 - order.toLong())),
        )
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = [GroupSubscriberJpaRepository::class])
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:group-subscribers;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan(GroupEntity::class.java.packageName, MemberEntity::class.java.packageName)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }
}

package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.PlaceReprocessingActiveException
import org.every.nook.api.application.admin.PlaceReprocessingOutcome
import org.every.nook.api.application.admin.PlaceReprocessingPort
import org.every.nook.api.application.admin.PlaceReprocessingResult
import org.every.nook.api.application.admin.PlaceReprocessingSource
import org.every.nook.api.application.admin.PlaceReprocessingStatus
import org.every.nook.api.application.admin.RequestPlaceReprocessingCommand
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaDialect
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceReprocessingMySqlTest {
    private val mysql = MySQLContainer("mysql:8.4")
    private lateinit var factory: SessionFactory
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: TransactionTemplate
    private lateinit var places: PlaceJpaRepository
    private lateinit var port: PlaceReprocessingPort
    private var placeId = 0L

    @BeforeAll
    fun start() {
        mysql.start()
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        jdbc = JdbcTemplate(dataSource)
        factory = Configuration()
            .addAnnotatedClass(PlaceEntity::class.java)
            .addAnnotatedClass(PlaceReprocessingJobEntity::class.java)
            .setProperty("hibernate.connection.url", mysql.jdbcUrl)
            .setProperty("hibernate.connection.username", mysql.username)
            .setProperty("hibernate.connection.password", mysql.password)
            .setProperty("hibernate.hbm2ddl.auto", "create-drop")
            .buildSessionFactory()
        // Exercise the actual migration, not Hibernate's generated queue table.
        jdbc.execute("DROP TABLE place_reprocessing_jobs")
        jdbc.execute(Files.readString(Path.of("../docs/tasks/NOOK-402/ddl/up.sql")))
        val manager = JpaTransactionManager(factory).apply {
            setDataSource(dataSource)
            setJpaDialect(HibernateJpaDialect())
        }
        transactions = TransactionTemplate(manager)
        val repositories = JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
        places = repositories.getRepository(PlaceJpaRepository::class.java)
        val delegate = repositories.getRepository(PlaceReprocessingJobJpaRepository::class.java)
        val jobs = object : PlaceReprocessingJobJpaRepository by delegate {
            override fun <S : PlaceReprocessingJobEntity> saveAndFlush(entity: S): S {
                stamp(entity)
                return delegate.saveAndFlush(entity)
            }
        }
        port = ProxyFactory(
            PlaceReprocessingAdapter(
                jobs,
                places,
                mock(AdminAuditLogPort::class.java),
                PlaceReprocessingProtection(mock(AdminAuditLogJpaRepository::class.java), jacksonObjectMapper()),
            ),
        ).apply {
            addAdvice(
                TransactionInterceptor().apply {
                    transactionManager = manager
                    transactionAttributeSource = AnnotationTransactionAttributeSource()
                },
            )
        }.getProxy() as PlaceReprocessingPort
    }

    @AfterAll
    fun stop() {
        if (::factory.isInitialized) factory.close()
        mysql.stop()
    }

    @BeforeTest
    fun reset() {
        jdbc.update("DELETE FROM place_reprocessing_jobs")
        jdbc.update("DELETE FROM places")
        placeId = requireNotNull(
            transactions.execute { places.saveAndFlush(fixturePlace("123", "누크 카페")).id },
        )
    }

    @Test
    fun `concurrent first requests create one pending job and reject the duplicate`() {
        val barrier = CyclicBarrier(2)
        val results = concurrent {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
            runCatching { port.enqueue(command()) }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is PlaceReprocessingActiveException })
        assertEquals(PlaceReprocessingStatus.PENDING, port.find(placeId)?.status)
    }

    @Test
    fun `two workers cannot claim the same job`() {
        port.enqueue(command())
        val barrier = CyclicBarrier(2)
        val claims = concurrent {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
            port.claim()
        }
        assertEquals(1, claims.count { it != null })
    }

    @Test
    fun `expired attempt cannot change a manually retried job`() {
        port.enqueue(command())
        val old = requireNotNull(port.claim())
        jdbc.update("UPDATE place_reprocessing_jobs SET updated_at = '2026-01-01 00:00:00'")
        assertEquals(null, port.claim())
        assertEquals(PlaceReprocessingStatus.FAILED, port.find(placeId)?.status)
        port.enqueue(command())
        val current = requireNotNull(port.claim())
        assertEquals(old.attempt + 1, current.attempt)
        assertFalse(port.finish(old, result()))
        assertFalse(port.fail(old))
        assertTrue(port.finish(current, result()))
        assertEquals(
            "https://example.com/refreshed.jpg",
            transactions.execute {
                places.findById(placeId).orElseThrow().thumbnailUrl
            },
        )
    }

    @Test
    fun `result write and job status roll back together`() {
        port.enqueue(command())
        val job = requireNotNull(port.claim())
        assertFailsWith<IllegalStateException> {
            port.finish(job, result().copy(tagChanges = listOf({ error("tag persistence failure") })))
        }
        assertEquals(PlaceReprocessingStatus.PROCESSING, port.find(placeId)?.status)
        assertEquals(null, transactions.execute { places.findById(placeId).orElseThrow().thumbnailUrl })
    }

    @Test
    fun `photo absence filter uses both thumbnail and photo list with matching total`() {
        transactions.executeWithoutResult {
            places.saveAndFlush(fixturePlace("456", "사진만 있는 카페", listOf("https://example.com/photo.jpg")))
        }
        transactions.executeWithoutResult {
            assertEquals(listOf(placeId), places.findAdminPage(null, 0, 10, true).map { it.id })
            assertEquals(1, places.countAdminPlaces(null, true))
            assertEquals(0, places.countAdminPlaces("사진만", true))
            assertEquals(2, places.countAdminPlaces(null))
        }
    }

    private fun result(): PlaceReprocessingResult {
        val place = requireNotNull(transactions.execute { places.findById(placeId).orElseThrow() })
        return PlaceReprocessingResult(
            PlaceReprocessingSource(place.toCandidate(), null, emptyList(), emptyList(), emptyList()),
            PlaceSupplement(null, listOf("https://example.com/refreshed.jpg")),
            false,
            PlaceReprocessingOutcome.NO_SOURCE,
            emptyList(),
        )
    }

    private fun command() = RequestPlaceReprocessingCommand(
        placeId,
        AdminActor("admin", "admin@example.com"),
        "재시도 검증",
        null,
    )

    private fun <T> concurrent(action: () -> T): List<T> = Executors.newFixedThreadPool(2).use { pool ->
        (1..2).map { pool.submit(Callable { action() }) }.map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun fixturePlace(externalId: String, name: String, photos: List<String> = emptyList()) = stamp(
        PlaceEntity(
            "KAKAO",
            externalId,
            name,
            "서울",
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            photoUrls = photos,
        ),
    )

    // Standalone Hibernate fixtures initialize audit fields; production uses JpaAuditingConfig.
    private fun <T : org.every.nook.api.infrastructure.persistence.BaseEntity> stamp(entity: T): T {
        if (ReflectionTestUtils.getField(entity, "createdAt") == null) {
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now())
        }
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now())
        return entity
    }

    private companion object {
        const val WAIT_SECONDS = 30L
    }
}

package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminParsingRecoveryPort
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.hibernate.cfg.Configuration
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.testcontainers.containers.MySQLContainer
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Disposable MySQL only; schema generation is never used against application databases. */
internal class ParsingMySqlFixture : AutoCloseable {
    private val mysql = MySQLContainer("mysql:8.4").apply { start() }
    private val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
    val jdbc = JdbcTemplate(dataSource)
    private val factory = Configuration()
        .addAnnotatedClass(ParsingFollowUpJobEntity::class.java)
        .addAnnotatedClass(PostContentParsingJobEntity::class.java)
        .addAnnotatedClass(PlaceParsingJobEntity::class.java)
        .setProperty("hibernate.connection.url", mysql.jdbcUrl)
        .setProperty("hibernate.connection.username", mysql.username)
        .setProperty("hibernate.connection.password", mysql.password)
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .buildSessionFactory()
    private val manager = JpaTransactionManager(factory)
    private val entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory)
    private val repositories = JpaRepositoryFactory(entityManager)
    val contentJobs: PostContentParsingJobJpaRepository =
        repositories.getRepository(PostContentParsingJobJpaRepository::class.java)
    val placeJobs: PlaceParsingJobJpaRepository = repositories.getRepository(PlaceParsingJobJpaRepository::class.java)
    private val repository = repositories.getRepository(ParsingFollowUpJobJpaRepository::class.java)
    val jobs: ParsingFollowUpJobPort = ProxyFactory(
        ParsingFollowUpPersistenceAdapter(repository, jacksonObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC)),
    ).apply {
        addAdvice(
            TransactionInterceptor().apply {
                transactionManager = manager
                transactionAttributeSource = AnnotationTransactionAttributeSource()
            },
        )
    }.getProxy() as ParsingFollowUpJobPort

    fun recovery(audit: AdminAuditLogPort): AdminParsingRecoveryPort = ProxyFactory(
        AdminParsingRecoveryAdapter(repository, audit, Clock.fixed(NOW, ZoneOffset.UTC)),
    ).apply {
        addAdvice(
            TransactionInterceptor().apply {
                transactionManager = manager
                transactionAttributeSource = AnnotationTransactionAttributeSource()
            },
        )
    }.getProxy() as AdminParsingRecoveryPort

    fun insertMediaJob() {
        jdbc.update(
            """
            INSERT INTO parsing_follow_up_jobs
                (job_type, post_id, payload, status, attempt_count, retry_attempt_count, next_attempt_at)
            VALUES ('POST_MEDIA', 1, ?, 'PENDING', 0, 0, '2026-01-01 00:00:00')
            """.trimIndent(),
            """{"postId":1,"mediaType":"IMAGE","sourceUrl":"https://example.com/image.jpg","sequence":0}""",
        )
    }

    fun writeResultValue(jobId: Long, value: String) {
        entityManager.createNativeQuery("UPDATE parsing_follow_up_jobs SET failure_reason = :value WHERE id = :id")
            .setParameter("value", value)
            .setParameter("id", jobId)
            .executeUpdate()
    }

    override fun close() {
        factory.close()
        mysql.stop()
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-09-13T00:00:00Z")
    }
}

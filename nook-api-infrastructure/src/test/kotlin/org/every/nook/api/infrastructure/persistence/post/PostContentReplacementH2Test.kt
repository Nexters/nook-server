package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.EntityManagerFactory
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.jpa.boot.spi.IntegratorProvider
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringJUnitConfig(PostContentReplacementH2Test.Config::class)
class PostContentReplacementH2Test {
    @Autowired
    private lateinit var factory: EntityManagerFactory

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val repositories by lazy {
        JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
    }
    private val posts by lazy { repositories.getRepository(PostJpaRepository::class.java) }
    private val jobs by lazy { repositories.getRepository(PostContentParsingJobJpaRepository::class.java) }
    private val media by lazy { repositories.getRepository(PostMediaJpaRepository::class.java) }
    private val hashtags by lazy { repositories.getRepository(PostHashtagJpaRepository::class.java) }
    private val places by lazy { repositories.getRepository(PlaceParsingJobJpaRepository::class.java) }

    @Test
    fun `reprocessing replaces existing media and overlapping hashtags repeatedly without affecting another post`() {
        val target = seed()
        val other = seed()
        repeat(2) { index ->
            tx {
                val job = requireNotNull(jobs.findByPostIdForUpdate(target))
                job.status = PostContentParsingStatus.PROCESSING
                job.attemptCount = index + 1
                assertTrue(adapter().complete(target, index + 1, replacement(target), emptyList()))
            }
            tx {
                assertEquals(
                    listOf("https://cdn.test/new.jpg"),
                    media.findAllByPostIdOrderBySequenceAsc(target)
                        .map { it.mediaUrl },
                )
                assertEquals(
                    listOf("keep", "new"),
                    hashtags.findAllByPostIdOrderBySequenceAsc(target).map { it.hashtag },
                )
                assertEquals(PostContentParsingStatus.COMPLETED, jobs.findByPostId(target)?.status)
                assertEquals("old", posts.findById(other).orElseThrow().body)
                assertEquals(
                    listOf("https://cdn.test/old.jpg"),
                    media.findAllByPostIdOrderBySequenceAsc(other)
                        .map { it.mediaUrl },
                )
                assertEquals(
                    listOf("keep", "old"),
                    hashtags.findAllByPostIdOrderBySequenceAsc(other).map { it.hashtag },
                )
            }
        }
    }

    @Test
    fun `downstream failure rolls back flushed deletes inserted rows and completion status`() {
        val target = seed()
        val followUps = mock(ParsingFollowUpJobPort::class.java) {
            throw IllegalStateException("follow-up persistence failed")
        }
        val failure = assertFailsWith<IllegalStateException> {
            tx { adapter(followUps).complete(target, 1, replacement(target), emptyList()) }
        }
        assertEquals("follow-up persistence failed", failure.message)
        tx {
            assertEquals("old", posts.findById(target).orElseThrow().body)
            assertEquals(
                listOf("https://cdn.test/old.jpg"),
                media.findAllByPostIdOrderBySequenceAsc(target)
                    .map { it.mediaUrl },
            )
            assertEquals(listOf("keep", "old"), hashtags.findAllByPostIdOrderBySequenceAsc(target).map { it.hashtag })
            assertEquals(PostContentParsingStatus.PROCESSING, jobs.findByPostId(target)?.status)
            assertNull(places.findByPostId(target))
        }
    }

    @Test
    fun `manual content remains protected when completing a retry`() {
        val target = seed()
        tx {
            posts.findById(target).orElseThrow().contentManuallyOverridden = true
            assertTrue(adapter().complete(target, 1, replacement(target), emptyList()))
        }
        tx {
            assertEquals("old", posts.findById(target).orElseThrow().body)
            assertEquals("https://cdn.test/old.jpg", media.findAllByPostIdOrderBySequenceAsc(target).single().mediaUrl)
            assertEquals(listOf("keep", "old"), hashtags.findAllByPostIdOrderBySequenceAsc(target).map { it.hashtag })
        }
    }

    private fun seed(): Long = tx {
        val key = UUID.randomUUID().toString()
        val saved = posts.saveAndFlush(PostEntity("INSTAGRAM", key, "https://source.test/$key", body = "old"))
        val id = requireNotNull(saved.id)
        jobs.saveAndFlush(PostContentParsingJobEntity(id, PostContentParsingStatus.PROCESSING, attemptCount = 1))
        media.saveAndFlush(PostMediaEntity(id, PostMedia.MediaType.IMAGE, "https://cdn.test/old.jpg", 0))
        hashtags.saveAllAndFlush(listOf(PostHashtagEntity(id, "keep", 0), PostHashtagEntity(id, "old", 1)))
        id
    }

    private fun replacement(id: Long): Post {
        val entity = posts.findById(id).orElseThrow()
        return Post(
            source = PostSource(entity.sourceType, entity.externalPostId),
            canonicalUrl = entity.canonicalUrl,
            body = "new",
            hashtags = listOf("keep", "new"),
            media = listOf(PostMedia(PostMedia.MediaType.IMAGE, "https://cdn.test/new.jpg", 0)),
        )
    }

    private fun adapter(followUps: ParsingFollowUpJobPort = mock(ParsingFollowUpJobPort::class.java)) =
        PostContentParsingPersistenceAdapter(
            jobs,
            posts,
            media,
            hashtags,
            places,
            mock(ApplicationEventPublisher::class.java),
            followUps,
            jacksonObjectMapper(),
        )

    private fun <T : Any> tx(block: () -> T): T =
        requireNotNull(TransactionTemplate(transactionManager).execute { block() })

    @Configuration
    @EnableJpaAuditing
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:post-content-replacement;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setManagedTypes(
                PersistenceManagedTypes.of(
                    PostEntity::class.java.name,
                    PostContentParsingJobEntity::class.java.name,
                    PostMediaEntity::class.java.name,
                    PostHashtagEntity::class.java.name,
                    PlaceParsingJobEntity::class.java.name,
                ),
            )
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(
                mapOf(
                    "hibernate.hbm2ddl.auto" to "create-drop",
                    "hibernate.integrator_provider" to IntegratorProvider { listOf(H2ConstraintNames) },
                ),
            )
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }

    // H2 requires schema-wide names, whereas MySQL allows the same index name on different tables.
    private object H2ConstraintNames : Integrator {
        override fun integrate(
            metadata: Metadata,
            bootstrapContext: BootstrapContext,
            sessionFactory: SessionFactoryImplementor,
        ) {
            metadata.database.namespaces.forEach { namespace ->
                namespace.tables.forEach { table ->
                    table.uniqueKeys.values.forEach { it.name = "${table.name}_${it.name}" }
                    table.indexes.values.forEach { it.name = "${table.name}_${it.name}" }
                }
            }
        }
    }
}

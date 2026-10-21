package org.every.nook.api.infrastructure.persistence.push

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import org.every.nook.api.application.push.PushPlatform
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringJUnitConfig(PostProcessingPushTargetTest.Config::class)
@Transactional
class PostProcessingPushTargetTest {
    @Autowired
    private lateinit var repository: UserPushTokenJpaRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val now = Instant.parse("2026-10-21T00:00:00Z")

    @Test
    fun `maps the original post to each users saved id without duplicate devices for multiple archives`() {
        val first = saved(7)
        val second = saved(8)
        link(first, 7)
        link(first, 7)
        link(second, 8)
        token(7, "phone")
        token(7, "tablet")
        token(8, "other-user")
        token(9, "not-saved")
        val otherPost = saved(10, 999)
        link(otherPost, 10)
        token(10, "other-post")
        entityManager.flush()
        val targets = PushTokenPersistenceAdapter(repository).findPostProcessingTargets(101)
        assertEquals(3, targets.size)
        assertEquals(setOf("phone", "tablet"), targets.filter { it.savedPostId == first.id }.map { it.token }.toSet())
        assertEquals(listOf("other-user"), targets.filter { it.savedPostId == second.id }.map { it.token })
        assertEquals(setOf(7L, 8L), targets.map { it.userId }.toSet())
    }

    @Test
    fun `excludes deleted saves deleted archive links deleted archives disabled tokens and wrong owners`() {
        val deletedSave = saved(1)
        link(deletedSave, 1)
        deletedSave.softDelete(now)
        val deletedLink = saved(2)
        link(deletedLink, 2).second.softDelete(now)
        val deletedGroup = saved(3)
        link(deletedGroup, 3).first.softDelete(now)
        val disabled = saved(4)
        link(disabled, 4)
        val foreignGroup = saved(5)
        link(foreignGroup, 99)
        saved(6)
        for (user in 1L..6L) token(user, "token-$user", enabled = user != 4L)
        entityManager.flush()
        assertEquals(emptyList(), repository.findPostProcessingTargets(101))
        // A still-active second archive makes the existing saved post reachable again.
        link(deletedGroup, 3)
        entityManager.flush()
        val targets = repository.findPostProcessingTargets(101)
        assertEquals(listOf(requireNotNull(deletedGroup.id)), targets.map { it.savedPostId })
    }

    private fun saved(userId: Long, postId: Long = 101): UserSavedPostEntity =
        UserSavedPostEntity(userId, postId).also(entityManager::persist)

    private fun link(saved: UserSavedPostEntity, owner: Long): Pair<GroupEntity, GroupPostEntity> {
        val group = GroupEntity(owner, "archive", GroupColor.BLUE).also(entityManager::persist)
        val link = GroupPostEntity(requireNotNull(group.id), requireNotNull(saved.id)).also(entityManager::persist)
        return group to link
    }

    private fun token(user: Long, value: String, enabled: Boolean = true) {
        entityManager.persist(UserPushTokenEntity(user, value, PushPlatform.IOS, enabled, now))
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = [UserPushTokenJpaRepository::class])
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:post-push-target;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan(
                UserPushTokenEntity::class.java.packageName,
                UserSavedPostEntity::class.java.packageName,
                GroupEntity::class.java.packageName,
            )
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }
}

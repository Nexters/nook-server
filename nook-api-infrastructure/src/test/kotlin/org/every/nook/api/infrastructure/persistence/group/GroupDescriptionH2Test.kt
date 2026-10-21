package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.mockito.Mockito.mock
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupDescriptionH2Test {
    @Test
    fun `persists original updates and deletion and preserves description in legacy updates`() {
        factory().use { factory ->
            val id = insert(factory)
            for (description in listOf("새 소개\n👨‍👩‍👧‍👦", null, "")) {
                factory.openSession().use { session ->
                    val tx = session.beginTransaction()
                    val repository = JpaRepositoryFactory(session).getRepository(GroupJpaRepository::class.java)
                    val adapter = GroupPersistenceAdapter(
                        repository,
                        mock(GroupPostJpaRepository::class.java),
                        mock(UserSavedPostJpaRepository::class.java),
                    )
                    assertIs<GroupPort.UpdateResult.Updated>(adapter.update(7, id, "카페", GroupColor.GREEN, description))
                    tx.commit()
                }
                factory.openSession().use { session ->
                    val expected = if (description == "") null else "새 소개\n👨‍👩‍👧‍👦"
                    assertEquals(expected, session.find(GroupEntity::class.java, id).description)
                }
            }
        }
    }

    @Test
    fun `native group summary returns persisted multiline description`() {
        factory().use { factory ->
            val id = insert(factory)
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                session.createNativeMutationQuery(
                    "CREATE TABLE group_posts (group_id BIGINT, user_saved_post_id BIGINT, " +
                        "deleted_at TIMESTAMP, updated_at TIMESTAMP)",
                ).executeUpdate()
                session.createNativeMutationQuery(
                    "CREATE TABLE user_saved_posts (id BIGINT, deleted_at TIMESTAMP)",
                ).executeUpdate()
                val repository = JpaRepositoryFactory(session).getRepository(GroupJpaRepository::class.java)
                val summary = repository.findAllSummaries(7).single()
                assertEquals(id, summary.id)
                assertEquals("첫 소개\n👨‍👩‍👧‍👦", summary.description)
                tx.commit()
            }
        }
    }

    private fun insert(factory: SessionFactory): Long = factory.openSession().use { session ->
        val tx = session.beginTransaction()
        val group = GroupEntity(7, "카페", GroupColor.YELLOW, "첫 소개\n👨‍👩‍👧‍👦")
        for (field in listOf("createdAt", "updatedAt")) {
            BaseEntity::class.java.getDeclaredField(field).apply { isAccessible = true }.set(group, Instant.EPOCH)
        }
        session.persist(group)
        tx.commit()
        requireNotNull(group.id)
    }

    private fun factory(): SessionFactory = Configuration()
        .addAnnotatedClass(GroupEntity::class.java)
        .setProperty("hibernate.connection.url", "jdbc:h2:mem:group-${UUID.randomUUID()};MODE=MySQL")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .buildSessionFactory()
}

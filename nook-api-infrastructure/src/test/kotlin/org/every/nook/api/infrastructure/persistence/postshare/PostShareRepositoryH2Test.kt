package org.every.nook.api.infrastructure.persistence.postshare

import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PostShareRepositoryH2Test {
    @Test
    fun `deletion generation survives persisted restore and keeps old share expired`() {
        factory().use { factory ->
            val saved = UserSavedPostEntity(7, 101).apply { placeLinksFrozen = true }
            val receiver = UserSavedPostEntity(8, 101).apply { placeLinksFrozen = true }
            val snapshot = """{"places":[]}"""
            val link = PostShareLinkEntity("a".repeat(64), 1, 101, saved.shareGeneration, snapshot, Instant.EPOCH)
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                stamp(saved)
                stamp(receiver)
                stamp(link)
                session.persist(saved)
                session.persist(receiver)
                session.persist(link)
                tx.commit()
            }
            val originalGeneration = saved.shareGeneration
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                session.find(UserSavedPostEntity::class.java, saved.id).softDelete(Instant.now())
                tx.commit()
            }
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                session.createNativeMutationQuery("UPDATE user_saved_posts SET deleted_at=NULL WHERE id=:id")
                    .setParameter("id", saved.id).executeUpdate()
                tx.commit()
            }
            factory.openSession().use { session ->
                val restored = session.find(UserSavedPostEntity::class.java, saved.id)
                val stored = session.find(PostShareLinkEntity::class.java, link.id)
                assertTrue(restored.placeLinksFrozen)
                assertNotEquals(originalGeneration, restored.shareGeneration)
                assertEquals(snapshot, stored.snapshot)
                val retained = session.find(UserSavedPostEntity::class.java, receiver.id)
                assertEquals(8, retained.userId)
                assertTrue(retained.placeLinksFrozen)
                assertFailsWith<org.every.nook.api.application.post.share.PostShareException> {
                    requirePostShareSource(stored, restored)
                }
            }
        }
    }

    @Test
    fun `database rejects duplicate bearer token hashes`() {
        factory().use { factory ->
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                fun link() = PostShareLinkEntity("b".repeat(64), 1, 101, "generation", "{}", Instant.EPOCH)
                    .also(::stamp)
                session.persist(link())
                assertFailsWith<jakarta.persistence.PersistenceException> { session.persist(link()) }
                tx.rollback()
            }
        }
    }

    @Test
    fun `stale memo update cannot restore old sharing generation or unfreeze a copied post`() {
        factory().use { factory ->
            val saved = UserSavedPostEntity(7, 101).also(::stamp)
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                session.persist(saved)
                tx.commit()
            }
            val oldGeneration = saved.shareGeneration
            factory.openSession().use { staleSession ->
                val staleTx = staleSession.beginTransaction()
                val stale = staleSession.find(UserSavedPostEntity::class.java, saved.id)
                factory.openSession().use { session ->
                    val tx = session.beginTransaction()
                    val latest = session.find(UserSavedPostEntity::class.java, saved.id)
                    latest.softDelete(Instant.now())
                    latest.placeLinksFrozen = true
                    session.flush()
                    session.createNativeMutationQuery("UPDATE user_saved_posts SET deleted_at=NULL WHERE id=:id")
                        .setParameter("id", saved.id).executeUpdate()
                    tx.commit()
                }
                stale.memo = "late memo edit"
                staleTx.commit()
            }
            factory.openSession().use { session ->
                val latest = session.find(UserSavedPostEntity::class.java, saved.id)
                assertNotEquals(oldGeneration, latest.shareGeneration)
                assertTrue(latest.placeLinksFrozen)
                assertEquals("late memo edit", latest.memo)
            }
        }
    }

    private fun factory(): SessionFactory = Configuration()
        .addAnnotatedClass(UserSavedPostEntity::class.java)
        .addAnnotatedClass(PostShareLinkEntity::class.java)
        .setProperty("hibernate.connection.url", "jdbc:h2:mem:share-${UUID.randomUUID()};MODE=MySQL")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .buildSessionFactory()

    private fun stamp(entity: BaseEntity) {
        for (field in listOf("createdAt", "updatedAt")) {
            BaseEntity::class.java.getDeclaredField(field).apply { isAccessible = true }.set(entity, Instant.EPOCH)
        }
    }
}

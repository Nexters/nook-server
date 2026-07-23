package org.every.nook.api.infrastructure.persistence

import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistenceEntityMetadataTest {
    @Test
    fun `all persistence entities inherit base entity`() {
        persistenceEntities.forEach { entity ->
            assertTrue(BaseEntity::class.java.isAssignableFrom(entity.java), entity.simpleName)
        }
    }

    @Test
    fun `post identity is unique by source and external post id`() {
        val table = requireNotNull(PostEntity::class.findAnnotation<Table>())
        val uniqueConstraint = table.uniqueConstraints.single()

        assertEquals("posts", table.name)
        assertEquals("idx_u_source_type_external_post_id", uniqueConstraint.name)
        assertEquals(listOf("source_type", "external_post_id"), uniqueConstraint.columnNames.toList())
    }

    @Test
    fun `place identity is unique by provider and external place id`() {
        val table = requireNotNull(PlaceEntity::class.findAnnotation<Table>())
        val uniqueConstraint = table.uniqueConstraints.single()

        assertEquals("places", table.name)
        assertEquals("idx_u_provider_external_place_id", uniqueConstraint.name)
        assertEquals(listOf("provider", "external_place_id"), uniqueConstraint.columnNames.toList())
    }

    @Test
    fun `user can save the same post more than once`() {
        val table = requireNotNull(UserSavedPostEntity::class.findAnnotation<Table>())

        assertTrue(table.uniqueConstraints.isEmpty())
        assertEquals(setOf("idx_user_id", "idx_post_id"), table.indexes.map { it.name }.toSet())
    }

    @Test
    fun `group name is unique within a user`() {
        val table = requireNotNull(GroupEntity::class.findAnnotation<Table>())
        val uniqueConstraint = table.uniqueConstraints.single()

        assertEquals("idx_u_user_id_name", uniqueConstraint.name)
        assertEquals(listOf("user_id", "name"), uniqueConstraint.columnNames.toList())
    }

    private companion object {
        val persistenceEntities: List<KClass<*>> = listOf(
            PostEntity::class,
            PostMediaEntity::class,
            PlaceEntity::class,
            PostPlaceEntity::class,
            UserSavedPostEntity::class,
            GroupEntity::class,
            GroupPostEntity::class,
        )
    }
}

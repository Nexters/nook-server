package org.every.nook.api.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.every.nook.api.domain.group.Group
import org.every.nook.api.infrastructure.persistence.auth.RefreshTokenEntity
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseEntity
import org.every.nook.api.infrastructure.persistence.config.RuntimeConfigurationEntity
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.member.SocialAccountEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkEntity
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
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
    fun `post source identity is unique`() {
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
    fun `user saved post is unique per source post`() {
        val table = requireNotNull(UserSavedPostEntity::class.findAnnotation<Table>())
        val uniqueConstraint = table.uniqueConstraints.single()

        assertEquals("idx_u_user_id_post_id", uniqueConstraint.name)
        assertEquals(listOf("user_id", "post_id"), uniqueConstraint.columnNames.toList())
        assertEquals(setOf("idx_user_id", "idx_post_id"), table.indexes.map { it.name }.toSet())
    }

    @Test
    fun `user saved post place is unique by place and display order`() {
        val table = requireNotNull(UserSavedPostPlaceEntity::class.findAnnotation<Table>())

        assertEquals("user_saved_post_places", table.name)
        assertEquals(setOf("idx_place_id"), table.indexes.map { it.name }.toSet())
        assertEquals(
            setOf(
                "idx_u_user_saved_post_id_place_id" to listOf("user_saved_post_id", "place_id"),
                "idx_u_user_saved_post_id_display_order" to listOf("user_saved_post_id", "display_order"),
            ),
            table.uniqueConstraints.map { it.name to it.columnNames.toList() }.toSet(),
        )
    }

    @Test
    fun `user place bookmark is unique across posts`() {
        val table = requireNotNull(UserPlaceBookmarkEntity::class.findAnnotation<Table>())

        assertEquals("user_place_bookmarks", table.name)
        assertEquals(
            setOf("idx_place_id", "idx_user_id_created_at_id"),
            table.indexes.map { it.name }.toSet(),
        )
        val uniqueConstraint = table.uniqueConstraints.single()
        assertEquals("idx_u_user_id_place_id", uniqueConstraint.name)
        assertEquals(listOf("user_id", "place_id"), uniqueConstraint.columnNames.toList())
    }

    @Test
    fun `group name allows duplicates within a user`() {
        val table = requireNotNull(GroupEntity::class.findAnnotation<Table>())
        val nameColumn =
            requireNotNull(
                GroupEntity::class.java
                    .getDeclaredField("name")
                    .getAnnotation(Column::class.java),
            )
        val colorField = GroupEntity::class.java.getDeclaredField("color")
        val colorColumn = requireNotNull(colorField.getAnnotation(Column::class.java))
        val colorEnum = requireNotNull(colorField.getAnnotation(Enumerated::class.java))

        assertTrue(table.uniqueConstraints.isEmpty())
        assertEquals(Group.MAX_NAME_LENGTH, nameColumn.length)
        assertEquals(GroupEntity.COLOR_COLUMN_LENGTH, colorColumn.length)
        assertEquals(EnumType.STRING, colorEnum.value)
    }

    @Test
    fun `place parsing job is unique per post`() {
        val table = requireNotNull(PlaceParsingJobEntity::class.findAnnotation<Table>())
        val uniqueConstraint = table.uniqueConstraints.single()

        assertEquals("place_parsing_jobs", table.name)
        assertEquals("idx_u_post_id", uniqueConstraint.name)
        assertEquals(listOf("post_id"), uniqueConstraint.columnNames.toList())
    }

    @Test
    fun `jpa relationships never request physical foreign keys`() {
        val joinColumns = relationshipEntities.flatMap { entity ->
            entity.java.declaredFields.mapNotNull { field -> field.getAnnotation(JoinColumn::class.java) }
        }

        assertTrue(joinColumns.isNotEmpty())
        joinColumns.forEach { joinColumn ->
            assertEquals(ConstraintMode.NO_CONSTRAINT, joinColumn.foreignKey.value)
        }
    }

    private companion object {
        val relationshipEntities: List<KClass<*>> = listOf(
            SocialAccountEntity::class,
            RefreshTokenEntity::class,
        )

        val persistenceEntities: List<KClass<*>> = listOf(
            PostEntity::class,
            PostMediaEntity::class,
            PostHashtagEntity::class,
            PlaceEntity::class,
            PostPlaceEntity::class,
            PlaceParsingJobEntity::class,
            UserPlaceBookmarkEntity::class,
            UserSavedPostEntity::class,
            UserSavedPostPlaceEntity::class,
            GroupEntity::class,
            GroupPostEntity::class,
            ScrapingProviderResponseEntity::class,
            RuntimeConfigurationEntity::class,
        )
    }
}

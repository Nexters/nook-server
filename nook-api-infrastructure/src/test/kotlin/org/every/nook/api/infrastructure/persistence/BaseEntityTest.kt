package org.every.nook.api.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BaseEntityTest {
    @Test
    fun `base entity has jpa auditing metadata`() {
        val entityListeners = BaseEntity::class.findAnnotation<EntityListeners>()

        assertNotNull(BaseEntity::class.findAnnotation<MappedSuperclass>())
        assertNotNull(entityListeners)
        assertEquals(AuditingEntityListener::class, entityListeners.value.single())
    }

    @Test
    fun `created at column is configured`() {
        val createdAt = BaseEntity::class.java.getDeclaredField("createdAt")
        val column = createdAt.getAnnotation(Column::class.java)

        assertNotNull(createdAt.getAnnotation(CreatedDate::class.java))
        assertNotNull(column)
        assertEquals("created_at", column.name)
        assertEquals(false, column.nullable)
        assertEquals(false, column.updatable)
        assertEquals("TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)", column.columnDefinition)
    }

    @Test
    fun `updated at column is configured`() {
        val updatedAt = BaseEntity::class.java.getDeclaredField("updatedAt")
        val column = updatedAt.getAnnotation(Column::class.java)

        assertNotNull(updatedAt.getAnnotation(LastModifiedDate::class.java))
        assertNotNull(column)
        assertEquals("updated_at", column.name)
        assertEquals(false, column.nullable)
        assertEquals(
            "TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)",
            column.columnDefinition,
        )
    }
}

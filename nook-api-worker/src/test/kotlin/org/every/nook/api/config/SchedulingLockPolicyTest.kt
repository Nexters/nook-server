package org.every.nook.api.config

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.place.PlaceParsingEventListener
import org.every.nook.api.post.PostContentParsingEventListener
import org.springframework.scheduling.annotation.Scheduled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulingLockPolicyTest {
    @Test
    fun `every scheduled parsing dispatcher has a unique lock`() {
        val scheduledMethods = listOf(
            PlaceParsingEventListener::class.java,
            PostContentParsingEventListener::class.java,
        ).flatMap { type -> type.declaredMethods.filter { it.isAnnotationPresent(Scheduled::class.java) } }

        val locks = scheduledMethods.map { method ->
            requireNotNull(method.getAnnotation(SchedulerLock::class.java)) {
                "${method.declaringClass.simpleName}.${method.name} must have @SchedulerLock"
            }
        }

        assertEquals(2, locks.size)
        assertEquals(locks.size, locks.map { it.name }.toSet().size)
        assertTrue(locks.all { it.lockAtMostFor == "30s" })
        assertTrue(locks.all { it.lockAtLeastFor == "9s" })
    }

    @Test
    fun `startup recovery methods have separate locks`() {
        val locks = listOf(
            PlaceParsingEventListener::class.java,
            PostContentParsingEventListener::class.java,
        ).map { type ->
            requireNotNull(
                type.getDeclaredMethod("recoverOutstandingJobs").getAnnotation(SchedulerLock::class.java),
            )
        }

        assertEquals(
            setOf(
                "placeParsing.recoverOutstandingJobs",
                "postContentParsing.recoverOutstandingJobs",
            ),
            locks.map { it.name }.toSet(),
        )
        assertTrue(locks.all { it.lockAtMostFor == "1m" })
        assertTrue(locks.all { it.lockAtLeastFor == "10s" })
    }
}

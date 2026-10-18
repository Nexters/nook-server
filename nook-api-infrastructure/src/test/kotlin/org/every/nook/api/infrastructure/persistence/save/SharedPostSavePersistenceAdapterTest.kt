package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.infrastructure.persistence.postshare.PostShareTestFixture
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPostSavePersistenceAdapterTest {
    @Test
    fun `legacy archive save keeps initial worker population for an unfinished source`() {
        val f = PostShareTestFixture()
        val recipient = f.savedPost(8, 22)
        `when`(f.saved.save(any(UserSavedPostEntity::class.java))).thenReturn(recipient)
        `when`(f.details.findDetail(7, 11)).thenReturn(
            f.detail.copy(processingStatus = PostProcessingStatusView.PENDING),
        )
        SharedPostSavePersistenceAdapter(f.saved, f.places, f.groupPosts, f.bookmarks, f.details)
            .save(8, 11, setOf(9))
        kotlin.test.assertFalse(recipient.placeLinksFrozen)
    }

    @Test
    fun `archive shared save copies owner edited associations instead of common original places`() {
        val f = PostShareTestFixture()
        val recipient = f.savedPost(8, 22)
        `when`(f.saved.save(any(UserSavedPostEntity::class.java))).thenReturn(recipient)
        `when`(f.places.findAllByUserSavedPostIdOrderBySequenceAsc(11))
            .thenReturn(listOf(UserSavedPostPlaceEntity(11, 99, 5)))
        SharedPostSavePersistenceAdapter(f.saved, f.places, f.groupPosts, f.bookmarks, f.details).save(8, 11, setOf(9))
        val copied = mockingDetails(f.places).invocations.single { it.method.name == "saveAll" }
            .getArgument<List<UserSavedPostPlaceEntity>>(0)
        assertEquals(listOf(99L), copied.map { it.placeId })
        assertEquals(listOf(5), copied.map { it.sequence })
        assertTrue(recipient.placeLinksFrozen)
        verify(f.places, never()).insertAllFromPost(22, 101)
    }
}

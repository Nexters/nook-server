package org.every.nook.api.domain.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceTagTest {
    @Test
    fun `catalog contains one hundred selectable tags split evenly across categories`() {
        assertEquals(100, PlaceTag.selectableEntries.size)
        assertEquals(100, PlaceTag.defaultDefinitions.size)
        assertEquals(100, PlaceTag.selectableEntries.map(PlaceTag::displayName).distinct().size)
        assertEquals(
            PlaceTagCategory.entries.associateWith { 20 },
            PlaceTag.selectableEntries.groupingBy(PlaceTag::category).eachCount(),
        )
        assertEquals((1..100).toList(), PlaceTag.defaultDefinitions.map(PlaceTagDefinition::sortOrder))
        assertTrue(PlaceTag.defaultDefinitions.all(PlaceTagDefinition::enabled))
    }

    @Test
    fun `selectable tags have matching keywords and fit the persistence column`() {
        PlaceTag.selectableEntries.forEach { tag ->
            assertTrue(tag.matchingKeywords.isNotEmpty(), "${tag.name} has no matching keyword")
            assertTrue(tag.name.length <= 30, "${tag.name} exceeds the tag column length")
        }
    }
}

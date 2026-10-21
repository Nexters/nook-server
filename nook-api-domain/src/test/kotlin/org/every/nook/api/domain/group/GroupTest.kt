package org.every.nook.api.domain.group

import kotlin.test.Test
import kotlin.test.assertFailsWith

class GroupTest {
    @Test
    fun `counts combined emoji flags skin tones and combining marks as visible characters`() {
        for (grapheme in listOf("가", "A", "!", "👨‍👩‍👧‍👦", "🇰🇷", "👍🏽", "e\u0301", "가")) {
            Group(1, "카페", GroupColor.YELLOW, description = grapheme.repeat(Group.MAX_DESCRIPTION_LENGTH))
            assertFailsWith<IllegalArgumentException> {
                Group(1, "카페", GroupColor.YELLOW, description = grapheme.repeat(Group.MAX_DESCRIPTION_LENGTH + 1))
            }
        }
    }

    @Test
    fun `counts internal newlines and trims surrounding whitespace before validation`() {
        val limit = Group.MAX_DESCRIPTION_LENGTH
        Group(1, "카페", GroupColor.YELLOW, description = "  " + "가".repeat(limit) + "\n  ")
        Group(1, "카페", GroupColor.YELLOW, description = "가".repeat(limit - 2) + "\n나")
        assertFailsWith<IllegalArgumentException> {
            Group(1, "카페", GroupColor.YELLOW, description = "가".repeat(limit - 1) + "\n나")
        }
    }

    @Test
    fun `accepts a group name up to twenty characters`() {
        Group(userId = 1, name = "가".repeat(Group.MAX_NAME_LENGTH), color = GroupColor.YELLOW)
    }

    @Test
    fun `rejects a group name over twenty characters`() {
        assertFailsWith<IllegalArgumentException> {
            Group(userId = 1, name = "가".repeat(Group.MAX_NAME_LENGTH + 1), color = GroupColor.YELLOW)
        }
    }
}

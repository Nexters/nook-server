package org.every.nook.api.domain.group

import kotlin.test.Test
import kotlin.test.assertFailsWith

class GroupTest {
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

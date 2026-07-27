package org.every.nook.api.domain.member

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MemberTest {
    @Test
    fun `nickname is normalized before member creation`() {
        val nickname = Member.normalizeNickname("  누커  ")

        val member = Member(nickname = nickname, profileImageUrl = null)

        assertEquals("누커", member.nickname)
        assertNull(member.profileImageUrl)
    }

    @Test
    fun `non HTTPS profile image URL is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Member(nickname = "누커", profileImageUrl = "http://example.com/profile.png")
        }
    }

    @Test
    fun `nickname longer than twenty characters is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Member(nickname = "가".repeat(21), profileImageUrl = null)
        }
    }
}

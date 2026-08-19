package org.every.nook.api.config

import org.springframework.security.authentication.TestingAuthenticationToken
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAccessGuardTest {
    @Test
    fun `allows only configured authenticated user ids`() {
        val guard = AdminAccessGuard(AdminSecurityProperties(setOf(17L)))

        assertTrue(guard.isAdmin(TestingAuthenticationToken("17", "credentials", "ROLE_USER")))
        assertFalse(guard.isAdmin(TestingAuthenticationToken("18", "credentials", "ROLE_USER")))
        assertFalse(guard.isAdmin(null))
    }
}

package org.every.nook.api

import kotlin.test.Test
import kotlin.test.assertNotNull

class NookApiBatchApplicationTest {
    @Test
    fun `batch entry point exists`() {
        assertNotNull(NookApiBatchApplication())
    }
}

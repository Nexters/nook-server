package org.every.nook.api.infrastructure.config

import kotlin.test.Test
import kotlin.test.assertNotNull

class AwsRuntimeDependencyTest {
    @Test
    fun `STS client is available for AssumeRole credential profiles`() {
        assertNotNull(Class.forName("software.amazon.awssdk.services.sts.StsClient"))
    }
}

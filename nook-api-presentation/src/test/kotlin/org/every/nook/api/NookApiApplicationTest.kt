package org.every.nook.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NookApiApplicationTest {
    @Test
    fun `application entry point exists`() {
        assertNotNull(NookApiApplication())
    }

    @Test
    fun `loads properties from dotenv file`() {
        val directory = Files.createTempDirectory("nook-api-dotenv")
        directory.resolve(".env").toFile().writeText("NOOK_DOTENV_TEST=api-value")

        assertEquals("api-value", loadDotenvProperties(directory.toString())["NOOK_DOTENV_TEST"])
    }

    @Test
    fun `does not fail when dotenv file is missing`() {
        val directory = Files.createTempDirectory("nook-api-dotenv-missing")

        assertEquals(null, loadDotenvProperties(directory.toString())["NOOK_MISSING_DOTENV_TEST"])
    }
}

package org.every.nook.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NookApiBatchApplicationTest {
    @Test
    fun `batch entry point exists`() {
        assertNotNull(NookApiBatchApplication())
    }

    @Test
    fun `loads properties from dotenv file`() {
        val directory = Files.createTempDirectory("nook-batch-dotenv")
        directory.resolve(".env").toFile().writeText("NOOK_DOTENV_TEST=batch-value")

        assertEquals("batch-value", loadDotenvProperties(directory.toString())["NOOK_DOTENV_TEST"])
    }

    @Test
    fun `does not fail when dotenv file is missing`() {
        val directory = Files.createTempDirectory("nook-batch-dotenv-missing")

        assertEquals(null, loadDotenvProperties(directory.toString())["NOOK_MISSING_DOTENV_TEST"])
    }

    @Test
    fun `finds dotenv file from a parent project directory`() {
        val projectDirectory = Files.createTempDirectory("nook-batch-project")
        val moduleDirectory = Files.createDirectory(projectDirectory.resolve("nook-api-batch"))
        projectDirectory.resolve(".env").toFile().writeText("NOOK_DOTENV_TEST=batch-value")

        assertEquals(projectDirectory.toString(), findDotenvDirectory(moduleDirectory))
    }
}

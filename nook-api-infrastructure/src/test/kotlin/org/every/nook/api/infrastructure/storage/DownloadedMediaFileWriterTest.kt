package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DownloadedMediaFileWriterTest {
    private val writer = DownloadedMediaFileWriter()

    @Test
    fun `writes content with digest and removes temporary file when closed`() {
        val downloaded = writer.persist(
            input = ByteArrayInputStream("abc".toByteArray()),
            contentType = "image/jpeg",
            extension = "jpg",
            maxBytes = 3,
        )
        val path = downloaded.path

        assertEquals(3, downloaded.size)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            downloaded.sha256,
        )
        downloaded.close()
        assertFalse(Files.exists(path))
    }

    @Test
    fun `rejects body larger than configured limit`() {
        assertFailsWith<PostMediaStorageException> {
            writer.persist(
                input = ByteArrayInputStream("oversized".toByteArray()),
                contentType = "image/jpeg",
                extension = "jpg",
                maxBytes = 3,
            )
        }
    }
}

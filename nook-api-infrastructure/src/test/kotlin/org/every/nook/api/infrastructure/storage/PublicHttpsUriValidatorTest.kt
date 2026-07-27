package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicHttpsUriValidatorTest {
    private val validator = PublicHttpsUriValidator()

    @Test
    fun `accepts public HTTPS address`() {
        val result = validator.validate("https://1.1.1.1/media.jpg")

        assertEquals("1.1.1.1", result.host)
    }

    @Test
    fun `rejects non HTTPS address`() {
        assertFailsWith<PostMediaStorageException> {
            validator.validate("http://1.1.1.1/media.jpg")
        }
    }

    @Test
    fun `rejects private and loopback addresses`() {
        listOf(
            "https://127.0.0.1/media.jpg",
            "https://10.0.0.1/media.jpg",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/media.jpg",
            "https://[fc00::1]/media.jpg",
        ).forEach { url ->
            assertFailsWith<PostMediaStorageException>(url) {
                validator.validate(url)
            }
        }
    }

    @Test
    fun `rejects user information`() {
        assertFailsWith<PostMediaStorageException> {
            validator.validate("https://user:password@1.1.1.1/media.jpg")
        }
    }
}

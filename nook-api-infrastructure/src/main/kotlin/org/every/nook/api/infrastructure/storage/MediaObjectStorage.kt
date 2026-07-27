package org.every.nook.api.infrastructure.storage

import java.nio.file.Path

interface MediaObjectStorage {
    fun exists(key: String): Boolean

    fun put(key: String, path: Path, contentType: String, contentLength: Long)
}

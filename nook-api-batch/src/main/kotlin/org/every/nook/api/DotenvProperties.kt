package org.every.nook.api

import io.github.cdimascio.dotenv.dotenv
import java.nio.file.Files
import java.nio.file.Path

internal fun loadDotenvProperties(directory: String? = null): Map<String, Any> = dotenv {
    this.directory = directory ?: findDotenvDirectory(Path.of("").toAbsolutePath())
    ignoreIfMissing = true
}.entries().associate { entry -> entry.key to entry.value }

internal fun findDotenvDirectory(start: Path): String = generateSequence(start.toAbsolutePath()) { it.parent }
    .firstOrNull { Files.isRegularFile(it.resolve(".env")) }
    ?.toString()
    ?: start.toString()

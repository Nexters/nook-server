package org.every.nook.api

import io.github.cdimascio.dotenv.dotenv

internal fun loadDotenvProperties(directory: String = "."): Map<String, Any> = dotenv {
    this.directory = directory
    ignoreIfMissing = true
}.entries().associate { entry -> entry.key to entry.value }

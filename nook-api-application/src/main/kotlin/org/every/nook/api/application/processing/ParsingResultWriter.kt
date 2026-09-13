package org.every.nook.api.application.processing

/** Runs only database result writes while the claimed execution still owns its job. */
fun interface ParsingResultWriter {
    fun write(change: () -> Unit)

    companion object {
        val DIRECT = ParsingResultWriter { change -> change() }
    }
}

class StaleParsingExecutionException : IllegalStateException("Parsing execution no longer owns the job")

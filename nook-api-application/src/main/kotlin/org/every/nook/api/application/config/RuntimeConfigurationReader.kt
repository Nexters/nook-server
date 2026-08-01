package org.every.nook.api.application.config

fun interface RuntimeConfigurationReader {
    fun findValue(key: String): String?
}

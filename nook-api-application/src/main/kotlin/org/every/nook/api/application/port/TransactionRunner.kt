package org.every.nook.api.application.port

interface TransactionRunner {
    fun <T> required(block: () -> T): T
}

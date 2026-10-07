package org.every.nook.api.infrastructure.storage

import java.io.IOException
import java.io.InputStream
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Closing the JDK response stream cancels the subscription and unblocks a pending body read. */
internal class DeadlineInputStream(private val source: InputStream, remaining: Duration) : InputStream() {
    private val state = AtomicReference(State.OPEN)
    private val expiration = scheduler.schedule(
        {
            if (state.compareAndSet(State.OPEN, State.TIMED_OUT)) {
                runCatching { source.close() }
            }
        },
        remaining.toNanos().coerceAtLeast(0),
        TimeUnit.NANOSECONDS,
    )

    override fun read(): Int = readWithinDeadline { source.read() }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        readWithinDeadline { source.read(buffer, offset, length) }

    override fun close() {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {
            expiration.cancel(false)
            source.close()
        }
    }

    private fun readWithinDeadline(read: () -> Int): Int {
        checkDeadline()
        val count = try {
            read()
        } catch (exception: IOException) {
            checkDeadline()
            throw exception
        }
        checkDeadline()
        if (count < 0) close()
        return count
    }

    private fun checkDeadline() {
        if (state.get() == State.TIMED_OUT) {
            throw HttpTimeoutException("Media response body deadline exceeded")
        }
    }

    private enum class State { OPEN, TIMED_OUT, CLOSED }

    private companion object {
        val scheduler = ScheduledThreadPoolExecutor(
            1,
            Thread.ofPlatform().daemon().name("media-download-deadline-", 0).factory(),
        ).apply { removeOnCancelPolicy = true }
    }
}

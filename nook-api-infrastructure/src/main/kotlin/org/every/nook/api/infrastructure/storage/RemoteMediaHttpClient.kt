package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import org.every.nook.api.application.post.error.PostMediaStorageTimeoutException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

fun interface RemoteMediaHttpClient {
    fun get(uri: URI): MediaHttpResponse
}

data class MediaHttpResponse(val statusCode: Int, val headers: Map<String, List<String>>, val body: InputStream) {
    fun firstHeader(name: String): String? = headers.entries
        .firstOrNull { (headerName) -> headerName.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}

class JdkRemoteMediaHttpClient(private val httpClient: HttpClient, private val properties: MediaStorageProperties) :
    RemoteMediaHttpClient {
    override fun get(uri: URI): MediaHttpResponse {
        val request = HttpRequest.newBuilder(uri)
            .timeout(properties.readTimeout)
            .header(USER_AGENT_HEADER, USER_AGENT)
            .header(ACCEPT_HEADER, ACCEPT)
            .GET()
            .build()
        val response = send(request)
        return MediaHttpResponse(response.statusCode(), response.headers().map(), response.body())
    }

    private fun send(request: HttpRequest): HttpResponse<InputStream> = try {
        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    } catch (exception: HttpTimeoutException) {
        storageTimeout(exception)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        storageFailure(exception)
    } catch (exception: IOException) {
        storageFailure(exception)
    }

    private fun storageTimeout(cause: Throwable): Nothing = throw PostMediaStorageTimeoutException(cause)

    private fun storageFailure(cause: Throwable): Nothing = throw PostMediaStorageException(cause)

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = "nook-media-fetcher/1.0"
        const val ACCEPT_HEADER = "Accept"
        const val ACCEPT = "image/*,video/*"
    }
}

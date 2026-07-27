package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

class PublicHttpsUriValidator {
    fun validate(value: String): URI {
        val uri = runCatching { URI(value) }
            .getOrElse(::invalid)
        if (uri.scheme != HTTPS_SCHEME || uri.host.isNullOrBlank() || uri.userInfo != null) {
            invalid()
        }

        val addresses = runCatching { InetAddress.getAllByName(uri.host) }
            .getOrElse(::invalid)
        if (addresses.isEmpty() || addresses.any(::isNonPublic)) {
            invalid()
        }
        return uri
    }

    private fun invalid(cause: Throwable? = null): Nothing = throw PostMediaStorageException(cause)

    private fun isNonPublic(address: InetAddress): Boolean = address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress ||
        address.isUniqueLocalIpv6()

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        if (this !is Inet6Address) {
            return false
        }
        return address.first().toInt() and UNIQUE_LOCAL_MASK == UNIQUE_LOCAL_PREFIX
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val UNIQUE_LOCAL_MASK = 0xFE
        const val UNIQUE_LOCAL_PREFIX = 0xFC
    }
}

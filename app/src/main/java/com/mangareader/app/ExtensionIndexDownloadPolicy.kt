package com.mangareader.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Bounds untrusted extension catalogue responses before constructing a JSON string.
 * Most catalogues are a few MiB, but a malicious or broken endpoint can stream
 * indefinitely without declaring Content-Length.
 */
internal object ExtensionIndexDownloadPolicy {
    const val MAX_INDEX_BYTES = 32 * 1024 * 1024

    fun readUtf8(
        input: InputStream,
        contentLength: Long = -1L,
        maxBytes: Int = MAX_INDEX_BYTES,
    ): String {
        require(maxBytes > 0)
        if (contentLength == 0L || contentLength < -1L || contentLength > maxBytes.toLong()) {
            throw IOException("Extension catalogue has an invalid or excessive Content-Length")
        }

        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            if (count > maxBytes - total) {
                throw IOException("Extension catalogue exceeds the download size limit")
            }
            bytes.write(buffer, 0, count)
            total += count
        }
        if (total == 0) throw IOException("Extension catalogue is empty")
        return bytes.toString(Charsets.UTF_8.name())
    }
}

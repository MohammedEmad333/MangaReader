package com.mangareader.app

import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Scale
import coil.size.Size
import coil.size.pxOrElse
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detect the AVIF image/sequence brand in the ISO BMFF file-type box.
 * The AVIF brand may be compatible rather than primary (for example mif1 + avif).
 * Tachiyomi caches chapter pages as 0001, without a usable filename extension.
 */
internal fun isAvifHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 16 || !bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())) {
        return false
    }
    val declaredSize = (bytes[0].toLong() and 0xffL shl 24) or
        ((bytes[1].toLong() and 0xffL) shl 16) or
        ((bytes[2].toLong() and 0xffL) shl 8) or
        (bytes[3].toLong() and 0xffL)

    // The 64-bit extended-size form (size == 1) is not read from this bounded prefix.
    if (declaredSize == 1L || (declaredSize != 0L && declaredSize < 16L)) return false
    val limit = if (declaredSize == 0L) bytes.size
        else minOf(bytes.size.toLong(), declaredSize).toInt()
    fun isAvifBrand(offset: Int): Boolean {
        if (offset + 4 > limit) return false
        val brand = String(bytes, offset, 4, Charsets.US_ASCII)
        return brand == "avif" || brand == "avis"
    }
    if (isAvifBrand(8)) return true
    for (offset in 16 until limit - 3 step 4) {
        if (isAvifBrand(offset)) return true
    }
    return false
}

/** Native AVIF fallback for devices whose Android image decoder rejects source galleries. */
internal class AvifPageDecoder(
    private val result: SourceResult,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult? = withContext(Dispatchers.IO) {
        val data = result.source.source().readByteArray()
        val coder = HeifCoder()
        val bitmap = if (options.size == Size.ORIGINAL) {
            coder.decode(data)
        } else {
            val width = options.size.width.pxOrElse { 0 }
            val height = options.size.height.pxOrElse { 0 }
            if (width > 0 && height > 0) {
                coder.decodeSampled(
                    data,
                    width,
                    height,
                    scaleMode = if (options.scale == Scale.FILL) ScaleMode.FILL else ScaleMode.FIT,
                )
            } else {
                coder.decode(data)
            }
        }
        DecodeResult(BitmapDrawable(options.context.resources, bitmap), isSampled = options.size != Size.ORIGINAL)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            // A bounded peek must tolerate short or corrupt files without throwing EOFException.
            // Inspect compatible brands inside ftyp, never the extensionless cache filename.
            val header = ByteArray(128)
            val read = result.source.source().peek().read(header, 0, header.size)
            return if (read > 0 && isAvifHeader(header.copyOf(read))) {
                AvifPageDecoder(result, options)
            } else null
        }
    }
}

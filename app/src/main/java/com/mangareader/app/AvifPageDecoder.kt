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

/** Detect the file's AVIF brand, not its extension: Tachiyomi saves pages as 0001. */
internal fun isAvifHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    val brand = String(bytes, 4, 8, Charsets.US_ASCII)
    return brand == "ftypavif" || brand == "ftypavis"
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
            val header = result.source.source().peek().readByteArray(12)
            return if (isAvifHeader(header)) AvifPageDecoder(result, options) else null
        }
    }
}

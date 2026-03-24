package com.ambientmemory.timeline.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File

/**
 * Simple difference hash for near-duplicate detection.
 */
object ImageHasher {
    fun dHashFromFile(file: File, maxDimension: Int = 256): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize =
            calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false
        val bmp = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return try {
            dHash(bmp)
        } finally {
            bmp.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, max: Int): Int {
        var inSampleSize = 1
        if (height > max || width > max) {
            var halfH = height / 2
            var halfW = width / 2
            while (halfH / inSampleSize >= max && halfW / inSampleSize >= max) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun dHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val gray = Array(8) { IntArray(9) }
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val c = scaled.getPixel(x, y)
                gray[y][x] = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        val bits = BooleanArray(64)
        var i = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                bits[i++] = gray[y][x] < gray[y][x + 1]
            }
        }
        val bytes = ByteArray(8)
        for (b in 0 until 8) {
            var v = 0
            for (bit in 0 until 8) {
                if (bits[b * 8 + bit]) v = v or (1 shl (7 - bit))
            }
            bytes[b] = v.toByte()
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hammingDistanceHex(a: String, b: String): Int {
        if (a.length != b.length || a.length != 16) return 64
        var dist = 0
        for (i in a.indices step 2) {
            val ba = a.substring(i, i + 2).toInt(16)
            val bb = b.substring(i, i + 2).toInt(16)
            dist += Integer.bitCount(ba xor bb)
        }
        return dist
    }
}

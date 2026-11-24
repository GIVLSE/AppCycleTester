package com.sequmio.givlse.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/** Only the explicitly published application's launcher icon is shared. */
object AppIconCodec {
    const val MAX_ENCODED = 65536
    private const val SIZE = 96

    fun capture(context: Context, packageName: String): String? = runCatching {
        val bitmap = context.packageManager.getApplicationIcon(packageName)
            .toBitmap(width = SIZE, height = SIZE)
        val bytes = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) return null
        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            .takeIf { it.length <= MAX_ENCODED }
    }.getOrNull()

    fun decode(value: String): Bitmap? = runCatching {
        if (value.isEmpty() || value.length > MAX_ENCODED) return null
        val bytes = Base64.decode(value, Base64.DEFAULT)
        // Reject malformed and oversized images before allocating pixel buffers.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType != "image/png" ||
            bounds.outWidth !in 1..SIZE || bounds.outHeight !in 1..SIZE) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

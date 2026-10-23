package com.newspush.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 解码用户选择的大图时先采样，避免相册里的高分辨率照片把内存撑爆。
 */
fun decodeSampledBitmap(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
    if (path.isBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxWidth &&
        bounds.outHeight / (sampleSize * 2) >= maxHeight
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}

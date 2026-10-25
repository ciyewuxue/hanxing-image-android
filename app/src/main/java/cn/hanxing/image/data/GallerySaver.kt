package cn.hanxing.image.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

/** 只往系统相册写新图，从不修改或删除相册里已有的内容。 */
object GallerySaver {

    private const val ALBUM = "寒星生图"

    fun save(context: Context, bytes: ByteArray, displayName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            // 按真实字节写 MIME：JPEG/PNG 都可能，写错相册里会显示异常
            put(MediaStore.Images.Media.MIME_TYPE, detectImageMime(bytes))
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}

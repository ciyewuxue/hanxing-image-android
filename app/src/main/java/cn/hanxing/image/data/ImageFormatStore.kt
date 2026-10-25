package cn.hanxing.image.data

import android.content.Context

/** 出图格式偏好：不涉及密钥，明文存。 */
class ImageFormatStore(context: Context) {

    private val prefs = context.getSharedPreferences("hanxing_format", Context.MODE_PRIVATE)

    fun load(): ImageFormat = ImageFormat.fromId(prefs.getString(KEY_FORMAT, null))

    fun save(format: ImageFormat) {
        prefs.edit().putString(KEY_FORMAT, format.id).apply()
    }

    private companion object {
        const val KEY_FORMAT = "image_format"
    }
}

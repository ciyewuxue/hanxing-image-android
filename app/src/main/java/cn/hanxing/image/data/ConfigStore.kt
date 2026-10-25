package cn.hanxing.image.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 配置存储：整份配置列表用 Android Keystore 里的 AES-256-GCM 密钥加密后写进 SharedPreferences。
 * Key 不可导出，应用沙箱之外拿不到。
 *
 * 旧版（只有一条配置的 `config_blob`）会在首次读取时迁移成一条名为「默认」的配置，
 * 迁移成功后才删掉旧数据。
 */
class ConfigStore(context: Context) {

    data class Snapshot(
        val profiles: List<Profile> = emptyList(),
        val activeId: String? = null,
    )

    private val prefs = context.getSharedPreferences("hanxing_config", Context.MODE_PRIVATE)

    fun load(): Snapshot {
        readJson(KEY_PROFILES)?.let { json ->
            val array = json.optJSONArray("profiles") ?: JSONArray()
            val profiles = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { runCatching { Profile.fromJson(it) }.getOrNull() }
            }
            if (profiles.isNotEmpty()) {
                val stored = json.optString("activeId")
                val active = profiles.firstOrNull { it.id == stored }?.id ?: profiles.first().id
                return Snapshot(profiles, active)
            }
        }

        val legacy = readJson(KEY_LEGACY) ?: return Snapshot()
        val migrated = Profile(
            id = UUID.randomUUID().toString(),
            name = "默认",
            baseUrl = legacy.optString("baseUrl", ""),
            apiKey = legacy.optString("apiKey", ""),
            model = legacy.optString("model", ""),
        )
        val snapshot = Snapshot(listOf(migrated), migrated.id)
        if (save(snapshot)) {
            prefs.edit().remove(KEY_LEGACY).apply()
        }
        return snapshot
    }

    fun save(snapshot: Snapshot): Boolean {
        val array = JSONArray()
        snapshot.profiles.forEach { array.put(it.toJson()) }
        val json = JSONObject()
            .put("profiles", array)
            .put("activeId", snapshot.activeId.orEmpty())
        val blob = encrypt(json.toString()) ?: return false
        return prefs.edit().putString(KEY_PROFILES, blob).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_PROFILES).remove(KEY_LEGACY).apply()
    }

    private fun readJson(key: String): JSONObject? {
        val blob = prefs.getString(key, null) ?: return null
        return runCatching {
            val separator = blob.indexOf(':')
            if (separator <= 0) return null
            val iv = Base64.decode(blob.substring(0, separator), Base64.NO_WRAP)
            val payload = Base64.decode(blob.substring(separator + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            JSONObject(String(cipher.doFinal(payload), Charsets.UTF_8))
        }.getOrNull()
    }

    private fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hanxing_config_v1"
        const val KEY_PROFILES = "profiles_blob"
        const val KEY_LEGACY = "config_blob"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

package cn.hanxing.image.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/**
 * 把 OkHttp / java.net 的英文原始报错翻成人话；
 * 返回 null 表示不是网络类异常，调用方按原样展示。
 */
fun friendlyIoMessage(lowerText: String): String? = when {
    "timeout" in lowerText || "timed out" in lowerText -> "连接超时，检查网络或服务地址后重试"
    "unable to resolve host" in lowerText -> "域名解析失败，检查服务地址是否填对"
    "failed to connect" in lowerText || "connection refused" in lowerText || "econnrefused" in lowerText ->
        "连不上服务器，检查服务地址和网络"
    "connection reset" in lowerText || "econnreset" in lowerText -> "连接被重置，稍后重试"
    "broken pipe" in lowerText || "epipe" in lowerText -> "连接中断，稍后重试"
    "ssl" in lowerText -> "安全连接（SSL）握手失败，检查服务地址"
    "network" in lowerText && "unreachable" in lowerText -> "网络不可达，检查手机网络"
    else -> null
}

/**
 * 生图接口客户端（OpenAI 兼容）。
 *
 * - 读超时设为 0：4K 在中转线路上可能要跑十几二十分钟，不能中途被超时掐断。
 * - 只发 `{model, prompt, size, n}`；比例与清晰度全部折算进 `size`。
 */
class ImageApi {

    /** 每个请求一个 id：并行生成时只取消指定的那一条。 */
    private val calls = java.util.concurrent.ConcurrentHashMap<String, Call>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    fun cancel(requestId: String) {
        calls.remove(requestId)?.cancel()
    }

    /** 从服务端读模型列表，只保留识别为生图类的模型。 */
    suspend fun listModels(config: Profile): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl(config.baseUrl, "models"))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(friendlyHttpError(response.code, text))
            val data = runCatching { JSONObject(text).optJSONArray("data") }.getOrNull()
                ?: return@withContext emptyList()
            buildList {
                for (index in 0 until data.length()) {
                    val id = data.optJSONObject(index)?.optString("id").orEmpty()
                    if (id.isNotBlank() && isImageModel(id)) add(id)
                }
            }
        }
    }

    suspend fun generate(
        config: Profile,
        prompt: String,
        size: String,
        outputFormat: String = "png",
        requestId: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", config.model)
            .put("prompt", prompt)
            .put("size", size)
            .put("n", 1)
        // 只在 JPEG 时带上压缩参数：PNG 是默认行为，多传参数反而可能被某些网关拒绝
        if (outputFormat == "jpeg") {
            payload.put("output_format", "jpeg").put("output_compression", JPEG_QUALITY)
        }

        val request = Request.Builder()
            .url(apiUrl(config.baseUrl, "images/generations"))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        execute(request, requestId, onProgress)
    }

    /** 基于一张参考图改图：multipart 上传；[size] 为空时不带尺寸，交给上游按原图比例走。 */
    suspend fun edit(
        config: Profile,
        prompt: String,
        size: String?,
        image: ByteArray,
        fileName: String,
        outputFormat: String = "png",
        requestId: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
        size?.takeIf { it.isNotBlank() }?.let { builder.addFormDataPart("size", it) }
        if (outputFormat == "jpeg") {
            builder.addFormDataPart("output_format", "jpeg")
            builder.addFormDataPart("output_compression", JPEG_QUALITY.toString())
        }
        val body = builder
            .addFormDataPart("image", fileName, image.toRequestBody(PNG_MEDIA_TYPE))
            .build()

        val request = Request.Builder()
            .url(apiUrl(config.baseUrl, "images/edits"))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()

        execute(request, requestId, onProgress)
    }

    private suspend fun execute(
        request: Request,
        requestId: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray {
        val call = client.newCall(request)
        calls[requestId] = call
        return try {
            call.execute().use { response ->
                val body = response.body ?: throw ApiException("服务端没有返回内容")
                if (!response.isSuccessful) {
                    throw ApiException(friendlyHttpError(response.code, body.string()))
                }
                val total = body.contentLength()
                val input = body.byteStream()
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    received += read
                    onProgress(received, total)
                }
                parseImage(output.toByteArray())
            }
        } finally {
            calls.remove(requestId, call)
        }
    }

    private suspend fun parseImage(bytes: ByteArray): ByteArray {
        // 有些网关直接把图片字节当响应体返回（不是 JSON），PNG / JPEG / WebP 都认
        if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return bytes
        if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return bytes
        if (bytes.size > 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) return bytes
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            ?: throw ApiException("返回内容无法解析，可能不是图片")
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: throw ApiException("返回内容不是有效 JSON，可能不是图片")
        json.optJSONObject("error")?.let {
            throw ApiException(it.optString("message").ifBlank { "生图失败" })
        }
        val data = json.optJSONArray("data") ?: throw ApiException(
            json.optString("message").ifBlank { "返回里没有图片数据" }
        )
        val first = data.optJSONObject(0) ?: throw ApiException("返回里没有图片数据")
        val base64 = first.optString("b64_json")
        if (base64.isNotBlank()) return Base64.decode(base64, Base64.DEFAULT)
        val url = first.optString("url")
        if (url.startsWith("http")) return download(url)
        throw ApiException("返回里没有图片数据")
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("下载图片失败（HTTP ${response.code}）")
            response.body?.bytes() ?: throw ApiException("下载图片失败：响应为空")
        }
    }

    private fun isImageModel(id: String): Boolean {
        val lower = id.lowercase()
        return IMAGE_HINTS.any { lower.contains(it) }
    }

    private fun apiUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        return "$root/$path"
    }

    /**
     * 报错优先说服务端原话：429 既可能是限流也可能是额度，只有原文分得清。
     * 这里只补一句中文定性，再附上 HTTP 码，方便对照日志。
     */
    private fun friendlyHttpError(code: Int, bodyText: String): String {
        val detail = extractMessage(bodyText)
        val head = when (code) {
            400 -> "请求被拒绝"
            401 -> "Key 无效或不被接受"
            402 -> "账户余额不足"
            403 -> "没有权限，或内容被服务端拒绝"
            404 -> "接口地址或模型名不存在"
            413 -> "请求内容过大"
            429 -> "触发了限流"
            in 500..599 -> "上游服务异常"
            else -> "请求失败"
        }
        val base = "$head（HTTP $code）"
        return if (detail.isNullOrBlank()) "$base，稍后重试" else "$base：$detail"
    }

    /** 从响应体里挖出服务端的原始报错：JSON 的 error.message / message / detail，或 HTML 标题。 */
    private fun extractMessage(bodyText: String): String? {
        if (bodyText.isBlank()) return null
        val fromJson = runCatching {
            val json = JSONObject(bodyText)
            json.optJSONObject("error")?.let { error ->
                error.optString("message").takeIf { it.isNotBlank() }
                    ?: error.optString("code").takeIf { it.isNotBlank() }
            } ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull()
        val raw = fromJson ?: runCatching {
            // 网关有时直接甩 HTML（nginx 502 之类），抓标题或去标签
            Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(bodyText)?.groupValues?.get(1)
                ?: bodyText.replace(Regex("<[^>]+>"), " ")
        }.getOrNull()
        return raw
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(160)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val PNG_MEDIA_TYPE = "image/png".toMediaType()

        /** JPEG 压缩质量：90 是「肉眼几乎无差」和体积的平衡点（实测体积约为 PNG 的 28%）。 */
        const val JPEG_QUALITY = 90

        /** 只要名字里带这些词的，都当生图模型列进下拉。 */
        val IMAGE_HINTS = listOf(
            "image", "dall-e", "dalle", "flux", "seedream", "imagen",
            "nano-banana", "midjourney", "sdxl", "sd3", "qwen-image",
        )
    }
}

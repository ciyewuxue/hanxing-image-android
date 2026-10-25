package cn.hanxing.image.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 出图格式。[AUTO] 只在 4K 下转 JPEG——那个尺寸的 PNG 动辄十几兆，
 * 慢流下根本出不来；1K / 2K 仍然给无损。
 */
enum class ImageFormat(val id: String, val label: String, val hint: String) {
    PNG("png", "原图 PNG", "无损，体积最大：2K 一张约 2–4 MB"),
    JPEG("jpeg", "高质量 JPEG", "质量 90，体积约为 PNG 的 28%；服务端不支持时会自动回退原图"),
    AUTO("auto", "自动", "4K 用 JPEG、1K / 2K 保持原图；服务端不支持时回退原图"),
    ;

    companion object {
        fun fromId(id: String?): ImageFormat = entries.firstOrNull { it.id == id } ?: PNG
    }
}

/** 按文件头判断真实格式：网关可能忽略 output_format，仍按实际字节落盘更稳。 */
fun detectImageMime(bytes: ByteArray): String = when {
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
        "image/jpeg"

    bytes.size >= 12 &&
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" ->
        "image/webp"

    else -> "image/png"
}

/** 一条服务配置：一个 Key + 它对应的地址与模型。整份加密后落盘（见 [ConfigStore]）。 */data class Profile(
    val id: String,
    val name: String = "默认",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("baseUrl", baseUrl)
        .put("apiKey", apiKey)
        .put("model", model)

    companion object {
        /** 不预设任何服务地址和模型：由使用者自己填，避免把某一家中转当成默认。 */
        fun fromJson(o: JSONObject) = Profile(
            id = o.optString("id"),
            name = o.optString("name", "默认"),
            baseUrl = o.optString("baseUrl", ""),
            apiKey = o.optString("apiKey", ""),
            model = o.optString("model", ""),
        )
    }
}

/** 一个对话：生图按对话分组管理；首次生图后标题自动取自首条提示词。 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("createdAt", createdAt)

    companion object {
        const val DEFAULT_TITLE = "新对话"

        /** 用首条提示词生成标题：取第一行，最多 20 个字。 */
        fun titleFromPrompt(prompt: String): String {
            val firstLine = prompt.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isBlank()) return DEFAULT_TITLE
            return if (firstLine.length <= 20) firstLine else firstLine.take(20)
        }

        fun fromJson(o: JSONObject) = Conversation(
            id = o.optString("id"),
            title = o.optString("title", DEFAULT_TITLE),
            createdAt = o.optLong("createdAt", 0L),
        )

        fun listFromJson(array: JSONArray): List<Conversation> =
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { runCatching { fromJson(it) }.getOrNull() }
            }
    }
}

/** 一张生成结果。图片本体在私有目录里，这里只存元数据。 */
data class ImageRecord(
    val id: String,
    val fileName: String,
    val prompt: String,
    val size: String,
    val aspectLabel: String,
    val tierLabel: String,
    val model: String,
    val createdAt: Long,
    val seconds: Int,
    val savedToGallery: Boolean = false,
    val basedOnReference: Boolean = false,
    val conversationId: String = "",
) {
    val pixelLabel: String get() = size.replace("x", " × ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", fileName)
        .put("prompt", prompt)
        .put("size", size)
        .put("aspectLabel", aspectLabel)
        .put("tierLabel", tierLabel)
        .put("model", model)
        .put("createdAt", createdAt)
        .put("seconds", seconds)
        .put("savedToGallery", savedToGallery)
        .put("basedOnReference", basedOnReference)
        .put("conversationId", conversationId)

    companion object {
        fun fromJson(o: JSONObject) = ImageRecord(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            prompt = o.optString("prompt"),
            size = o.optString("size", "1024x1024"),
            aspectLabel = o.optString("aspectLabel", "1:1"),
            tierLabel = o.optString("tierLabel", "1K"),
            model = o.optString("model", ""),
            createdAt = o.optLong("createdAt", 0L),
            seconds = o.optInt("seconds", 0),
            savedToGallery = o.optBoolean("savedToGallery", false),
            basedOnReference = o.optBoolean("basedOnReference", false),
            conversationId = o.optString("conversationId", ""),
        )

        fun listFromJson(array: JSONArray): List<ImageRecord> =
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { runCatching { fromJson(it) }.getOrNull() }
            }
    }
}

/** 尺寸面板里可选的比例。 */
enum class Aspect(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1", 1, 1),
    LANDSCAPE("16:9", 16, 9),
    PORTRAIT("9:16", 9, 16),
    FOUR_THREE("4:3", 4, 3),
    THREE_FOUR("3:4", 3, 4);

    companion object {
        val all: List<Aspect> = entries
        fun of(label: String?): Aspect = all.firstOrNull { it.label == label } ?: SQUARE
    }
}

/** 清晰度档位，只决定长边目标值，具体像素由 [ImageSizes] 约束。 */
enum class Tier(val label: String, val longEdge: Int) {
    ONE_K("1K", 1024),
    TWO_K("2K", 2048),
    FOUR_K("4K", 3840);

    companion object {
        val all: List<Tier> = entries
        fun of(label: String?): Tier = all.firstOrNull { it.label == label } ?: ONE_K
    }
}

/** 一次请求最终落到的像素，以及它来自「手动选择」还是「提示词」。 */
data class ResolvedSize(
    val width: Int,
    val height: Int,
    val ratioLabel: String,
    val tierLabel: String,
    val custom: Boolean = false,
) {
    val request: String get() = "${width}x$height"
    val pixelLabel: String get() = "$width × $height"
}

/**
 * 尺寸换算，规则与网页版 `版本二十六-源码/src/lib/api.js` 一致：
 * 边长 16 的倍数、长边 ≤ 3840、长宽比 ≤ 3、总像素 655,360–8,294,400。
 *
 * 面板选「自动」时按提示词判断：先看显式像素（`1920*1088`），再看比例/清晰度关键词；
 * 都没提就落回 1:1 · 1K；只写了「4K」没写比例时按 16:9 处理（方形 4K 需明确写 1:1/方形）。
 */
object ImageSizes {

    const val AUTO = "自动"

    private const val MAX_PIXELS = 8_294_400L
    private const val MIN_PIXELS = 655_360L
    private const val MAX_EDGE = 3840

    private val RATIOS: Map<String, Pair<Int, Int>> = linkedMapOf(
        "1:1" to (1 to 1),
        "16:9" to (16 to 9),
        "9:16" to (9 to 16),
        "4:3" to (4 to 3),
        "3:4" to (3 to 4),
        "3:2" to (3 to 2),
        "2:3" to (2 to 3),
        "21:9" to (21 to 9),
    )

    private val RATIO_IN_PROMPT = Regex("""(\d{1,2})\s*[:：]\s*(\d{1,2})""")
    private val PIXELS_IN_PROMPT = Regex("""(\d{3,4})\s*[x×*＊]\s*(\d{3,4})""", RegexOption.IGNORE_CASE)
    private val FOUR_K = Regex("""(?:^|[^0-9a-z])4\s*k(?:[^0-9a-z]|$)""", RegexOption.IGNORE_CASE)
    private val TWO_K = Regex("""(?:^|[^0-9a-z])2\s*k(?:[^0-9a-z]|$)""", RegexOption.IGNORE_CASE)
    private val ONE_K = Regex("""(?:^|[^0-9a-z])1\s*k(?:[^0-9a-z]|$)""", RegexOption.IGNORE_CASE)

    /** 按钮上显示的当前选择，例如「自动」「16:9 · 自动」「16:9 · 4K」。 */
    fun selectionLabel(ratioSetting: String, tierSetting: String): String =
        if (ratioSetting == AUTO && tierSetting == AUTO) {
            AUTO
        } else {
            "$ratioSetting · $tierSetting"
        }

    fun resolve(ratioSetting: String, tierSetting: String, prompt: String): ResolvedSize {
        val manualRatio = ratioSetting.takeIf { RATIOS.containsKey(it) }
        val manualTier = tierSetting.takeIf { it != AUTO }?.let { Tier.of(it) }

        // 两项都选自动时，提示词里的显式像素优先
        if (manualRatio == null && manualTier == null) {
            pixelSizeFromPrompt(prompt)?.let { (width, height) ->
                return ResolvedSize(
                    width = width,
                    height = height,
                    ratioLabel = "自定义",
                    tierLabel = tierForPixels(width.toLong() * height),
                    custom = true,
                )
            }
        }

        val promptRatio = ratioFromPrompt(prompt)
        val promptTier = tierFromPrompt(prompt)

        var ratioLabel = manualRatio ?: promptRatio ?: "1:1"
        val tierLabel = manualTier?.label ?: promptTier
        // 只说了「4K」没说比例：按 16:9 走，而不是方形 2880
        if (manualRatio == null && promptRatio == null && tierLabel == Tier.FOUR_K.label) {
            ratioLabel = "16:9"
        }

        val tier = tierLabel?.let { Tier.of(it) } ?: Tier.ONE_K
        val (width, height) = pixelsFor(RATIOS.getValue(ratioLabel), tier)
        return ResolvedSize(width, height, ratioLabel, tier.label)
    }

    /** 提示词里是否明确提到了尺寸（像素、比例或清晰度）。改图时用它判断要不要沿用原图尺寸。 */
    fun hasSizeIntent(prompt: String): Boolean =
        pixelSizeFromPrompt(prompt) != null || ratioFromPrompt(prompt) != null || tierFromPrompt(prompt) != null

    private fun pixelsFor(ratio: Pair<Int, Int>, tier: Tier): Pair<Int, Int> {
        val longAspect = max(ratio.first, ratio.second)
        val shortAspect = min(ratio.first, ratio.second)
        val tierEdge = min(tier.longEdge, MAX_EDGE) / 16 * 16

        fun sizeFor(longEdge: Int): Pair<Int, Int> {
            var short = (longEdge.toDouble() * shortAspect / longAspect).roundToInt()
            short = ((short + 8) / 16) * 16
            if (short < 16) short = 16
            return if (ratio.first >= ratio.second) longEdge to short else short to longEdge
        }

        // 长边从清晰度对应的边长起步；面积超上限就往回收，低于下限就往外放——
        // 9:16 这类瘦长比例在 1K 下面积不足，必须放一点点，否则只能退回方形。
        var long = tierEdge
        var size = sizeFor(long)
        while (size.first.toLong() * size.second > MAX_PIXELS && long - 16 >= 256) {
            long -= 16
            size = sizeFor(long)
        }
        while (size.first.toLong() * size.second < MIN_PIXELS && long + 16 <= MAX_EDGE) {
            long += 16
            size = sizeFor(long)
        }
        return if (size.first.toLong() * size.second in MIN_PIXELS..MAX_PIXELS) size else 1024 to 1024
    }

    private fun ratioFromPrompt(prompt: String): String? {
        for (match in RATIO_IN_PROMPT.findAll(prompt)) {
            val key = "${match.groupValues[1].toInt()}:${match.groupValues[2].toInt()}"
            if (RATIOS.containsKey(key)) return key
        }
        return when {
            Regex("超宽|全景|宽银幕|带鱼屏").containsMatchIn(prompt) -> "21:9"
            Regex("竖版|竖图|竖幅|竖屏|手机壁纸|竖向").containsMatchIn(prompt) -> "9:16"
            Regex("横版|横图|横幅|横屏|宽屏|横向").containsMatchIn(prompt) -> "16:9"
            Regex("方形|正方形|方图|头像").containsMatchIn(prompt) -> "1:1"
            else -> null
        }
    }

    private fun tierFromPrompt(prompt: String): String? = when {
        FOUR_K.containsMatchIn(prompt) || Regex("2160\\s*p|uhd|超高清", RegexOption.IGNORE_CASE)
            .containsMatchIn(prompt) -> Tier.FOUR_K.label

        TWO_K.containsMatchIn(prompt) || Regex("1440\\s*p", RegexOption.IGNORE_CASE)
            .containsMatchIn(prompt) -> Tier.TWO_K.label

        ONE_K.containsMatchIn(prompt) || Regex("1080\\s*p", RegexOption.IGNORE_CASE)
            .containsMatchIn(prompt) -> Tier.ONE_K.label

        else -> null
    }

    /** 提示词里的 `1920*1088` / `1024x1024`：合并到 16 的倍数后仍不合法就忽略。 */
    private fun pixelSizeFromPrompt(prompt: String): Pair<Int, Int>? {
        for (match in PIXELS_IN_PROMPT.findAll(prompt)) {
            var width = match.groupValues[1].toInt()
            var height = match.groupValues[2].toInt()
            if (!isValidSize(width, height)) {
                width = alignTo16(width)
                height = alignTo16(height)
            }
            if (isValidSize(width, height)) return width to height
        }
        return null
    }

    private fun alignTo16(value: Int): Int = max(16, (value / 16.0).roundToInt() * 16)

    private fun isValidSize(width: Int, height: Int): Boolean {
        if (width < 16 || height < 16) return false
        if (width % 16 != 0 || height % 16 != 0) return false
        val longEdge = max(width, height)
        val shortEdge = min(width, height)
        if (longEdge > MAX_EDGE) return false
        if (longEdge.toDouble() / shortEdge > 3.0) return false
        val area = width.toLong() * height
        return area in MIN_PIXELS..MAX_PIXELS
    }

    private fun tierForPixels(area: Long): String = when {
        area >= 6_000_000L -> Tier.FOUR_K.label
        area >= 2_500_000L -> Tier.TWO_K.label
        else -> Tier.ONE_K.label
    }
}

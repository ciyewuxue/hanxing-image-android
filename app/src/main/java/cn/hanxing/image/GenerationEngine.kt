package cn.hanxing.image

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import cn.hanxing.image.data.Conversation
import cn.hanxing.image.data.ImageApi
import cn.hanxing.image.data.ImageFormat
import cn.hanxing.image.data.ImageRecord
import cn.hanxing.image.data.ImageSizes
import cn.hanxing.image.data.ImageStore
import cn.hanxing.image.data.Profile
import cn.hanxing.image.data.ResolvedSize
import cn.hanxing.image.data.Tier
import cn.hanxing.image.data.detectImageMime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Toast(val text: String, val undo: Boolean = false, val token: Long = System.nanoTime())

/** 当前轮次用作改图底图的图片；来自生成记录或相册。 */
data class ReferenceImage(
    val name: String,
    val size: String?,
    val file: java.io.File,
    val pixelLabel: String?,
)

/** 一条正在跑的生成任务：哪个对话、从什么时候开始、收到多少。 */
data class GenerationProgress(
    val conversationId: String,
    val startedAt: Long,
    val received: Long = 0,
    val total: Long = -1,
    val elapsedMs: Long = 0,
)

/** 生成引擎的状态；界面（ViewModel）与前台通知都从这里读。 */
data class EngineState(
    val records: List<ImageRecord> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val conversationCounts: Map<String, Int> = emptyMap(),
    /** 当前对话是否在生成。 */
    val generating: Boolean = false,
    /** 所有正在生成的对话 id，抽屉里用来标记。 */
    val generatingIds: Set<String> = emptySet(),
    /** 所有正在跑的任务，按开始时间排序。 */
    val running: List<GenerationProgress> = emptyList(),
    val received: Long = 0,
    val total: Long = -1,
    val elapsedMs: Long = 0,
    val lastFailure: String? = null,
    val toast: Toast? = null,
)

/**
 * 生成引擎：应用级单例，生图请求跑在自己的协程里，不挂在 Activity/ViewModel 上。
 * 4K 可能要跑十几二十分钟，切后台、转屏、界面重建都不该打断它——
 * 真正的进程保活由 [GenerationService]（前台服务）负责，这里只管把活干完、把记录落盘。
 *
 * 支持多对话并行：每个对话最多一条任务，全局最多 [MAX_CONCURRENT] 条，
 * 进度、取消都按对话区分；切对话、新建对话不会被正在跑的任务拦住。
 */
class GenerationEngine(private val app: Context) {

    val imageStore: ImageStore = ImageStore(app).also { it.reconcile() }
    private val api = ImageApi()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Run(val job: Job, val requestId: String)

    /** 对话 id → 正在跑的任务。 */
    private val runs = ConcurrentHashMap<String, Run>()

    /** 对话 id → 该任务的进度。 */
    private val progress = ConcurrentHashMap<String, GenerationProgress>()

    private val _state = MutableStateFlow(
        EngineState(
            records = imageStore.recordsFor(imageStore.activeConversationId),
            conversations = imageStore.conversations,
            activeConversationId = imageStore.activeConversationId,
            conversationCounts = countByConversation(),
        )
    )
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // ---- 生成 / 改图 ----

    fun start(
        config: Profile,
        text: String,
        resolved: ResolvedSize,
        manual: Boolean,
        reference: ReferenceImage?,
        format: ImageFormat = ImageFormat.PNG,
    ) {
        // 任务归到点击时的这个对话，之后切走也不会画到别的对话里
        val conversation = imageStore.ensureActiveConversation()
        if (runs.containsKey(conversation.id)) {
            _state.update { it.copy(toast = Toast("这个对话还在画，等它完成或先停止")) }
            return
        }
        if (runs.size >= MAX_CONCURRENT) {
            _state.update { it.copy(toast = Toast("最多同时画 $MAX_CONCURRENT 张，等一张完成再开")) }
            return
        }

        val editing = reference != null && reference.file.exists()

        // 改图时若没指定尺寸，沿用参考图的尺寸；相册图没有尺寸就交给上游按原图走
        val size: String? = when {
            !editing -> resolved.request
            manual || ImageSizes.hasSizeIntent(text) -> resolved.request
            else -> reference.size
        }
        // 自动：只有 4K 换 JPEG（那个尺寸的 PNG 太大，慢流下几乎出不来）
        val outputFormat = when (format) {
            ImageFormat.JPEG -> "jpeg"
            ImageFormat.PNG -> "png"
            ImageFormat.AUTO -> if (resolved.tierLabel == Tier.FOUR_K.label) "jpeg" else "png"
        }
        val startedAt = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()

        progress[conversation.id] = GenerationProgress(conversation.id, startedAt)
        _state.update { it.copy(lastFailure = null) }
        refresh()
        startKeepAlive()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ticker = launch {
                while (true) {
                    delay(1000)
                    updateProgress(conversation.id) {
                        it.copy(elapsedMs = System.currentTimeMillis() - startedAt)
                    }
                }
            }

            try {
                val onProgress: (Long, Long) -> Unit = { received, total ->
                    updateProgress(conversation.id) { it.copy(received = received, total = total) }
                }
                val bytes = if (editing) {
                    val ref = reference!!
                    api.edit(
                        config = config,
                        prompt = text,
                        size = size,
                        image = withContext(Dispatchers.IO) { ref.file.readBytes() },
                        fileName = ref.name,
                        outputFormat = outputFormat,
                        requestId = requestId,
                        onProgress = onProgress,
                    )
                } else {
                    api.generate(
                        config = config,
                        prompt = text,
                        size = size ?: resolved.request,
                        outputFormat = outputFormat,
                        requestId = requestId,
                        onProgress = onProgress,
                    )
                }
                val actualSize = pixelSizeOf(bytes)
                // 网关可能忽略格式参数，按真实字节定扩展名，避免相册里出现「.png 的 JPEG」
                val extension = if (detectImageMime(bytes) == "image/jpeg") "jpg" else "png"
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                val wasEmpty = imageStore.recordsFor(conversation.id).isEmpty()
                val record = ImageRecord(
                    id = UUID.randomUUID().toString(),
                    fileName = "hanxing_${System.currentTimeMillis()}.$extension",
                    prompt = text,
                    size = actualSize ?: size ?: reference?.pixelLabel?.replace(" × ", "x") ?: "",
                    aspectLabel = resolved.ratioLabel,
                    tierLabel = resolved.tierLabel,
                    model = config.model,
                    createdAt = System.currentTimeMillis(),
                    seconds = seconds,
                    basedOnReference = editing,
                    conversationId = conversation.id,
                )
                withContext(Dispatchers.Main.immediate) {
                    imageStore.add(record, bytes)
                    if (wasEmpty) imageStore.autoTitle(conversation.id, text)
                }
            } catch (error: Exception) {
                val message = friendlyError(error)
                _state.update {
                    it.copy(
                        lastFailure = message,
                        toast = if (message == null) Toast("已停止") else null,
                    )
                }
            } finally {
                ticker.cancel()
                progress.remove(conversation.id)
                runs.remove(conversation.id)
                refresh()
                if (runs.isEmpty()) stopKeepAlive()
            }
        }

        runs[conversation.id] = Run(job, requestId)
        job.start()
    }

    /** 把底层异常翻成人话；返回 null 表示用户主动停止，不用提示。 */
    private fun friendlyError(error: Exception): String? {
        if (error.isStopSignal()) return null
        val raw = error.message ?: return "生成失败"
        val friendly = cn.hanxing.image.data.friendlyIoMessage(raw.lowercase())
        return friendly ?: raw.ifBlank { "生成失败" }
    }

    /** 用一条旧记录的参数重画（沿用它的提示词与尺寸）。 */
    fun retry(config: Profile, record: ImageRecord, format: ImageFormat = ImageFormat.PNG) {
        val parts = record.size.split("x")
        val width = parts.getOrNull(0)?.toIntOrNull()
        val height = parts.getOrNull(1)?.toIntOrNull()
        val resolved = if (width != null && height != null) {
            ResolvedSize(
                width = width,
                height = height,
                ratioLabel = record.aspectLabel,
                tierLabel = record.tierLabel,
                custom = record.aspectLabel == "自定义",
            )
        } else {
            ImageSizes.resolve(record.aspectLabel, record.tierLabel, record.prompt)
        }
        start(config, record.prompt, resolved, manual = true, reference = null, format = format)
    }

    /** 停掉当前对话正在跑的那一张，别的对话不受影响。 */
    fun stop() {
        val id = imageStore.activeConversationId ?: return
        val run = runs[id] ?: return
        api.cancel(run.requestId)
    }

    /** 从服务端读模型列表（与生图共用同一个客户端）。 */
    suspend fun listModels(config: Profile): List<String> = api.listModels(config)

    // ---- 对话 ----

    fun newConversation() {
        imageStore.createConversation()
        refresh()
    }

    fun selectConversation(id: String) {
        imageStore.setActiveConversation(id)
        refresh()
    }

    /** 删对话返回被移走的图片数；删除后焦点自动落到下一个对话。 */
    fun deleteConversation(id: String): Int {
        if (runs.containsKey(id)) {
            _state.update { it.copy(toast = Toast("这个对话还在画，先停止再删")) }
            return -1
        }
        val count = imageStore.deleteConversation(id)
        refresh()
        return count
    }

    private fun countByConversation(): Map<String, Int> =
        imageStore.records.groupBy { it.conversationId }.mapValues { entry -> entry.value.size }

    // ---- 记录操作（与界面共用同一份 ImageStore）----

    fun markSaved(id: String) {
        imageStore.markSaved(id, true)
        refresh()
    }

    fun delete(id: String): ImageRecord? {
        val removed = imageStore.delete(id)
        refresh()
        return removed
    }

    fun restore(record: ImageRecord): Boolean {
        val ok = imageStore.restore(record)
        if (ok) refresh()
        return ok
    }

    fun clearToast() {
        _state.update { if (it.toast == null) it else it.copy(toast = null) }
    }

    fun dismissFailure() {
        _state.update { if (it.lastFailure == null) it else it.copy(lastFailure = null) }
    }

    // ---- 状态汇总 ----

    /**
     * 把仓库、任务表汇总成界面状态：列表永远按「当前对话」取，
     * 进度只映射当前对话那一份，抽屉则看 [EngineState.generatingIds]。
     */
    private fun refresh(transform: (EngineState) -> EngineState = { it }) {
        _state.update { current ->
            val activeId = imageStore.activeConversationId
            val activeProgress = progress[activeId]
            transform(
                current.copy(
                    records = imageStore.recordsFor(activeId),
                    conversations = imageStore.conversations,
                    activeConversationId = activeId,
                    conversationCounts = countByConversation(),
                    generatingIds = runs.keys.toSet(),
                    running = progress.values.sortedBy { item -> item.startedAt },
                    generating = activeProgress != null,
                    received = activeProgress?.received ?: 0L,
                    total = activeProgress?.total ?: -1L,
                    elapsedMs = activeProgress?.elapsedMs ?: 0L,
                )
            )
        }
    }

    private fun updateProgress(id: String, transform: (GenerationProgress) -> GenerationProgress) {
        val current = progress[id] ?: return
        progress[id] = transform(current)
        refresh()
    }

    // ---- 前台服务保活 ----

    private fun startKeepAlive() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, GenerationService::class.java))
        }
    }

    private fun stopKeepAlive() {
        runCatching {
            app.stopService(Intent(app, GenerationService::class.java))
        }
    }

    private fun Exception.isStopSignal(): Boolean {
        val text = (message ?: "").lowercase()
        return this is java.io.IOException && (text.contains("cancel") || text.contains("closed"))
    }

    private fun pixelSizeOf(bytes: ByteArray): String? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) "${bounds.outWidth}x${bounds.outHeight}" else null
    }.getOrNull()

    companion object {
        /** 同时最多跑几张：再多也抢不到带宽，还容易撞上游限流。 */
        const val MAX_CONCURRENT = 3
    }
}

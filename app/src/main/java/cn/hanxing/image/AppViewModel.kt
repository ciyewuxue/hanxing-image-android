package cn.hanxing.image

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.hanxing.image.data.ConfigStore
import cn.hanxing.image.data.ImageFormat
import cn.hanxing.image.data.ImageFormatStore
import cn.hanxing.image.data.ImageRecord
import cn.hanxing.image.data.ImageSizes
import cn.hanxing.image.data.Profile
import cn.hanxing.image.data.ResolvedSize
import cn.hanxing.image.ui.Accent
import cn.hanxing.image.ui.ThemeMode
import cn.hanxing.image.ui.ThemeSettings
import cn.hanxing.image.ui.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val ready: Boolean = false,
    val theme: ThemeSettings = ThemeSettings(),
    val profiles: List<Profile> = emptyList(),
    val activeId: String? = null,
    val settingsOpen: Boolean = false,
    val records: List<ImageRecord> = emptyList(),
    val conversations: List<cn.hanxing.image.data.Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val conversationCounts: Map<String, Int> = emptyMap(),
    val reference: ReferenceImage? = null,
    val models: List<String> = emptyList(),
    /** 这批 models 是从哪个服务地址读回来的：换地址/新建配置时不能拿旧列表充数 */
    val modelsForUrl: String? = null,
    val selectedModel: String = "",
    val loadingModels: Boolean = false,
    val modelError: String? = null,
    val generating: Boolean = false,
    val generatingIds: Set<String> = emptySet(),
    val received: Long = 0,
    val total: Long = -1,
    val elapsedMs: Long = 0,
    val lastFailure: String? = null,
    val toast: Toast? = null,
    val imageFormat: ImageFormat = ImageFormat.PNG,
) {
    val activeProfile: Profile?
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}

/**
 * 界面状态壳：配置、模型、主题、参考图选择留在这里；
 * 生成与记录全部委托给应用级 [GenerationEngine]——界面销毁不影响正在跑的生图。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val configStore = ConfigStore(application)
    private val themeStore = ThemeStore(application)
    private val formatStore = ImageFormatStore(application)
    private val engine = (application as HanxingApp).engine
    private val imageStore get() = engine.imageStore

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastDeleted: ImageRecord? = null

    init {
        val snapshot = configStore.load()
        val theme = themeStore.load()
        _state.update {
            it.copy(
                ready = true,
                theme = theme,
                profiles = snapshot.profiles,
                activeId = snapshot.activeId,
                imageFormat = formatStore.load(),
            )
        }
        // 引擎状态（记录、生成进度、失败提示）汇进界面状态
        viewModelScope.launch {
            engine.state.collect { engineState ->
                _state.update {
                    it.copy(
                        records = engineState.records,
                        conversations = engineState.conversations,
                        activeConversationId = engineState.activeConversationId,
                        conversationCounts = engineState.conversationCounts,
                        generating = engineState.generating,
                        generatingIds = engineState.generatingIds,
                        received = engineState.received,
                        total = engineState.total,
                        elapsedMs = engineState.elapsedMs,
                        lastFailure = engineState.lastFailure,
                        toast = it.toast ?: engineState.toast,
                    )
                }
            }
        }
        _state.value.activeProfile?.let { loadModels(it) }
    }

    fun fileFor(record: ImageRecord) = imageStore.fileOf(record)

    fun openSettings() {
        _state.update { it.copy(settingsOpen = true) }
    }

    fun closeSettings() {
        _state.update { it.copy(settingsOpen = false, modelError = null) }
    }

    // ---- 配置（多 Key）----

    fun saveProfile(profile: Profile) {
        val cleaned = profile.copy(
            name = profile.name.trim().ifBlank { "未命名" },
            baseUrl = profile.baseUrl.trim(),
            apiKey = profile.apiKey.trim(),
            model = profile.model.trim(),
        )
        val validation = when {
            cleaned.baseUrl.isBlank() -> "先填服务地址"
            !cleaned.baseUrl.lowercase().startsWith("http://") &&
                !cleaned.baseUrl.lowercase().startsWith("https://") ->
                "服务地址要以 http:// 或 https:// 开头"

            cleaned.apiKey.isBlank() -> "先填 API Key"
            _state.value.models.isEmpty() -> "请先点击上方获取模型列表"
            cleaned.model.isBlank() -> "先选一个模型，或点「获取模型列表」后挑一个"
            else -> null
        }
        if (validation != null) {
            _state.update { it.copy(modelError = validation) }
            return
        }

        val current = _state.value
        val existed = current.profiles.any { it.id == cleaned.id }
        val profiles = if (existed) {
            current.profiles.map { if (it.id == cleaned.id) cleaned else it }
        } else {
            current.profiles + cleaned
        }
        val activeId = current.activeId ?: cleaned.id
        persistProfiles(profiles, activeId)

        val becameActive = activeId == cleaned.id
        _state.update {
            it.copy(
                profiles = profiles,
                activeId = activeId,
                settingsOpen = if (current.profiles.isEmpty()) false else it.settingsOpen,
                modelError = null,
            )
        }
        if (becameActive) loadModels(cleaned) else refreshModelsIfActiveChanged(cleaned)
    }

    private fun refreshModelsIfActiveChanged(edited: Profile) {
        val active = _state.value.activeProfile ?: return
        if (active.id == edited.id) loadModels(active)
    }

    fun selectProfile(id: String) {
        val current = _state.value
        val profile = current.profiles.firstOrNull { it.id == id } ?: return
        if (current.activeId == id) return
        persistProfiles(current.profiles, id)
        _state.update { it.copy(activeId = id, models = emptyList(), modelsForUrl = null, modelError = null) }
        loadModels(profile)
    }

    fun deleteProfile(id: String) {
        val current = _state.value
        if (current.profiles.size <= 1) {
            _state.update { it.copy(toast = Toast("至少保留一条配置")) }
            return
        }
        val profiles = current.profiles.filterNot { it.id == id }
        val activeId = if (current.activeId == id) profiles.first().id else current.activeId
        persistProfiles(profiles, activeId)
        _state.update { it.copy(profiles = profiles, activeId = activeId, models = emptyList(), modelsForUrl = null, modelError = null) }
        profiles.firstOrNull { it.id == activeId }?.let { loadModels(it) }
    }

    private fun persistProfiles(profiles: List<Profile>, activeId: String?) {
        configStore.save(ConfigStore.Snapshot(profiles, activeId))
    }

    // ---- 主题 ----

    fun setAccent(accent: Accent) {
        val theme = _state.value.theme.copy(accent = accent)
        themeStore.save(theme)
        _state.update { it.copy(theme = theme) }
    }

    fun setThemeMode(mode: ThemeMode) {
        val theme = _state.value.theme.copy(mode = mode)
        themeStore.save(theme)
        _state.update { it.copy(theme = theme) }
    }

    // ---- 图片格式 ----

    fun setImageFormat(format: ImageFormat) {
        formatStore.save(format)
        _state.update { it.copy(imageFormat = format) }
    }

    // ---- 模型列表 ----

    /** 主界面下拉选中的「本次生图模型」；默认沿用配置里存的那个。 */
    fun selectModel(model: String) {
        _state.update { it.copy(selectedModel = model) }
    }

    private fun pickDefaultModel(models: List<String>, configured: String): String =
        if (configured.isNotBlank() && models.contains(configured)) configured
        else models.firstOrNull().orEmpty()

    fun loadModels(profile: Profile) {
        if (profile.apiKey.isBlank()) return
        _state.update { it.copy(loadingModels = true, modelError = null) }
        viewModelScope.launch {
            val result = runCatching { engine.listModels(profile) }
            result.fold(
                onSuccess = { models ->
                    _state.update {
                        it.copy(
                            loadingModels = false,
                            modelsForUrl = profile.baseUrl.trim().trimEnd('/'),
                            models = models,
                            selectedModel = pickDefaultModel(models, profile.model),
                            modelError = if (models.isEmpty()) {
                                "服务端没返回可识别的生图模型"
                            } else null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loadingModels = false,
                            modelsForUrl = null,
                            models = emptyList(),
                            selectedModel = "",
                            modelError = cn.hanxing.image.data.friendlyIoMessage((error.message ?: "").lowercase())
                                ?: (error.message ?: "读取模型列表失败"),
                        )
                    }
                },
            )
        }
    }

    // ---- 生图 / 改图 ----

    fun generate(prompt: String, ratioSetting: String, tierSetting: String) {
        val text = prompt.trim()
        if (text.isBlank()) return
        val profile = _state.value.activeProfile ?: return
        val manual = ratioSetting != ImageSizes.AUTO || tierSetting != ImageSizes.AUTO
        engine.start(
            config = profile.withSelectedModel(),
            text = text,
            resolved = ImageSizes.resolve(ratioSetting, tierSetting, text),
            manual = manual,
            reference = _state.value.reference,
            format = _state.value.imageFormat,
        )
    }

    /** 本次生图用主界面下拉选中的模型；没选过就用配置里的默认。 */
    private fun Profile.withSelectedModel(): Profile {
        val selected = _state.value.selectedModel
        return if (selected.isNotBlank() && selected != model) copy(model = selected) else this
    }

    fun useAsReference(record: ImageRecord) {
        val file = imageStore.fileById(record.id) ?: return
        _state.update {
            it.copy(
                reference = ReferenceImage(
                    name = record.fileName,
                    size = record.size,
                    file = file,
                    pixelLabel = record.pixelLabel,
                ),
                toast = Toast("已设为参考图，写下想怎么改"),
            )
        }
    }

    /** 从相册选一张图当参考图：复制进缓存目录，尺寸留给上游按原图走。 */
    fun usePickedReference(uri: android.net.Uri) {
        viewModelScope.launch {
            val reference = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val name = "picked_${System.currentTimeMillis()}.png"
                    val file = File(app.cacheDir, name)
                    file.writeBytes(bytes)
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    ReferenceImage(
                        name = name,
                        size = null,
                        file = file,
                        pixelLabel = if (bounds.outWidth > 0) "${bounds.outWidth} × ${bounds.outHeight}" else null,
                    )
                }.getOrNull()
            }
            if (reference == null) {
                _state.update { it.copy(toast = Toast("这张图读不出来，换一张试试")) }
            } else {
                _state.update {
                    it.copy(reference = reference, toast = Toast("已选好参考图，写下想怎么改"))
                }
            }
        }
    }

    fun clearReference() {
        _state.update { it.copy(reference = null) }
    }

    fun stop() {
        engine.stop()
    }

    // ---- 对话 ----

    fun newConversation() {
        engine.newConversation()
        _state.update { it.copy(reference = null) }
    }

    fun selectConversation(id: String) {
        engine.selectConversation(id)
        _state.update { it.copy(reference = null) }
    }

    /** 删对话（含其中的图）；返回 false 表示被生成中状态拦下。 */
    fun deleteConversation(id: String) {
        val count = engine.deleteConversation(id)
        if (count < 0) return // 引擎已弹「正在生成」提示
        _state.update {
            it.copy(
                reference = null,
                toast = Toast("已删除对话及其中 $count 张图"),
            )
        }
    }

    // ---- 记录操作 ----

    fun saveToGallery(record: ImageRecord) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val file = imageStore.fileOf(record)
                    if (!file.exists()) return@runCatching false
                    cn.hanxing.image.data.GallerySaver.save(
                        getApplication(),
                        file.readBytes(),
                        record.fileName,
                    )
                }.getOrDefault(false)
            }
            if (success) {
                engine.markSaved(record.id)
                _state.update { it.copy(toast = Toast("已保存到相册 · 寒星生图")) }
            } else {
                _state.update { it.copy(toast = Toast("保存失败，检查一下存储空间")) }
            }
        }
    }

    fun delete(record: ImageRecord) {
        val removed = engine.delete(record.id)
        lastDeleted = removed
        _state.update {
            it.copy(
                toast = if (removed != null) Toast("已删除 1 张图", undo = true) else Toast("删除失败"),
            )
        }
    }

    fun undoDelete() {
        val record = lastDeleted ?: return
        if (engine.restore(record)) {
            lastDeleted = null
            _state.update { it.copy(toast = Toast("已恢复")) }
        }
    }

    fun retry(record: ImageRecord) {
        val profile = _state.value.activeProfile ?: return
        engine.retry(profile.withSelectedModel(), record, _state.value.imageFormat)
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
        engine.clearToast()
    }

    fun dismissFailure() {
        engine.dismissFailure()
    }

    fun clearModelError() {
        _state.update { it.copy(modelError = null) }
    }
}

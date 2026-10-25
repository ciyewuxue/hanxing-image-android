package cn.hanxing.image.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 图片仓库：图片本体放在应用私有目录 `files/images/`，元数据放 `files/index.json`，
 * 对话列表放 `files/conversations.json`。
 * 删除走 `files/trash/` 软删除，撤销窗口内可以移回来；超过保留期由 [reconcile] 清掉。
 *
 * 注意：系统相册里的副本不归这里管，任何删除操作都不碰 MediaStore。
 */
class ImageStore(context: Context) {

    private val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
    private val trashDir = File(context.filesDir, "trash").apply { mkdirs() }
    private val indexFile = AtomicFile(File(context.filesDir, "index.json"))
    private val conversationsFile = AtomicFile(File(context.filesDir, "conversations.json"))

    private val _records = mutableListOf<ImageRecord>()
    private val _conversations = mutableListOf<Conversation>()
    private var activeId: String? = null

    /** 按创建时间倒序（新的在前）。 */
    val records: List<ImageRecord>
        @Synchronized get() = _records.toList()

    /** 按创建时间倒序（新的在前）。 */
    val conversations: List<Conversation>
        @Synchronized get() = _conversations.toList()

    val activeConversationId: String?
        @Synchronized get() = activeId

    init {
        loadIndex()
        loadConversations()
        migrate()
    }

    @Synchronized
    fun fileOf(record: ImageRecord): File = File(imagesDir, record.fileName)

    @Synchronized
    fun fileById(id: String): File? {
        val record = _records.firstOrNull { it.id == id } ?: return null
        val file = File(imagesDir, record.fileName)
        return if (file.exists()) file else null
    }

    @Synchronized
    fun recordsFor(conversationId: String?): List<ImageRecord> =
        _records.filter { it.conversationId == conversationId }

    // ---- 对话 ----

    /** 当前对话；没有就建一个空的顶上。 */
    @Synchronized
    fun ensureActiveConversation(): Conversation {
        val existing = _conversations.firstOrNull { it.id == activeId }
            ?: _conversations.firstOrNull()
        if (existing != null) {
            activeId = existing.id
            return existing
        }
        return createConversation()
    }

    @Synchronized
    fun createConversation(): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = Conversation.DEFAULT_TITLE,
            createdAt = System.currentTimeMillis(),
        )
        _conversations.add(0, conversation)
        activeId = conversation.id
        persistConversations()
        return conversation
    }

    @Synchronized
    fun setActiveConversation(id: String) {
        if (_conversations.none { it.id == id }) return
        activeId = id
        persistConversations()
    }

    /** 首次生图后按提示词自动起名；只有还是默认标题时才改。 */
    @Synchronized
    fun autoTitle(conversationId: String, prompt: String) {
        val index = _conversations.indexOfFirst { it.id == conversationId }
        if (index < 0) return
        if (_conversations[index].title != Conversation.DEFAULT_TITLE) return
        _conversations[index] = _conversations[index].copy(
            title = Conversation.titleFromPrompt(prompt),
        )
        persistConversations()
    }

    /** 删对话：其中图片进 trash、记录移除；删了当前对话就切到下一个，删光就补一个空对话。 */
    @Synchronized
    fun deleteConversation(id: String): Int {
        val index = _conversations.indexOfFirst { it.id == id }
        if (index < 0) return 0
        val removed = _records.filter { it.conversationId == id }
        removed.forEach { record ->
            val source = File(imagesDir, record.fileName)
            if (source.exists()) runCatching { source.renameTo(File(trashDir, record.fileName)) }
        }
        _records.removeAll(removed.toSet())
        _conversations.removeAt(index)
        if (activeId == id) activeId = _conversations.firstOrNull()?.id
        persist()
        persistConversations()
        if (_conversations.isEmpty()) createConversation()
        return removed.size
    }

    @Synchronized
    fun add(record: ImageRecord, bytes: ByteArray): ImageRecord {
        val target = File(imagesDir, record.fileName)
        target.writeBytes(bytes)
        _records.add(0, record)
        persist()
        return record
    }

    @Synchronized
    fun markSaved(id: String, saved: Boolean = true) {
        val index = _records.indexOfFirst { it.id == id }
        if (index < 0) return
        _records[index] = _records[index].copy(savedToGallery = saved)
        persist()
    }

    /** 软删除：文件移到 trash，记录从索引里移除，返回被删记录。 */
    @Synchronized
    fun delete(id: String): ImageRecord? {
        val index = _records.indexOfFirst { it.id == id }
        if (index < 0) return null
        val record = _records.removeAt(index)
        val source = File(imagesDir, record.fileName)
        if (source.exists()) {
            runCatching { source.renameTo(File(trashDir, record.fileName)) }
        }
        // 对话被删空后标题回到默认，下一次生图会按新的第一张重新起名
        if (_records.none { it.conversationId == record.conversationId }) {
            val conversationIndex = _conversations.indexOfFirst { it.id == record.conversationId }
            if (conversationIndex >= 0 && _conversations[conversationIndex].title != Conversation.DEFAULT_TITLE) {
                _conversations[conversationIndex] =
                    _conversations[conversationIndex].copy(title = Conversation.DEFAULT_TITLE)
                persistConversations()
            }
        }
        persist()
        return record
    }

    /** 撤销删除：把文件从 trash 移回，记录重新入索引。 */
    @Synchronized
    fun restore(record: ImageRecord): Boolean {
        val trashed = File(trashDir, record.fileName)
        val target = File(imagesDir, record.fileName)
        if (!trashed.exists()) return false
        if (!trashed.renameTo(target)) return false
        if (_records.none { it.id == record.id }) {
            _records.add(0, record)
            // 撤销的是最后一张时，标题已在删除时重置，这里按该对话最早的一张补回来
            val conversationIndex = _conversations.indexOfFirst { it.id == record.conversationId }
            if (conversationIndex >= 0 && _conversations[conversationIndex].title == Conversation.DEFAULT_TITLE) {
                val earliest = _records
                    .filter { it.conversationId == record.conversationId }
                    .minByOrNull { it.createdAt }
                if (earliest != null) {
                    _conversations[conversationIndex] = _conversations[conversationIndex].copy(
                        title = Conversation.titleFromPrompt(earliest.prompt),
                    )
                    persistConversations()
                }
            }
            persist()
        }
        return true
    }

    @Synchronized
    fun clearAll(): Int {
        val count = _records.size
        _records.forEach { record ->
            val source = File(imagesDir, record.fileName)
            if (source.exists()) runCatching { source.renameTo(File(trashDir, record.fileName)) }
        }
        _records.clear()
        persist()
        return count
    }

    /**
     * 启动时对账：
     * 1. 索引里有记录但文件没了 → 丢弃记录；
     * 2. 目录里有文件但索引没记录（异常退出留下的）→ 删掉；
     * 3. trash 里超过 [TRASH_KEEP_DAYS] 天的 → 删掉。
     */
    fun reconcile() {
        val known = _records.map { it.fileName }.toSet()
        var changed = false

        val missing = _records.filterNot { File(imagesDir, it.fileName).exists() }
        if (missing.isNotEmpty()) {
            _records.removeAll(missing.toSet())
            changed = true
        }

        imagesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in known) {
                runCatching { file.delete() }
            }
        }

        val deadline = System.currentTimeMillis() - TRASH_KEEP_DAYS * 24 * 3600_000L
        trashDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline) {
                runCatching { file.delete() }
            }
        }

        if (changed) persist()
    }

    /** 旧数据没有对话概念：没归组的记录挂到「默认对话」；全新安装也先备好一个空对话。 */
    private fun migrate() {
        var changed = false
        if (_conversations.isEmpty()) {
            val legacy = _records.isNotEmpty()
            _conversations.add(
                Conversation(
                    id = UUID.randomUUID().toString(),
                    title = if (legacy) "默认对话" else Conversation.DEFAULT_TITLE,
                    createdAt = _records.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                )
            )
            changed = true
        }
        val fallback = _conversations.firstOrNull()
        if (fallback != null) {
            for (i in _records.indices) {
                val record = _records[i]
                val valid = record.conversationId.isNotBlank() &&
                    _conversations.any { it.id == record.conversationId }
                if (!valid) {
                    _records[i] = record.copy(conversationId = fallback.id)
                    changed = true
                }
            }
        }
        if (activeId == null || _conversations.none { it.id == activeId }) {
            activeId = _conversations.firstOrNull()?.id
            changed = true
        }
        if (changed) {
            persist()
            persistConversations()
        }
    }

    private fun loadIndex() {
        runCatching {
            if (!indexFile.baseFile.exists()) return
            val text = indexFile.readFully().toString(Charsets.UTF_8)
            if (text.isBlank()) return
            _records.clear()
            _records.addAll(ImageRecord.listFromJson(JSONArray(text)))
        }
    }

    private fun loadConversations() {
        runCatching {
            if (!conversationsFile.baseFile.exists()) return
            val text = conversationsFile.readFully().toString(Charsets.UTF_8)
            if (text.isBlank()) return
            val root = JSONObject(text)
            _conversations.clear()
            _conversations.addAll(Conversation.listFromJson(root.optJSONArray("list") ?: JSONArray()))
            activeId = root.optString("activeId").takeIf { it.isNotBlank() }
        }
    }

    private fun persist() {
        val array = JSONArray()
        _records.forEach { array.put(it.toJson()) }
        val stream = indexFile.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            indexFile.finishWrite(stream)
        } catch (throwable: Throwable) {
            indexFile.failWrite(stream)
            throw throwable
        }
    }

    private fun persistConversations() {
        val array = JSONArray()
        _conversations.forEach { array.put(it.toJson()) }
        val root = JSONObject()
            .put("activeId", activeId ?: "")
            .put("list", array)
        val stream = conversationsFile.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            conversationsFile.finishWrite(stream)
        } catch (throwable: Throwable) {
            conversationsFile.failWrite(stream)
            throw throwable
        }
    }

    private companion object {
        const val TRASH_KEEP_DAYS = 7
    }
}

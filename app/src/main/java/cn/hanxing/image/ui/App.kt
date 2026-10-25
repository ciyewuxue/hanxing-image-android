package cn.hanxing.image.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import cn.hanxing.image.AppViewModel
import cn.hanxing.image.BuildConfig
import cn.hanxing.image.ReferenceImage
import cn.hanxing.image.UiState
import cn.hanxing.image.data.Aspect
import cn.hanxing.image.data.Conversation
import cn.hanxing.image.data.ImageFormat
import cn.hanxing.image.data.ImageRecord
import cn.hanxing.image.data.ImageSizes
import cn.hanxing.image.data.Profile
import cn.hanxing.image.data.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HanxingTheme(settings = state.theme) {
        AppContent(state = state, viewModel = viewModel)
    }
}

@Composable
private fun AppContent(state: UiState, viewModel: AppViewModel) {
    // 提示改成屏幕中央的小卡片：底部 Snackbar 会压住输入区，而且跟现在的配色不搭
    LaunchedEffect(state.toast?.token) {
        val toast = state.toast ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (toast.undo) 4000 else 2200)
        viewModel.clearToast()
    }

    NotificationPermissionPrompt()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!state.ready) return@Surface
            when {
                state.profiles.isEmpty() -> ProfileEditor(
                    state = state,
                    initial = null,
                    firstRun = true,
                    onSave = viewModel::saveProfile,
                    onLoadModels = viewModel::loadModels,
                    onCancel = null,
                )

                state.settingsOpen -> SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                )

                else -> HomeScreen(state = state, viewModel = viewModel)
            }
        }
        // 首页的提示由 HomeScreen 自己贴着输入区上方画；设置页没有输入区，用这个底部版本
        if (state.profiles.isEmpty() || state.settingsOpen) {
            AppToast(
                toast = state.toast,
                onUndo = viewModel::undoDelete,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }
        }
    }
}

/** 居中提示卡：圆角、浅底色、轻阴影，带撤销时停留 4 秒。 */
@Composable
private fun AppToast(
    toast: cn.hanxing.image.Toast?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 退出动画期间 toast 已经被清空，这里留一份最后的文案，避免卡片先变空白再消失
    var lastShown by remember { mutableStateOf<cn.hanxing.image.Toast?>(null) }
    LaunchedEffect(toast) { if (toast != null) lastShown = toast }
    val shown = toast ?: lastShown

    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.94f, animationSpec = tween(160)),
        exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.96f, animationSpec = tween(130)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 18.dp,
                    end = if (shown?.undo == true) 6.dp else 18.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = shown?.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (shown?.undo == true) {
                    TextButton(onClick = onUndo) { Text("撤销") }
                }
            }
        }
    }
}

/**
 * 通知权限预说明：系统授权弹窗的文案由系统语言决定、应用改不了，
 * 所以先用中文说清楚为什么要这个权限，再拉起系统弹窗。
 */
@Composable
private fun NotificationPermissionPrompt() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    }

    if (!visible) return
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text("开启通知权限") },
        text = {
            Text(
                text = "后台运行时可通过通知栏查看生图进度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                visible = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("去开启") }
        },
        dismissButton = {
            TextButton(onClick = { visible = false }) { Text("暂不") }
        },
    )
}

/** 页面级外边距：窄屏收窄，平板放宽，避免小屏被写死的内边距挤扁。 */
@Composable
private fun pagePadding(vertical: Int = 16): PaddingValues {
    val width = LocalConfiguration.current.screenWidthDp
    val horizontal = when {
        width < 360 -> 16
        width < 600 -> 20
        else -> 32
    }
    return PaddingValues(horizontal = horizontal.dp, vertical = vertical.dp)
}

@Composable
private fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    // 返回键：编辑中退回设置列表，否则退回生图界面（而不是退出应用）
    BackHandler {
        if (creating || editing != null) {
            creating = false
            editing = null
        } else {
            viewModel.closeSettings()
        }
    }

    val draft = editing
    if (creating || draft != null) {
        ProfileEditor(
            state = state,
            initial = draft,
            firstRun = false,
            onSave = { profile ->
                viewModel.saveProfile(profile)
                editing = null
                creating = false
            },
            onLoadModels = viewModel::loadModels,
            onCancel = {
                editing = null
                creating = false
            },
        )
        return
    }

    val active = state.activeProfile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(pagePadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::closeSettings) { Text("完成") }
        }

        Text("配置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.profiles.forEach { profile ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = profile.id == active?.id,
                        onClick = { viewModel.selectProfile(profile.id) },
                    )
                    Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = profile.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { editing = profile; viewModel.clearModelError() }) { Text("编辑") }
                    TextButton(
                        onClick = { viewModel.deleteProfile(profile.id) },
                        enabled = state.profiles.size > 1,
                    ) {
                        Text(
                            text = "删除",
                            color = if (state.profiles.size > 1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        TextButton(onClick = { creating = true; viewModel.clearModelError() }) { Text("+ 新增配置") }
        Text(
            text = "每条配置各自记着自己的模型；切换后会自动重新读一次模型列表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Text("主题色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Accent.all.forEach { accent ->
                val selected = accent == state.theme.accent
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.darkPrimary)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.setAccent(accent) },
                    )
                    Text(
                        text = accent.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("深浅模式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.all.forEach { mode ->
                FilterChip(
                    selected = state.theme.mode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("图片格式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImageFormat.entries.forEach { format ->
                FilterChip(
                    selected = state.imageFormat == format,
                    onClick = { viewModel.setImageFormat(format) },
                    label = { Text(format.label) },
                )
            }
        }
        Text(
            text = state.imageFormat.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "格式只影响出图时编码一次，保存和相册都不再压缩。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "版本 ${BuildConfig.VERSION_NAME} · 作者 ciyewuxue",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun ProfileEditor(
    state: UiState,
    initial: Profile?,
    firstRun: Boolean,
    onSave: (Profile) -> Unit,
    onLoadModels: (Profile) -> Unit,
    onCancel: (() -> Unit)?,
) {
    val fallbackId = rememberSaveable { UUID.randomUUID().toString() }
    val id = initial?.id ?: fallbackId

    var name by rememberSaveable(id) {
        mutableStateOf(
            initial?.name ?: if (firstRun) "默认" else "配置 ${state.profiles.size + 1}"
        )
    }
    var baseUrl by rememberSaveable(id) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var apiKey by rememberSaveable(id) { mutableStateOf(initial?.apiKey.orEmpty()) }
    var model by rememberSaveable(id) { mutableStateOf(initial?.model.orEmpty()) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    // 只认「当前这个地址读回来的」模型：新建配置时不能把上一个配置的清单摆出来
    val models = if (state.modelsForUrl != null && state.modelsForUrl == baseUrl.trim().trimEnd('/')) {
        state.models
    } else {
        emptyList()
    }
    val draft = Profile(id = id, name = name, baseUrl = baseUrl, apiKey = apiKey, model = model)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(pagePadding(vertical = 20)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        firstRun -> "寒星 · 生图"
                        initial == null -> "新增配置"
                        else -> "编辑配置"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (firstRun) {
                    Text(
                        text = "填一次服务地址和 Key，之后打开就能画。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("服务地址") },
            placeholder = { Text("https://…/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型") },
            placeholder = { Text("填模型名，或从下面获取后点选") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onLoadModels(draft) },
                enabled = apiKey.isNotBlank() && !state.loadingModels,
            ) {
                Text(if (state.loadingModels) "获取中…" else "获取模型列表")
            }
        }
        if (models.isNotEmpty()) {
            Text(
                text = "点下面任一模型，就会填进输入框",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (models.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                models.forEach { item ->
                    FilterChip(
                        selected = item == model,
                        onClick = { model = item },
                        label = { Text(item) },
                    )
                }
            }
        }

        state.modelError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { onSave(draft) },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (firstRun) "保存并开始" else "保存")
        }

        Text(
            text = "Key 加密保存在你手机本地，换手机或卸载重装都需要重新输入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeScreen(state: UiState, viewModel: AppViewModel) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var ratioSetting by rememberSaveable { mutableStateOf(ImageSizes.AUTO) }
    var tierSetting by rememberSaveable { mutableStateOf(ImageSizes.AUTO) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }
    var preview by remember { mutableStateOf<ImageRecord?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val screen = LocalConfiguration.current
    // 抽屉按屏宽比例给宽度：窄屏不铺满，宽屏不无限拉伸
    val drawerWidth = (screen.screenWidthDp * 0.72f)
        .coerceIn(240f, 320f)
        .coerceAtMost(screen.screenWidthDp * 0.85f)
        .dp

    // 尺寸选择跟着对话走：换对话（含新建）就回到「自动」，不再沿用上一轮的选择
    LaunchedEffect(state.activeConversationId) {
        ratioSetting = ImageSizes.AUTO
        tierSetting = ImageSizes.AUTO
    }

    // 抽屉开着时按返回 = 收抽屉，而不是退出应用
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // 聊天式列表：reverseLayout 把「新的在前」倒过来画，index 0 就是屏幕最底部的那张。
    // 进对话、点生成、出图，都定位到 0——新内容永远在眼前，不用手动往下翻。
    val newestId = state.records.firstOrNull()?.id
    LaunchedEffect(state.activeConversationId, newestId, state.generating) {
        withFrameNanos { }
        listState.scrollToItem(0)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.usePickedReference(uri)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
            ) {
                ConversationDrawer(
                    conversations = state.conversations,
                    activeId = state.activeConversationId,
                    counts = state.conversationCounts,
                    generatingIds = state.generatingIds,
                    onNew = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { confirmDelete = it },
                )
            }
        },
    ) {
        // 平板 / 折叠屏展开时不把内容拉满一屏，居中限宽，手机上仍是全宽
        Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                Header(
                    subtitle = state.activeProfile?.name.orEmpty(),
                    models = state.models,
                    selectedModel = state.selectedModel.ifBlank { state.activeProfile?.model.orEmpty() },
                    generating = state.generating,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSelectModel = viewModel::selectModel,
                    onSettings = viewModel::openSettings,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val showEmpty = state.records.isEmpty() && !state.generating
                    if (showEmpty) {
                        // 空状态不再塞进固定高度的列表项：直接占满列表区，
                        // 放得下就居中，放不下就自己滚，避免底部被裁掉还滑不回来。
                        EmptyState(onPick = { prompt = it }, modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            // 聊天式：数据仍是「新的在前」，靠 reverseLayout 把它倒过来画——
                            // 第一项贴着底部，越往上越旧。好处是停在底部时新卡出现会自动顶上来，
                            // 图片异步加载改变行高也不会把位置带偏。
                            reverseLayout = true,
                        ) {
                            if (state.generating) {
                                item { GeneratingCard(state = state, onStop = viewModel::stop) }
                            }
                            items(state.records, key = { it.id }) { record ->
                                ImageCard(
                                    record = record,
                                    file = viewModel.fileFor(record),
                                    onPreview = { preview = record },
                                    onEdit = { viewModel.useAsReference(record) },
                                    onSave = { viewModel.saveToGallery(record) },
                                    onRetry = { viewModel.retry(record) },
                                    onDelete = { viewModel.delete(record) },
                                )
                            }
                        }
                    }
                    // 提示贴着输入区上方浮动，不挡图片主体
                    AppToast(
                        toast = state.toast,
                        onUndo = viewModel::undoDelete,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    )
                }

                Composer(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    sizeLabel = ImageSizes.selectionLabel(ratioSetting, tierSetting),
                    reference = state.reference,
                    onPickReference = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClearReference = viewModel::clearReference,
                    generating = state.generating,
                    failure = state.lastFailure,
                    onDismissFailure = viewModel::dismissFailure,
                    onOpenSize = { showSizeDialog = true },
                    onGenerate = {
                        keyboardController?.hide()
                        viewModel.generate(prompt, ratioSetting, tierSetting)
                        prompt = ""
                    },
                    onStop = viewModel::stop,
                )
            }
        }
    }

    preview?.let { record ->
        ImagePreviewDialog(
            file = viewModel.fileFor(record),
            prompt = record.prompt,
            onDismiss = { preview = null },
        )
    }

    confirmDelete?.let { conversation ->
        val count = state.conversationCounts[conversation.id] ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除对话？") },
            text = {
                Text("「${conversation.title}」里的 $count 张图会一并删除；未保存的图片在对话删除后无法找回。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conversation.id)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }

    if (showSizeDialog) {
        SizeDialog(
            ratioSetting = ratioSetting,
            tierSetting = tierSetting,
            prompt = prompt,
            onDismiss = { showSizeDialog = false },
            onConfirm = { nextRatio, nextTier ->
                ratioSetting = nextRatio
                tierSetting = nextTier
                showSizeDialog = false
            },
        )
    }
}

/** 左侧抽屉：对话列表。新对话首张图的提示词会自动变成标题。 */
@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    counts: Map<String, Int>,
    generatingIds: Set<String>,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("对话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新建对话")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(conversations, key = { it.id }) { conversation ->
                val active = conversation.id == activeId
                Surface(
                    onClick = { onSelect(conversation.id) },
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${counts[conversation.id] ?: 0} 张图",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (conversation.id in generatingIds) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "生成中",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onDelete(conversation) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除对话",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：菜单 +「寒星 · 生图」；第二行只报当前配置名，紧跟一个模型下拉——
 * 「用的是哪套 Key」和「这次用哪个模型」连成一句话读完。右上角只留设置键。
 */
@Composable
private fun Header(
    subtitle: String,
    models: List<String>,
    selectedModel: String,
    generating: Boolean,
    onMenu: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    // 模型名最多占屏宽 42%：窄屏不把配置名挤没，宽屏也不留一块空白
    val chipMaxWidth = (LocalConfiguration.current.screenWidthDp * 0.42f).dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Default.Menu, contentDescription = "对话列表")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "寒星 · 生图",
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (models.isNotEmpty()) {
                    Box {
                        // 不用 Surface：Material 会给可点击组件塞一个 48dp 的最小点击区，
                        // 视觉上 chip 只有 26dp，上下多出来的透明高度把标题顶出一大段空白。
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !generating) { modelMenuOpen = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedModel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = chipMaxWidth),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                        ) {
                            models.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = item,
                                            fontWeight = if (item == selectedModel) FontWeight.SemiBold else null,
                                        )
                                    },
                                    onClick = {
                                        onSelectModel(item)
                                        modelMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun EmptyState(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // 361dp 宽、常规字号下这块内容约 488dp，列表区约 497dp，正好放得下；
        // 再矮或字体放大就切紧凑档，紧凑档仍放不下时交给纵向滚动，保证永远不会被裁掉半张卡。
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.4f)
        val compact = maxHeight < 470.dp * fontScale
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp)
                .padding(
                    top = if (compact) 20.dp else 36.dp,
                    bottom = if (compact) 16.dp else 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = if (compact) 52.dp else 64.dp)
            Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
            Text(
                text = "说一句话，画一张图",
                fontSize = if (compact) 22.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 28.sp else 30.sp,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            Text(
                text = "比例和清晰度可以在下面的「尺寸」里选，\n也可以直接写在提示词里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            ) {
                EXAMPLE_PROMPTS.forEach { example ->
                    Surface(
                        onClick = { onPick(example) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = if (compact) 12.dp else 14.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 新对话首页的例子：点一下就填进输入框。 */
private val EXAMPLE_PROMPTS = listOf(
    "一只戴围巾的猫站在雪地里",
    "黄昏的海边，浪拍在礁石上",
    "极简产品海报：大面积留白，浅灰背景，正中一只白色马克杯",
    "水墨风格江南小镇，清晨薄雾，远山，横幅",
)

@Composable
private fun ImageCard(
    record: ImageRecord,
    file: File,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap = rememberBitmap(file)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = record.prompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPreview),
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("图片不在了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = record.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.pixelLabel} · ${record.seconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (record.basedOnReference) {
                        Text(
                            text = " · 基于参考图",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (record.savedToGallery) {
                        Text(
                            text = "已保存",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                // 四个操作等分一行：窄屏 + 大字号下按比例收窄，不会挤出屏幕
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardAction(
                        text = if (record.savedToGallery) "已保存" else "保存",
                        enabled = !record.savedToGallery,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                    CardAction(text = "再编辑", onClick = onEdit, modifier = Modifier.weight(1f))
                    CardAction(text = "重试", onClick = onRetry, modifier = Modifier.weight(1f))
                    CardAction(
                        text = "删除",
                        onClick = onDelete,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * 全屏预览：黑底、等比铺满，双指缩放 1–6 倍，放大后可拖动；
 * 点右上角关闭，返回键同样关闭。提示词只在 1 倍时显示，放大时不挡图。
 */
@Composable
private fun ImagePreviewDialog(
    file: File,
    prompt: String,
    onDismiss: () -> Unit,
) {
    val bitmap = rememberBitmap(file, maxEdge = 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
            val boxWidth = constraints.maxWidth.toFloat()
            val boxHeight = constraints.maxHeight.toFloat()

            if (bitmap != null) {
                val aspect = bitmap.width.toFloat() / bitmap.height
                // 与 ContentScale.Fit 一致：算出图片实际显示尺寸，用于限制拖动范围
                val shownWidth = if (boxWidth / boxHeight > aspect) boxHeight * aspect else boxWidth
                val shownHeight = shownWidth / aspect

                fun clampToBounds(candidate: Offset, nextScale: Float): Offset {
                    val maxX = ((shownWidth * nextScale - boxWidth) / 2f).coerceAtLeast(0f)
                    val maxY = ((shownHeight * nextScale - boxHeight) / 2f).coerceAtLeast(0f)
                    return Offset(
                        candidate.x.coerceIn(-maxX, maxX),
                        candidate.y.coerceIn(-maxY, maxY),
                    )
                }

                Image(
                    bitmap = bitmap,
                    contentDescription = prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        // 双击放大到 3 倍（以点的位置为锚点），再双击还原
                        .pointerInput(bitmap) {
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    val target = if (scale > 1.001f) 1f else 3f
                                    val targetOffset = if (target <= 1.001f) Offset.Zero else clampToBounds(
                                        Offset(
                                            (boxWidth / 2f - tap.x) * (target - 1f),
                                            (boxHeight / 2f - tap.y) * (target - 1f),
                                        ),
                                        target,
                                    )
                                    scope.launch {
                                        val fromScale = scale
                                        val fromOffset = offset
                                        animate(0f, 1f, animationSpec = tween(180)) { progress, _ ->
                                            scale = fromScale + (target - fromScale) * progress
                                            offset = Offset(
                                                fromOffset.x + (targetOffset.x - fromOffset.x) * progress,
                                                fromOffset.y + (targetOffset.y - fromOffset.y) * progress,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(1f, 6f)
                                scale = next
                                offset = if (next <= 1.001f) Offset.Zero else clampToBounds(offset + pan, next)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (scale > 1.001f) {
                    Text(
                        text = "双击还原 · 拖动查看",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
                }
            }

            if (scale <= 1.001f && prompt.isNotBlank()) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun GeneratingCard(state: UiState, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在生成…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatElapsed(state.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = progressLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { (state.received.toFloat() / state.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = onStop) { Text("停止") }
        }
    }
}

@Composable
private fun Composer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    sizeLabel: String,
    reference: ReferenceImage?,
    onPickReference: () -> Unit,
    onClearReference: () -> Unit,
    generating: Boolean,
    failure: String?,
    onDismissFailure: () -> Unit,
    onOpenSize: () -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
) {
    // 键盘被返回键收起后，输入框仍持有焦点，系统会记住「用户主动隐藏」而拒绝再次弹出。
    // 这里在键盘收起时主动清掉焦点：下次点击就是一次全新的「首次获得焦点」，输入法一定拉起。
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // 这里只接管返回键，不再「一看不见键盘就清焦点」。
    // 微信输入法的窗口上报 inset 偏慢，第一帧就被清焦点会让它断开、留下一个空白键盘窗；
    // 清焦点只在用户按返回收起键盘时做，正好覆盖「收起后再点不弹」的场景。
    BackHandler(enabled = imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (reference != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val thumbnail = rememberBitmap(reference.file)
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = "参考图",
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        text = "参考图" + (reference.pixelLabel?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearReference) { Text("移除") }
                }
            }
            if (failure != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        // 服务端原文可能很长，最多三行，别把输入区顶没
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissFailure) { Text("知道了") }
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (reference != null) "想怎么改？" else "说一句话，画一张图") },
                minLines = 2,
                maxLines = 5,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 尺寸按钮吃掉剩余宽度：机型再窄、字号再大也只收窄文字，不会把整行挤出屏幕
                TextButton(
                    onClick = onOpenSize,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "尺寸：$sizeLabel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onPickReference,
                    enabled = !generating,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(if (reference == null) "上传图片" else "换一张", maxLines = 1)
                }
                if (generating) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("停止") }
                } else {
                    Button(onClick = onGenerate, enabled = prompt.isNotBlank()) {
                        Text(if (reference != null) "改图" else "生成")
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeDialog(
    ratioSetting: String,
    tierSetting: String,
    prompt: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var nextRatio by remember { mutableStateOf(ratioSetting) }
    var nextTier by remember { mutableStateOf(tierSetting) }
    val resolved = ImageSizes.resolve(nextRatio, nextTier, prompt)

    val ratioOptions = listOf(ImageSizes.AUTO) + Aspect.all.map { it.label }
    val tierOptions = listOf(ImageSizes.AUTO) + Tier.all.map { it.label }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("尺寸")
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "4K 价格高、耗时长，非必要不建议选择",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 3.dp).weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("比例", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ratioOptions.forEach { item ->
                        FilterChip(
                            selected = item == nextRatio,
                            onClick = { nextRatio = item },
                            label = { Text(item) },
                        )
                    }
                }
                Text("清晰度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tierOptions.forEach { item ->
                        FilterChip(
                            selected = item == nextTier,
                            onClick = { nextTier = item },
                            label = { Text(item) },
                        )
                    }
                }
                Text(
                    text = "将请求 ${resolved.pixelLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nextRatio, nextTier) }) { Text("好") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun rememberBitmap(file: File, maxEdge: Int = 1280): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path, file.lastModified(), maxEdge) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                val width = maxOf(bounds.outWidth, bounds.outHeight)
                while (width > 0 && width / sample > maxEdge) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private fun formatElapsed(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun progressLine(state: UiState): String {
    if (state.received <= 0) return "图片生成中，请稍候…"
    val seconds = state.elapsedMs / 1000.0
    val speed = if (seconds > 0.5) " · ${formatBytes((state.received / seconds).toLong())}/s" else ""
    val total = if (state.total > 0) " / ${formatBytes(state.total)}" else ""
    return "生图完成，已接收 ${formatBytes(state.received)}" + total + speed
}

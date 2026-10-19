package com.example.sudokumaster.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.domain.model.FontScale
import com.example.sudokumaster.domain.model.HighlightColor
import com.example.sudokumaster.domain.model.HintMode
import com.example.sudokumaster.domain.model.Palette
import com.example.sudokumaster.domain.model.ThemeMode
import com.example.sudokumaster.ui.components.ModuleCard
import com.example.sudokumaster.ui.components.SegmentedControl
import com.example.sudokumaster.ui.components.SwitchRow
import com.example.sudokumaster.viewmodel.SettingsViewModel
import com.example.sudokumaster.viewmodel.vmFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPlayImported: (sizeId: String, diffId: Int, puzzle: String, solution: String?) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SudokuApplication
    val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = vmFactory { SettingsViewModel(app.container) }
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showClear by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showRestore by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    // 真实版本号（随构建自动更新，避免写死显示不全）
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) { delay(1600); toast = null }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ModuleCard(title = "外观") {
                SegmentedControl(
                    items = listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色"),
                    selected = settings.theme.name,
                    onSelect = { vm.update { copy(theme = ThemeMode.valueOf(it)) } }
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("BLUE" to "蓝", "GREEN" to "绿", "GRAY" to "灰"),
                    selected = settings.palette.name,
                    onSelect = { vm.update { copy(palette = Palette.valueOf(it)) } }
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("STANDARD" to "标准字", "LARGE" to "大号字"),
                    selected = settings.fontScale.name,
                    onSelect = { vm.update { copy(fontScale = FontScale.valueOf(it)) } }
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("BLUE" to "蓝", "GREEN" to "绿", "PURPLE" to "紫"),
                    selected = settings.highlightColor.name,
                    onSelect = { vm.update { copy(highlightColor = HighlightColor.valueOf(it)) } }
                )
            }

            Spacer(Modifier.height(14.dp))

            ModuleCard(title = "玩法") {
                SegmentedControl(
                    items = listOf("FILL" to "提示直接填答案", "CANDIDATE" to "提示仅给候选数"),
                    selected = settings.hintMode.name,
                    onSelect = { vm.update { copy(hintMode = HintMode.valueOf(it)) } }
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("长按切换笔记", "长按格子快速开关笔记模式", settings.longPressNote) {
                    vm.update { copy(longPressNote = it) }
                }
                Divider()
                SwitchRow("错误标记", "填入与解冲突的数字时标红", settings.showError) {
                    vm.update { copy(showError = it) }
                }
                Divider()
                SwitchRow("同值高亮", "高亮与选中格相同的数字", settings.highlightSame) {
                    vm.update { copy(highlightSame = it) }
                }
                Divider()
                SwitchRow("候选数自动排除", "填入数字后自动清除同行列宫的该候选", settings.autoExclude) {
                    vm.update { copy(autoExclude = it) }
                }
                Divider()
                SwitchRow("自动笔记按钮", "在工具栏显示一键笔记按钮", settings.autoNote) {
                    vm.update { copy(autoNote = it) }
                }
                Divider()
                SwitchRow("快速生成", "低端设备提速（题目略易）", settings.fastGen) {
                    vm.update { copy(fastGen = it) }
                }
            }

            Spacer(Modifier.height(14.dp))

            ModuleCard(title = "反馈") {
                SwitchRow("震动反馈", "操作时轻微震动", settings.vibrate) {
                    vm.update { copy(vibrate = it) }
                }
                Divider()
                SwitchRow("提示音", "操作时轻响一声", settings.sound) {
                    vm.update { copy(sound = it) }
                }
            }

            Spacer(Modifier.height(14.dp))

            ModuleCard(title = "数据") {
                SettingsClickRow("导入题目", "粘贴题目 JSON（缺解时自动求解），立即开局") {
                    showImport = true
                }
                Divider()
                SettingsClickRow("备份全部数据", "历史记录与进行中进度存为 JSON（复制到剪贴板）") {
                    scope.launch {
                        val backup = vm.exportBackup()
                        clipboard.setText(AnnotatedString(backup.json))
                        toast = "备份已复制到剪贴板（${backup.recordCount} 条记录）"
                    }
                }
                Divider()
                SettingsClickRow("恢复数据", "从备份 JSON 恢复（覆盖现有记录与进度）") {
                    showRestore = true
                }
                Divider()
                SettingsClickRow("导出题目", "最近一条题目存为 JSON（复制到剪贴板）") {
                    scope.launch {
                        val json = vm.exportJson()
                        if (json != null) {
                            clipboard.setText(AnnotatedString(json))
                            toast = "题目 JSON 已复制到剪贴板"
                        } else toast = "暂无可导出的题目"
                    }
                }
                Divider()
                SettingsClickRow("清除所有游戏记录", "清空成绩与历史，保留题库") {
                    showClear = true
                }
            }

            Spacer(Modifier.height(14.dp))

            ModuleCard(title = "关于") {
                SettingsClickRow("隐私声明", "单机离线 · 不收集任何信息") {
                    showAbout = true
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "版本 数独大师 V$versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "本应用不请求网络，不上传任何用户数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除记录？") },
            text = { Text("将删除所有成绩与历史记录，内置题目保留。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllRecords()
                    showClear = false
                    toast = "记录已清除"
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("隐私声明") },
            text = {
                Text("本应用为纯单机离线应用，不会访问互联网，不收集、不上传任何用户个人信息。" +
                    "游戏进度、设置、成绩仅保存在手机本地私有目录；卸载应用后本地数据自动清除。" +
                    "应用不申请通讯录、位置、存储、网络等任何敏感权限。")
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("我了解了") }
            }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入题目") },
            text = {
                Column {
                    Text(
                        "粘贴题目 JSON（需含 size 与 puzzle；solution 可选，缺失将自动求解）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        placeholder = { Text("{\"size\":\"9\",\"puzzle\":\"0030...\"}") },
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val parsed = vm.parsePuzzleImport(importText)
                        if (parsed == null) {
                            toast = "JSON 格式有误或题面不合法"
                        } else {
                            showImport = false
                            onPlayImported(parsed.sizeId, parsed.diffId, parsed.puzzle, parsed.solution)
                        }
                    }
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            }
        )
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("恢复数据") },
            text = {
                Column {
                    Text(
                        "粘贴此前「备份全部数据」得到的 JSON。恢复将覆盖当前所有历史记录与进行中进度，此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreText,
                        onValueChange = { restoreText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        placeholder = { Text("{\"type\":\"backup\",\"records\":[...]}") },
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        toast = vm.importBackup(restoreText)
                        showRestore = false
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) { Text("取消") }
            }
        )
    }

    toast?.let { msg ->
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(12.dp)
        ) { Text(msg) }
    }
}

@Composable
private fun SettingsClickRow(label: String, desc: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.example.sudokumaster.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.fx.CelebrationController
import com.example.sudokumaster.ui.fx.WinCelebrationOverlay
import com.example.sudokumaster.ui.theme.LocalAppColors
import com.example.sudokumaster.util.Feedback
import com.example.sudokumaster.util.fmtTime
import com.example.sudokumaster.viewmodel.GameViewModel
import com.example.sudokumaster.viewmodel.vmFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    sizeId: SizeId,
    diffId: Int,
    mode: String,               // "new" | "resume" | "replay" | "import"
    replayRecordId: Long,
    importedPuzzle: String = "",        // mode=import：题面数字串
    importedSolution: String? = null,   // mode=import：解（可空，自动求解）
    onBack: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.sudokumaster.SudokuApplication
    val vm: GameViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = vmFactory { GameViewModel(app.container) }
    )

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settingsFlow.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val winInfo by vm.winInfo.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val hintsLeft by vm.hintsLeft.collectAsStateWithLifecycle()

    // 通关庆祝控制器（音频生命周期与界面共存亡）
    val celebration = remember { CelebrationController() }
    DisposableEffect(Unit) { onDispose { celebration.release() } }

    // 首次进入：按模式开局 / 恢复 / 复玩 / 导入
    LaunchedEffect(mode, sizeId, diffId, replayRecordId, importedPuzzle) {
        when (mode) {
            "new" -> vm.startNew(sizeId, diffId)
            "resume" -> vm.resumeFromSaved()
            "replay" -> vm.replayFromRecord(replayRecordId)
            "import" -> vm.startImported(sizeId, diffId, importedPuzzle, importedSolution)
        }
    }

    // 计时刷新（4Hz），暂停时不刷新
    val active = uiState != null && uiState?.isWon == false && uiState?.isPaused == false
    LaunchedEffect(active) {
        while (active) {
            vm.refreshTimer()
            delay(250)
        }
    }

    // 前后台暂停 / 恢复
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause(); vm.saveCurrent(); celebration.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumeTimer() }

    // Toast 自动消失
    LaunchedEffect(toast) {
        if (toast != null) { delay(1600); vm.consumeToast() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState?.title ?: "数独大师", fontWeight = FontWeight.Bold)
                        Text(
                            fmtTime(uiState?.elapsedMs ?: 0L),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.exitToHome(); onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val paused = uiState?.isPaused == true
                    IconButton(onClick = { vm.togglePause() }) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "继续" else "暂停"
                        )
                    }
                }
            )
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            uiState?.let { state ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isNoteMode) {
                        NoteModeBanner()
                        Spacer(Modifier.height(6.dp))
                    }

                    SudokuBoard(
                        n = state.n,
                        boxRows = state.boxRows,
                        boxCols = state.boxCols,
                        cells = state.cells,
                        onCellClick = { vm.select(it) },
                        onCellLongClick = {
                            if (settings.longPressNote) {
                                vm.toggleNote()
                                Feedback.vibrate(context, settings.vibrate)
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    NumberPad(
                        n = state.n,
                        countsLeft = state.digitCountsLeft,
                        isNoteMode = state.isNoteMode,
                        onDigit = {
                            vm.input(it)
                            Feedback.vibrate(context, settings.vibrate)
                            Feedback.beep(settings.sound)
                        }
                    )

                    Spacer(Modifier.height(6.dp))

                    // 笔记开关 + 擦除
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PadAction(
                            text = if (state.isNoteMode) "笔记：开" else "笔记：关",
                            icon = Icons.Filled.EditNote,
                            active = state.isNoteMode,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.toggleNote()
                                Feedback.vibrate(context, settings.vibrate)
                            }
                        )
                        PadAction(
                            text = "擦除",
                            icon = Icons.Filled.Backspace,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.erase()
                                Feedback.vibrate(context, settings.vibrate)
                            }
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // 工具栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ToolButton("撤销", Icons.Filled.Undo, state.canUndo, Modifier.weight(1f)) { vm.undo() }
                        ToolButton("重做", Icons.Filled.Redo, state.canRedo, Modifier.weight(1f)) { vm.redo() }
                        ToolButton(
                            text = if (hintsLeft > 0) "提示 $hintsLeft" else "提示 0",
                            icon = Icons.Filled.Lightbulb,
                            enabled = hintsLeft > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            vm.hint()
                            Feedback.vibrate(context, settings.vibrate)
                        }
                        if (settings.autoNote) {
                            ToolButton("笔记", Icons.Filled.EditNote, true, Modifier.weight(1f)) { vm.autoNotes() }
                        }
                        ToolButton("重置", Icons.Filled.Refresh, true, Modifier.weight(1f)) { vm.reset() }
                    }
                }
            }

            // 生成中
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("正在生成题目…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 暂停遮罩：冻结计时，点击任意处或「继续游戏」恢复
            if (uiState?.isPaused == true && uiState?.isWon != true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable { vm.togglePause() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "已暂停",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.togglePause() }) { Text("继续游戏") }
                    }
                }
            }

            // Toast
            toast?.let { msg ->
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // 通关庆祝界面（居中面板 + 礼花 + 角色庆祝 + 音频）
    winInfo?.let { info ->
        WinCelebrationOverlay(
            info = info,
            config = celebration.config,
            soundEnabled = settings.sound,
            controller = celebration,
            onReplay = {
                vm.dismissWin()
                val s = vm.uiState.value
                vm.startNew(s?.sizeId ?: sizeId, s?.difficulty?.id ?: diffId)
            },
            onHome = {
                vm.dismissWin()
                onNavigateHome()
            },
            onDismiss = { vm.dismissWin() }
        )
    }
}

@Composable
private fun NoteModeBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "笔记模式：点按数字记录候选数",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun NumberPad(
    n: Int,
    countsLeft: List<Int>,
    isNoteMode: Boolean,
    onDigit: (Int) -> Unit
) {
    val cols = if (n <= 6) n else 5
    val appColors = LocalAppColors.current
    val digits = (1..n).toList()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var idx = 0
        while (idx < digits.size) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until cols) {
                    val d = digits.getOrNull(idx)
                    idx++
                    if (d != null) {
                        val left = countsLeft.getOrElse(d) { 0 }
                        if (left > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(if (n <= 6) 1f else 0.9f)
                                    .background(
                                        if (isNoteMode) appColors.primarySoft
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onDigit(d) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = d.toString(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = left.toString(),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // 该数字已全部填满：整键留空隐藏
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PadAction(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (active) {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = appColors.primarySoft
            )
        } else {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        }
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1)
    }
}

@Composable
private fun ToolButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

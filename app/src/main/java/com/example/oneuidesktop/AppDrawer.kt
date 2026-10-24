package com.example.oneuidesktop

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One UI 风格应用抽屉：横向分页 + 搜索 + 长按拖动排序（任何状态下长按即可拖动，
 * 拖到屏幕左右边缘自动翻页；最后一页右边缘移到末尾并自动生成新页）。
 *
 * 「整理应用」编辑模式保留：顶部提示 + 点选移动（点 A 再点 B）+ 完成保存。
 * 拖放式排序：拖起浮动图标 → 松手落位并持久化；不做实时重排（规避手势销毁问题）。
 */
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    customOrder: Boolean,
    showLabels: Boolean,
    onSetCustomOrder: (Boolean) -> Unit,
    onCommit: (List<AppInfo>) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var list by remember { mutableStateOf(apps) }
    LaunchedEffect(apps) { list = apps }

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    var dragging by remember { mutableStateOf<AppInfo?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cellCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var lastFlipAt by remember { mutableStateOf(0L) }

    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val spec = remember(isLandscape) { gridSpec(isLandscape) }

    // 编辑模式：点选移动（点 A 选中，点 B 移到 B 的位置）
    fun handleEditTap(app: AppInfo) {
        val cur = selected
        if (cur == null) {
            selected = app
        } else if (cur.key == app.key) {
            selected = null
        } else {
            val from = list.indexOf(cur)
            val to = list.indexOf(app)
            if (from >= 0 && to >= 0 && from != to) {
                list = list.toMutableList().apply { add(to, removeAt(from)) }
                onCommit(list)
            }
            selected = null
        }
    }

    val searchActive = query.isNotBlank() && !editing
    val visible = if (searchActive) {
        list.filter { it.label.contains(query, ignoreCase = true) }
    } else {
        list
    }
    val pagerState = rememberPagerState(pageCount = {
        if (searchActive) 1 else (list.size + spec.perPage - 1) / spec.perPage
    })

    /** 拖动中的边缘处理：左右自动翻页；最后一页右边缘移到末尾（自动产生新页） */
    fun handleMove(dragged: AppInfo, pos: Offset) {
        if (gridSize == IntSize.Zero) return
        val padH = with(density) { 16.dp.toPx() }
        val edge = with(density) { 48.dp.toPx() }
        val page = pagerState.currentPage
        val now = System.currentTimeMillis()
        if (now - lastFlipAt <= 450) return
        when {
            pos.x < padH + edge && page > 0 -> {
                lastFlipAt = now
                scope.launch { pagerState.animateScrollToPage(page - 1) }
            }
            pos.x > gridSize.width - padH - edge && page < pagerState.pageCount - 1 -> {
                lastFlipAt = now
                scope.launch { pagerState.animateScrollToPage(page + 1) }
            }
            pos.x > gridSize.width - padH - edge -> {
                // 最后一页右边缘：移到列表末尾；页满时会自然生成新页，跟随过去
                lastFlipAt = now
                val from = list.indexOf(dragged)
                if (from in 0 until (list.size - 1)) {
                    list = list.toMutableList().apply { add(list.size - 1, removeAt(from)) }
                    val targetPage = (list.size - 1) / spec.perPage
                    if (targetPage > pagerState.currentPage) {
                        scope.launch {
                            snapshotFlow { pagerState.pageCount }
                                .first { it - 1 >= targetPage }
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            }
        }
    }

    fun dropAt(dragged: AppInfo, pos: Offset) {
        if (gridSize == IntSize.Zero) return
        // 翻页动画中松手：用落定目标页计算
        val page = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        val padH = with(density) { 16.dp.toPx() }
        val padV = with(density) { 10.dp.toPx() }
        val cellW = (gridSize.width - 2 * padH) / spec.cols
        val cellH = (gridSize.height - 2 * padV) / spec.rows
        val current = list.indexOf(dragged)
        if (current < 0 || cellW <= 0 || cellH <= 0) return
        val col = ((pos.x - padH) / cellW).toInt().coerceIn(0, spec.cols - 1)
        val row = ((pos.y - padV) / cellH).toInt().coerceIn(0, spec.rows - 1)
        val target = (page * spec.perPage + row * spec.cols + col)
            .coerceIn(0, list.size - 1)
        if (target != current) {
            list = list.toMutableList().apply { add(target, removeAt(current)) }
        }
        onCommit(list)
        if (!customOrder) onSetCustomOrder(true)
    }

    val startDrag: (AppInfo, Offset) -> Unit = { app, offsetInCell ->
        val gc = gridCoords
        val cc = cellCoords[app.key]
        if (dragging == null && gc != null && cc != null) {
            dragging = app
            dragPos = cc.positionInRoot() - gc.positionInRoot() + offsetInCell
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Text(
                    selected?.let { app -> "已选中【${app.label}】，点击目标图标移动到该位置" }
                        ?: "长按拖动排序，或点选图标后再点目标位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onCommit(list)
                    editing = false
                }) {
                    Text("完成")
                }
            } else {
                SearchField(query = query, onQuery = { query = it }, modifier = Modifier.weight(1f))
                SortMenu(
                    customOrder = customOrder,
                    onSelect = onSetCustomOrder,
                    onOpenSettings = onOpenSettings,
                    onEdit = {
                        editing = true
                        onSetCustomOrder(true)
                    },
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    gridCoords = it
                    gridSize = it.size
                },
        ) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchActive) "没有找到匹配的应用" else "暂无应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (searchActive) {
                GridPage(
                    pageApps = visible.take(spec.perPage),
                    cols = spec.cols,
                    rows = spec.rows,
                    dragEnabled = false,
                    showLabels = showLabels,
                    onLaunch = onLaunch,
                    onCellPosition = { key, coords -> cellCoords[key] = coords },
                    onStartDrag = startDrag,
                    onDragMove = {},
                    onDragEnd = {},
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = dragging == null,
                ) { page ->
                    val from = page * spec.perPage
                    val tapHandler: (AppInfo) -> Unit = if (editing) {
                        { app -> handleEditTap(app) }
                    } else {
                        onLaunch
                    }
                    GridPage(
                        pageApps = list.subList(from, minOf(from + spec.perPage, list.size)),
                        cols = spec.cols,
                        rows = spec.rows,
                        dragEnabled = true,
                        showLabels = showLabels,
                        onLaunch = tapHandler,
                        onCellPosition = { key, coords -> cellCoords[key] = coords },
                        onStartDrag = startDrag,
                        onDragMove = { amount ->
                            val d = dragging
                            dragPos += amount
                            if (d != null) handleMove(d, dragPos)
                        },
                        onDragEnd = {
                            val dragged = dragging
                            val pos = dragPos
                            dragging = null
                            if (dragged != null) dropAt(dragged, pos)
                        },
                    )
                }
            }

            // 跟随手指的浮动图标
            dragging?.let { app ->
                val iconHalf = with(density) { 28.dp.toPx() }
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPos.x - iconHalf).roundToInt(),
                                (dragPos.y - iconHalf - with(density) { 18.dp.toPx() }).roundToInt(),
                            )
                        }
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = 1.25f
                            scaleY = 1.25f
                            alpha = 0.95f
                        },
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp),
        ) {
            PageDots(
                count = pagerState.pageCount,
                active = pagerState.currentPage,
                darkText = true,
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun GridPage(
    pageApps: List<AppInfo>,
    cols: Int,
    rows: Int,
    dragEnabled: Boolean,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onCellPosition: (String, LayoutCoordinates) -> Unit,
    onStartDrag: (AppInfo, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    // 嵌套网格（与主屏一致，长按手势可被可靠触发）
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        for (row in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until cols) {
                    val app = pageApps.getOrNull(row * cols + col)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (app != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { onCellPosition(app.key, it) }
                                    .then(
                                        if (dragEnabled) {
                                            Modifier.pointerInput(app.key) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        onStartDrag(app, offset)
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        onDragMove(amount)
                                                    },
                                                    onDragEnd = onDragEnd,
                                                    onDragCancel = onDragEnd,
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIconCell(
                                    app = app,
                                    onLaunch = onLaunch,
                                    darkText = true,
                                    showLabel = showLabels,
                                    dim = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.height(56.dp),
        singleLine = true,
        placeholder = { Text("搜索") },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SortMenu(
    customOrder: Boolean,
    onSelect: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "排序设置")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("整理应用（拖动排序）") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("字母顺序") },
                leadingIcon = { RadioButton(selected = !customOrder, onClick = null) },
                onClick = {
                    onSelect(false)
                    expanded = false
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("桌面设置") },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}

package com.example.oneuidesktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/**
 * One UI 主屏：时钟 + 图标网格（长按拖动排序 / 拖到图标上建文件夹）+ Dock。
 */
@Composable
fun HomeScreen(
    appsByKey: Map<String, AppInfo>,
    items: List<HomeItem>,
    dockKeys: List<String>,
    showLabels: Boolean,
    onLayoutChange: (List<HomeItem>, List<String>) -> Unit,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    var dragTotal by remember { mutableStateOf(0f) }
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }

    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val spec = remember(isLandscape) { gridSpec(isLandscape) }

    var draggingItem by remember { mutableStateOf<HomeItem?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dockCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cellCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val openFolderResolved = items.asSequence()
        .filterIsInstance<HomeFolder>()
        .firstOrNull { it.id == openFolderId }

    fun cellIndexAt(pos: Offset): Int {
        if (gridSize == IntSize.Zero) return -1
        val padH = with(density) { 16.dp.toPx() }
        val padV = with(density) { 12.dp.toPx() }
        val cellW = (gridSize.width - 2 * padH) / spec.cols
        val cellH = (gridSize.height - 2 * padV) / spec.rows
        if (cellW <= 0 || cellH <= 0) return -1
        val col = ((pos.x - padH) / cellW).toInt().coerceIn(0, spec.cols - 1)
        val row = ((pos.y - padV) / cellH).toInt().coerceIn(0, spec.rows - 1)
        return row * spec.cols + col
    }

    fun handleDragEnd(dragged: HomeItem) {
        val dc = dockCoords
        val gc = gridCoords
        // 拖到 Dock：替换对应槽位的应用，旧应用回到网格末尾
        if (dc != null && gc != null && dragged is HomeApp) {
            val rootPos = gc.positionInRoot() + dragPos
            val local = rootPos - dc.positionInRoot()
            if (local.x >= 0f && local.y >= 0f &&
                local.x <= dc.size.width.toFloat() && local.y <= dc.size.height.toFloat()
            ) {
                val slot = (local.x / (dc.size.width / 5f)).toInt().coerceIn(0, 4)
                if (slot < 4 && dockKeys.getOrNull(slot) != dragged.id) {
                    val oldKey = dockKeys.getOrNull(slot)
                    val newItems = items.filterNot { it.id == dragged.id }.toMutableList()
                    if (!oldKey.isNullOrEmpty() && oldKey != dragged.id) newItems.add(HomeApp(oldKey))
                    val newDock = dockKeys.toMutableList()
                    while (newDock.size < 4) newDock.add("")
                    newDock[slot] = dragged.id
                    onLayoutChange(newItems, newDock.filter { it.isNotEmpty() })
                    return
                }
            }
        }
        val index = cellIndexAt(dragPos)
        if (index < 0) return
        val from = items.indexOf(dragged)
        if (from < 0) return
        val target = items.getOrNull(index)
        val newList = items.toMutableList()
        when {
            target == null || target.id == dragged.id -> {
                newList.removeAt(from)
                newList.add(index.coerceAtMost(newList.size), dragged)
                onLayoutChange(newList, dockKeys)
            }
            dragged is HomeFolder -> {
                newList.removeAt(from)
                newList.add(index.coerceAtMost(newList.size), dragged)
                onLayoutChange(newList, dockKeys)
            }
            target is HomeApp -> {
                newList.removeAt(from)
                val targetIdx = newList.indexOf(target)
                if (targetIdx >= 0) {
                    newList[targetIdx] = HomeFolder(
                        id = "folder_${System.currentTimeMillis()}",
                        name = "文件夹",
                        members = listOf(target.id, dragged.id),
                    )
                }
                onLayoutChange(newList, dockKeys)
            }
            target is HomeFolder -> {
                newList.removeAt(from)
                val targetIdx = newList.indexOf(target)
                if (targetIdx >= 0) {
                    newList[targetIdx] = target.copy(members = target.members + dragged.id)
                }
                onLayoutChange(newList, dockKeys)
            }
        }
    }

    val highlightIndex = if (draggingItem != null) cellIndexAt(dragPos) else -1

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -160f) onOpenDrawer() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            OneUiClockHeader(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 28.dp, top = 18.dp, bottom = 8.dp),
            )
            // 图标网格
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        gridCoords = it
                        gridSize = it.size
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    for (row in 0 until spec.rows) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (col in 0 until spec.cols) {
                                val index = row * spec.cols + col
                                val item = items.getOrNull(index)
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (index == highlightIndex && draggingItem != null) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(7.dp)
                                                .background(
                                                    Color.White.copy(alpha = 0.18f),
                                                    RoundedCornerShape(20.dp),
                                                ),
                                        )
                                    }
                                    if (item != null) {
                                        key(item.id) {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .onGloballyPositioned { cellCoords[item.id] = it }
                                                    .pointerInput(item.id) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { offset ->
                                                                val gc = gridCoords
                                                                val cc = cellCoords[item.id]
                                                                if (draggingItem == null && gc != null && cc != null) {
                                                                    draggingItem = item
                                                                    dragPos = cc.positionInRoot() - gc.positionInRoot() + offset
                                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                dragPos += amount
                                                            },
                                                            onDragEnd = {
                                                                val dragged = draggingItem
                                                                draggingItem = null
                                                                if (dragged != null) handleDragEnd(dragged)
                                                            },
                                                            onDragCancel = { draggingItem = null },
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                HomeItemContent(
                                                    item = item,
                                                    appsByKey = appsByKey,
                                                    showLabels = showLabels,
                                                    onLaunch = onLaunch,
                                                    onOpenFolder = { openFolderId = it.id },
                                                    dim = draggingItem?.id == item.id,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PageDots(count = 1, active = 0, darkText = false, modifier = Modifier.padding(bottom = 12.dp))
            Box(
                Modifier
                    .onGloballyPositioned { dockCoords = it }
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            ) {
                DockBar(
                    dockApps = dockKeys.mapNotNull { appsByKey[it] },
                    onOpenDrawer = onOpenDrawer,
                    onLaunch = onLaunch,
                )
            }
        }

        // 跟随手指的浮动图标
        draggingItem?.let { item ->
            val iconHalf = with(density) { 28.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (dragPos.x - iconHalf).roundToInt(),
                            (dragPos.y - iconHalf).roundToInt(),
                        )
                    }
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = 1.22f
                        scaleY = 1.22f
                        alpha = 0.95f
                    },
                contentAlignment = Alignment.Center,
            ) {
                FloatingItemContent(item, appsByKey)
            }
        }

        // 空白处长按菜单
        homeMenuAt?.let { pos ->
            Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { homeMenuAt = null }) {
                    DropdownMenuItem(
                        text = { Text("壁纸") },
                        onClick = {
                            homeMenuAt = null
                            onSetWallpaper()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("桌面设置") },
                        onClick = {
                            homeMenuAt = null
                            onOpenSettings()
                        },
                    )
                }
            }
        }

        // 文件夹弹窗
        openFolderResolved?.let { folder ->
            FolderDialog(
                folder = folder,
                appsByKey = appsByKey,
                onDismiss = { newName ->
                    val updated = items.map {
                        if (it is HomeFolder && it.id == folder.id) it.copy(name = newName) else it
                    }
                    onLayoutChange(updated, dockKeys)
                    openFolderId = null
                },
                onLaunch = { app ->
                    onLaunch(app)
                    openFolderId = null
                },
                onDisband = {
                    val newItems = buildList {
                        for (it in items) {
                            if (it is HomeFolder && it.id == folder.id) {
                                addAll(it.members.map { key -> HomeApp(key) })
                            } else {
                                add(it)
                            }
                        }
                    }
                    onLayoutChange(newItems, dockKeys)
                    openFolderId = null
                },
            )
        }
    }
}

@Composable
private fun HomeItemContent(
    item: HomeItem,
    appsByKey: Map<String, AppInfo>,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeFolder) -> Unit,
    dim: Boolean,
) {
    when (item) {
        is HomeApp -> appsByKey[item.id]?.let { app ->
            AppIconCell(app = app, onLaunch = onLaunch, darkText = false, showLabel = showLabels, dim = dim)
        }
        is HomeFolder -> FolderIcon(
            folder = item,
            appsByKey = appsByKey,
            showLabel = showLabels,
            onOpen = onOpenFolder,
            dim = dim,
        )
    }
}

@Composable
private fun FloatingItemContent(item: HomeItem, appsByKey: Map<String, AppInfo>) {
    when (item) {
        is HomeApp -> appsByKey[item.id]?.let { app ->
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(52.dp))
        }
        is HomeFolder -> Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            val icons = item.members.mapNotNull { appsByKey[it] }.take(4)
            Column {
                icons.chunked(2).take(2).forEach { pair ->
                    Row {
                        pair.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
                        repeat(2 - pair.size) { Spacer(Modifier.size(19.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderIcon(
    folder: HomeFolder,
    appsByKey: Map<String, AppInfo>,
    showLabel: Boolean,
    onOpen: (HomeFolder) -> Unit,
    dim: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { alpha = if (dim) 0.4f else 1f }
            .clickable { onOpen(folder) }
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.38f)),
            contentAlignment = Alignment.Center,
        ) {
            val icons = folder.members.mapNotNull { appsByKey[it] }.take(4)
            Column {
                icons.chunked(2).take(2).forEach { pair ->
                    Row {
                        pair.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
                        repeat(2 - pair.size) { Spacer(Modifier.size(19.dp)) }
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                folder.name,
                fontSize = 12.sp,
                maxLines = 1,
                style = TextStyle(
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
                ),
            )
        }
    }
}

@Composable
private fun FolderDialog(
    folder: HomeFolder,
    appsByKey: Map<String, AppInfo>,
    onDismiss: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onDisband: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val members = folder.members.mapNotNull { appsByKey[it] }
    Dialog(
        onDismissRequest = { onDismiss(name.ifBlank { folder.name }) },
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("文件夹名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    members.chunked(4).forEach { rowApps ->
                        Row(Modifier.fillMaxWidth()) {
                            rowApps.forEach { app ->
                                Box(Modifier.weight(1f)) {
                                    AppIconCell(
                                        app = app,
                                        onLaunch = { onLaunch(app); },
                                        darkText = true,
                                    )
                                }
                            }
                            repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDisband, modifier = Modifier.align(Alignment.End)) {
                    Text("解散文件夹")
                }
            }
        }
    }
}

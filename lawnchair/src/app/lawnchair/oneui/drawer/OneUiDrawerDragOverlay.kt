/*
 * Copyright 2026, OneUI Desktop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.oneui.drawer

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.data.AppInfo
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One UI drawer drag edit mode. While an icon is dragged the whole drawer is taken over:
 * the background dims, the apps are laid out as paged grids (one page per
 * [DeviceProfile.inv.numColumns] x numRows cells), neighbouring pages peek from the screen
 * edges, and a dot grid + landing outline preview the target cell.
 *
 * Drop semantics (matching the real One UI):
 * - drop on a cell: move the app to that cell in the custom order;
 * - hold near the left / right edge: flip between the existing pages;
 * - drop in the empty area above / below the grid: add the app to the home screen.
 */
class OneUiDrawerDragOverlay(
    private val launcher: LawnchairLauncher,
) : View(launcher), DropTarget, DragController.DragListener {

    private val dragLayer: DragLayer get() = launcher.dragLayer
    private val appsView get() = launcher.appsView
    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val density = resources.displayMetrics.density

    private val dimPaint = Paint()
    private val neighborPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-regular", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val iconCache = HashMap<String, Drawable>()

    private var active = false
    private var ordered: MutableList<AppInfo>? = null
    private var dragged: AppInfo? = null
    private var page = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null
    private var pageCount = 1

    private var targetIndex = -1
    private val targetRect = Rect()
    private var addToHomeEdge = 0
    private var edgeDirection = 0

    private var lastTouchX = 0
    private var lastTouchY = 0
    private var dragStartX = 0
    private var dragStartY = 0
    private var itemMenu: PopupWindow? = null

    private val tmpRect = Rect()
    private val location = IntArray(2)

    private val addToHomeText = context.getString(R.string.oneui_drawer_drag_add_to_home)

    private val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlinePaint.strokeWidth = 1.3f * density
        plusPaint.strokeWidth = 1.6f * density
        bandTextPaint.textSize = 13.5f * resources.displayMetrics.scaledDensity
        labelPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        dotsPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 70)
        bandPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 26)
        bandTextPaint.color = Color.WHITE
        plusPaint.color = Color.WHITE
        labelPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 235)
        neighborPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 64)
        // The edit mode sits on a dark scrim even when the drawer itself uses a light theme.
        dimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 200)
    }

    // region Data

    private val gridCols: Int get() = launcher.deviceProfile.inv.numColumns
    private val gridRows: Int get() = launcher.deviceProfile.inv.numRows
    private val cellsPerPage: Int get() = gridCols * gridRows

    /**
     * Snapshot of the drawer in display order. The preview keeps the current order; the custom
     * sort is only persisted when the user actually drops the icon on a new cell.
     */
    private fun initOrdered(): MutableList<AppInfo> {
        val list = ordered
        if (list != null) return list
        val recyclerView = appsView.activeRecyclerView
        val keys = (recyclerView?.apps as? LawnchairAlphabeticalAppsList<*>)
            ?.adapterItems
            ?.asSequence()
            ?.filter { it.viewType == BaseAllAppsAdapter.VIEW_TYPE_ICON }
            ?.mapNotNull { it.itemInfo as? AppInfo }
            ?.toMutableList()
            ?: mutableListOf()
        ordered = keys
        return keys
    }

    private fun pageOf(index: Int): Int = if (index < 0) 0 else index / cellsPerPage

    private fun commitOrder() {
        val list = ordered ?: return
        prefs.drawerCustomOrder.set(list.joinToString("\n") { it.toComponentKey().toString() })
        prefs.drawerSortMode.set(LawnchairAlphabeticalAppsList.SORT_CUSTOM)
    }

    // endregion

    // region DragController.DragListener

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        if (!options.isDrawerDrag) return
        val app = dragObject.dragInfo as? AppInfo
        val list = initOrdered()
        if (app == null || list.indexOf(app) < 0) {
            // Dragged item is not a drawer app (e.g. search result); nothing to take over.
            return
        }
        active = true
        dragged = app
        iconCache.clear()
        page = pageOf(list.indexOf(app)).coerceAtLeast(0)
        pageOffset = page.toFloat()
        pageCount = pageCountFor(list.size)
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        dragStartX = dragObject.x
        dragStartY = dragObject.y
        // The overlay takes over completely; the live list (and its search capsule) hide.
        appsView.visibility = INVISIBLE
        showItemMenu(app, dragObject.originalView as? View)
        invalidate()
    }

    override fun onDragEnd() {
        removeCallbacks(edgeFlipRunnable)
        edgeDirection = 0
        dismissItemMenu()
        active = false
        dragged = null
        ordered = null
        iconCache.clear()
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        pageAnimator?.cancel()
        pageAnimator = null
        appsView.visibility = VISIBLE
        invalidate()
    }

    // endregion

    // region DropTarget

    override fun isDropEnabled(): Boolean =
        active && launcher.isInState(LauncherState.ALL_APPS)

    override fun onDragEnter(dragObject: DropTarget.DragObject) = Unit

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        lastTouchX = dragObject.x
        lastTouchY = dragObject.y
        // Moving the icon beyond a small slop closes the One UI item menu.
        if (itemMenu != null) {
            val dx = (lastTouchX - dragStartX).toDouble()
            val dy = (lastTouchY - dragStartY).toDouble()
            if (sqrt(dx * dx + dy * dy) > 8f * density) {
                dismissItemMenu()
            }
        }
        updateTarget(lastTouchX, lastTouchY)
        invalidate()
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) = Unit

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = true

    override fun prepareAccessibilityDrop() = Unit

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        removeCallbacks(edgeFlipRunnable)
        edgeDirection = 0
        @Suppress("UNCHECKED_CAST")
        val dragView = dragObject.dragView as? DragView<Launcher>
        val app = dragged
        if (dragView == null || app == null) {
            resetTarget()
            return
        }
        when {
            addToHomeEdge != 0 -> {
                val added = launcher.workspace.addAppFromDrawer(app) != null
                val text = if (added) R.string.oneui_drawer_drag_added else R.string.oneui_drawer_drag_no_space
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                // The app stays in the drawer; adding it to the home screen only creates a copy.
                fadeOutDragView(dragView)
            }
            targetIndex >= 0 -> {
                val list = ordered ?: return
                val from = list.indexOf(app)
                if (from >= 0) {
                    val before = list.toList()
                    val to = targetIndex.coerceIn(0, list.size)
                    list.removeAt(from)
                    // Exact-cell semantics: the icon lands in the previewed cell; the apps in
                    // between shift by one to make room.
                    list.add(to.coerceIn(0, list.size), app)
                    if (list != before) {
                        val wasCustom = prefs.drawerSortMode.get() ==
                            LawnchairAlphabeticalAppsList.SORT_CUSTOM
                        commitOrder()
                        if (!wasCustom) {
                            Toast.makeText(
                                context,
                                R.string.oneui_drawer_drag_alphabetical,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                animateDragViewToTarget(dragView)
            }
            else -> flyBack(dragView)
        }
        resetTarget()
    }

    private fun resetTarget() {
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        invalidate()
    }

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    // endregion

    // region Geometry

    private fun cardRect(out: Rect): Rect {
        val w = width
        val h = height
        if (w == 0 || h == 0) {
            out.setEmpty()
            return out
        }
        val horizontal = (w * 0.063f).roundToInt()
        val top = (h * 0.092f).roundToInt()
        val bottom = (h * 0.836f).roundToInt()
        out.set(horizontal, top, w - horizontal, bottom)
        return out
    }

    private fun pageX(pageIndex: Int, card: Rect): Int =
        card.left + ((pageIndex - pageOffset) * (card.width() + 16f * density)).roundToInt()

    // endregion

    // region Target computation

    private fun updateTarget(x: Int, y: Int) {
        val card = cardRect(tmpRect)
        if (card.isEmpty) {
            resetTarget()
            return
        }

        // Above / below the paged card: drop to the home screen.
        if (y < card.top || y > card.bottom) {
            addToHomeEdge = if (y < card.top) 1 else 2
            targetIndex = -1
            targetRect.setEmpty()
            stopEdgeFlip()
            invalidate()
            return
        }
        addToHomeEdge = 0

        // Left / right edge band: flip pages while held. Keep the band narrow so the outer
        // columns can still be used as drop cells.
        val edge = 36f * density
        val direction = when {
            x < card.left + edge -> -1
            x > card.right - edge -> 1
            else -> 0
        }
        if (direction != 0) {
            targetIndex = -1
            targetRect.setEmpty()
            startEdgeFlip(direction)
            invalidate()
            return
        }
        stopEdgeFlip()

        if (!card.contains(x, y)) {
            resetTarget()
            invalidate()
            return
        }

        updateGridTarget(x, y, card)
    }

    private fun updateGridTarget(x: Int, y: Int, card: Rect) {
        val list = ordered ?: return
        val cellW = card.width().toFloat() / gridCols
        val cellH = card.height().toFloat() / gridRows
        val col = ((x - card.left) / cellW).toInt().coerceIn(0, gridCols - 1)
        val row = ((y - card.top) / cellH).toInt().coerceIn(0, gridRows - 1)
        val cell = row * gridCols + col

        val cellLeft = (card.left + col * cellW).roundToInt()
        val cellTop = (card.top + row * cellH).roundToInt()
        targetRect.set(cellLeft, cellTop, (cellLeft + cellW).roundToInt(), (cellTop + cellH).roundToInt())

        val pageStart = page * cellsPerPage
        targetIndex = when {
            // Drop on an occupied cell: the icon takes that exact cell and the rest reflow.
            pageStart + cell < list.size -> pageStart + cell
            // Empty cell on the last page: append to the end of the order.
            pageStart + cell >= list.size && page == pageCount - 1 -> list.size
            else -> -1
        }
    }

    private fun pageCountFor(size: Int): Int = ceil(size / cellsPerPage.toDouble()).toInt().coerceAtLeast(1)

    private fun iconSize(cellW: Float, cellH: Float): Int =
        min(cellW * 0.64f, cellH * 0.52f).roundToInt()

    /** Icons and labels sit slightly above the vertical cell centre, as in the drawer itself. */
    private fun iconCenterY(cellTop: Int, cellH: Float): Float = cellTop + cellH * 0.42f

    // endregion

    // region Edge flip

    private fun startEdgeFlip(direction: Int) {
        if (edgeDirection == direction) return
        edgeDirection = direction
        removeCallbacks(edgeFlipRunnable)
        postDelayed(edgeFlipRunnable, EDGE_FLIP_DWELL)
    }

    private fun stopEdgeFlip() {
        if (edgeDirection == 0) return
        edgeDirection = 0
        removeCallbacks(edgeFlipRunnable)
    }

    private val edgeFlipRunnable = object : Runnable {
        override fun run() {
            if (edgeDirection == 0) return
            val target = page + edgeDirection
            if (target < 0 || target >= pageCount) {
                // A packed custom order cannot hold the gap that a brand-new page would need,
                // so the editor only flips between existing pages.
                stopEdgeFlip()
                return
            }
            animatePage(target)
            postDelayed(this, EDGE_FLIP_INTERVAL)
        }
    }

    private fun animatePage(target: Int) {
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageOffset, target.toFloat()).apply {
            duration = 220
            interpolator = this@OneUiDrawerDragOverlay.interpolator
            addUpdateListener {
                pageOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        page = target
    }

    // endregion

    // region One UI item menu

    /**
     * Shows the One UI item menu (title + add-to-home / uninstall) anchored to the source
     * icon. The menu closes as soon as the icon is moved; the drag itself keeps running.
     */
    private fun showItemMenu(app: AppInfo, source: View?) {
        dismissItemMenu()
        if (source == null) return
        val pkg = app.componentName?.packageName ?: return
        val density = resources.displayMetrics.density
        val textColor = Color.parseColor("#DE000000")

        fun px(v: Float): Int = (v * density).roundToInt()

        fun actionButton(iconRes: Int, label: String, onClick: () -> Unit): LinearLayout {
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#3B3F45"))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(px(24f), px(24f))
            }
            val text = TextView(context).apply {
                text = label
                setTextColor(textColor)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, px(6f), 0, 0) }
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(px(14f), px(10f), px(14f), px(10f))
                isClickable = true
                isFocusable = true
                addView(icon)
                addView(text)
                setOnClickListener { onClick() }
            }
        }

        val title = TextView(context).apply {
            text = app.title
            setTextColor(textColor)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val infoIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            setColorFilter(Color.parseColor("#66000000"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(px(30f), px(30f))
            setOnClickListener {
                dismissItemMenu()
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$pkg"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(12f), px(8f), px(4f))
            addView(title)
            addView(infoIcon)
        }
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                actionButton(R.drawable.ic_home_screen, context.getString(R.string.oneui_item_menu_add_home)) {
                    dismissItemMenu()
                    val added = launcher.workspace.addAppFromDrawer(app) != null
                    Toast.makeText(
                        context,
                        if (added) R.string.oneui_drawer_drag_added else R.string.oneui_drawer_drag_no_space,
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (added) {
                        launcher.dragController.cancelDrag()
                    }
                },
            )
            addView(
                actionButton(R.drawable.ic_remove_no_shadow, context.getString(R.string.oneui_item_menu_uninstall)) {
                    dismissItemMenu()
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(24f).toFloat()
                setColor(Color.parseColor("#F7F7FA"))
            }
            elevation = px(12f).toFloat()
            addView(titleRow)
            addView(actionsRow)
        }

        val menu = PopupWindow(
            container,
            px(300f),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isOutsideTouchable = true
        }

        // Anchor above the source icon, centered on it.
        source.getLocationOnScreen(location)
        val own = IntArray(2)
        getLocationOnScreen(own)
        val anchorX = location[0] - own[0] + source.width / 2
        val anchorY = location[1] - own[1]
        val x = (anchorX - px(300f) / 2).coerceIn(8, width - px(300f) - 8)
        val y = (anchorY - px(300f) / 8).coerceAtLeast(8)
        menu.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        itemMenu = menu
    }

    private fun dismissItemMenu() {
        itemMenu?.dismiss()
        itemMenu = null
    }    // endregion

    // region Drop animations

    private fun animateDragViewToTarget(dragView: DragView<Launcher>) {
        dragLayer.getLocationInDragLayer(this, location)
        val cellH = targetRect.height().toFloat()
        val targetLeft = targetRect.centerX() - dragView.measuredWidth / 2 + location[0]
        val targetTop = iconCenterY(targetRect.top, cellH) - dragView.measuredHeight / 2 + location[1]
        dragLayer.animateViewIntoPosition(
            dragView,
            targetLeft,
            targetTop.roundToInt(),
            1f,
            1f,
            1f,
            null,
            DragLayer.ANIMATION_END_DISAPPEAR,
            180,
            null,
        )
    }

    private fun fadeOutDragView(dragView: DragView<Launcher>) {
        dragLayer.getLocationInDragLayer(dragView, location)
        dragLayer.animateViewIntoPosition(
            dragView,
            location[0],
            location[1],
            0f,
            1f,
            1f,
            null,
            DragLayer.ANIMATION_END_DISAPPEAR,
            140,
            null,
        )
    }

    private fun flyBack(dragView: DragView<Launcher>) {
        launcher.dragController.animateDragViewToOriginalPosition(null, null, 160)
    }

    // endregion

    // region Drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val list = ordered ?: return
        val card = cardRect(tmpRect)
        if (card.isEmpty) return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val cellW = card.width().toFloat() / gridCols
        val cellH = card.height().toFloat() / gridRows
        val iconSize = iconSize(cellW, cellH)

        // Neighbouring pages peek from the screen edges as light rounded strips, exactly like the
        // One UI paged editor; the current page itself has no card background. They are drawn at
        // rest only, while a page flip animation moves the incoming page itself.
        if (pageOffset == page.toFloat()) {
            drawNeighborStrips(canvas, card)
        }

        val currentLeft = pageX(page, card).toFloat()
        val pageCard = RectF(
            currentLeft,
            card.top.toFloat(),
            currentLeft + card.width(),
            card.bottom.toFloat(),
        )
        drawDots(canvas, pageCard, cellW, cellH)
        drawPageApps(canvas, list, page, pageCard, iconSize)
        if (targetIndex >= 0 && !targetRect.isEmpty) {
            drawTargetOutline(canvas, cellW, cellH)
        }

        if (addToHomeEdge != 0) {
            drawAddToHome(canvas, card)
        } else {
            drawPageDots(canvas, card, pageCount, page)
        }
    }

    private fun drawNeighborStrips(canvas: Canvas, card: Rect) {
        val stripWidth = 9f * density
        val radius = 26f * density
        val top = card.top.toFloat()
        val bottom = card.bottom.toFloat()
        if (page > 0) {
            // Left rounded corners stay off screen, so only the inner edge reads as rounded.
            canvas.drawRoundRect(-radius, top, stripWidth, bottom, radius, radius, neighborPaint)
        }
        if (page < pageCount - 1) {
            val right = width.toFloat()
            canvas.drawRoundRect(right - stripWidth, top, right + radius, bottom, radius, radius, neighborPaint)
        }
    }

    private fun drawDots(canvas: Canvas, pageCard: RectF, cellW: Float, cellH: Float) {
        val half = 0.9f * density
        for (col in 1 until gridCols) {
            val x = pageCard.left + col * cellW
            for (row in 1..gridRows) {
                val y = pageCard.top + row * cellH
                canvas.drawRect(x - half, y - half, x + half, y + half, dotsPaint)
            }
        }
    }

    private fun drawPageApps(canvas: Canvas, list: List<AppInfo>, page: Int, pageCard: RectF, iconSize: Int) {
        val cellW = pageCard.width() / gridCols
        val cellH = pageCard.height() / gridRows
        val draggedKey = dragged?.toComponentKey()?.toString()
        val emptyCell = 5f * density
        for (cell in 0 until cellsPerPage) {
            val app = list.getOrNull(page * cellsPerPage + cell) ?: continue
            val key = app.toComponentKey().toString()
            if (draggedKey != null && key == draggedKey) continue
            val col = cell % gridCols
            val row = cell / gridCols
            val cellLeft = pageCard.left + col * cellW
            val cellTop = pageCard.top + row * cellH
            val cx = cellLeft + cellW / 2f
            val cy = iconCenterY(cellTop.roundToInt(), cellH)
            val drawable = iconCache.getOrPut(key) { app.bitmap.newIcon(context) }
            drawable.setBounds(
                (cx - iconSize / 2f).roundToInt(),
                (cy - iconSize / 2f).roundToInt(),
                (cx + iconSize / 2f).roundToInt(),
                (cy + iconSize / 2f).roundToInt(),
            )
            drawable.draw(canvas)

            canvas.save()
            canvas.clipRect(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH)
            val label = TextUtils.ellipsize(
                app.title ?: "",
                labelPaint,
                cellW - emptyCell * 2,
                TextUtils.TruncateAt.END,
            )
            val labelTop = cy + iconSize / 2f + 8f * density
            val baseline = labelTop - labelPaint.fontMetrics.ascent
            canvas.drawText(label.toString(), cx, baseline, labelPaint)
            canvas.restore()
        }
    }

    private fun drawTargetOutline(canvas: Canvas, cellW: Float, cellH: Float) {
        val size = iconSize(cellW, cellH) + 10f * density
        val cx = targetRect.centerX().toFloat()
        val cy = iconCenterY(targetRect.top, cellH)
        val half = size / 2f
        val outline = RectF(cx - half, cy - half, cx + half, cy + half)
        val radius = size * 0.24f
        outlinePaint.alpha = 200
        canvas.drawRoundRect(outline, radius, radius, outlinePaint)
    }

    private fun drawAddToHome(canvas: Canvas, card: Rect) {
        val height = 46f * density
        val gap = 12f * density
        val top = if (addToHomeEdge == 1) {
            (card.top - height - gap).coerceAtLeast(72f * density)
        } else {
            card.bottom + gap
        }
        val margin = 32f * density
        val band = RectF(card.left + margin, top, card.right - margin, top + height)
        canvas.drawRoundRect(band, height / 2f, height / 2f, bandPaint)

        val circleX = band.left + 26f * density
        val circleY = band.centerY()
        val circleRadius = 8.5f * density
        canvas.drawCircle(circleX, circleY, circleRadius, plusPaint)
        val plusHalf = 4.2f * density
        canvas.drawLine(circleX - plusHalf, circleY, circleX + plusHalf, circleY, plusPaint)
        canvas.drawLine(circleX, circleY - plusHalf, circleX, circleY + plusHalf, plusPaint)

        val textX = band.left + 46f * density
        val metrics = bandTextPaint.fontMetrics
        val baseline = band.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(addToHomeText, textX, baseline, bandTextPaint)
    }

    private fun drawPageDots(canvas: Canvas, card: Rect, count: Int, activePage: Int) {
        val radius = 3.2f * density
        val spacing = 17f * density
        val total = count.coerceAtLeast(1)
        val centerX = card.exactCenterX()
        val y = card.bottom + 78f * density
        for (i in 0 until total) {
            pageDotPaint.color = Color.WHITE
            pageDotPaint.alpha = if (i == activePage) 255 else 90
            canvas.drawCircle(centerX - (total - 1) * spacing / 2f + i * spacing, y, radius, pageDotPaint)
        }
        val plusX = centerX + (total - 1) * spacing / 2f + 22f * density
        val half = 4.6f * density
        pageDotPaint.color = Color.WHITE
        pageDotPaint.alpha = 90
        canvas.drawLine(plusX - half, y, plusX + half, y, pageDotPaint)
        canvas.drawLine(plusX, y - half, plusX, y + half, pageDotPaint)
    }

    // endregion

    private companion object {
        const val EDGE_FLIP_DWELL = 380L
        const val EDGE_FLIP_INTERVAL = 420L
    }
}

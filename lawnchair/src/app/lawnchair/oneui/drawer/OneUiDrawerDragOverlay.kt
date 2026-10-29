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
import android.view.View
import android.view.ViewParent
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemLongClickListener
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One UI drawer drag edit mode. While an icon is dragged the whole drawer is taken over:
 * the background dims, the apps are laid out as paged grid cards (one page per
 * [DeviceProfile.inv.numColumns] x numRows cells), neighbouring pages peek from the sides,
 * dot grid + landing outline preview the target cell.
 *
 * Drop semantics (matching the real One UI):
 * - drop on a cell: move the app there in the custom order;
 * - drop past the left / right edge: flip to the neighbouring page (a fresh page is created
 *   when flipping past the last one);
 * - drop in the empty area above / below the card: add the app to the home screen.
 */
class OneUiDrawerDragOverlay(
    private val launcher: LawnchairLauncher,
) : View(launcher), DropTarget, DragController.DragListener {

    private val dragLayer: DragLayer get() = launcher.dragLayer
    private val appsView get() = launcher.appsView
    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val density = resources.displayMetrics.density

    private val dimPaint = Paint()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-regular", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

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
    private var edgeFlipCountdown = 0L

    private var lastTouchX = 0
    private var lastTouchY = 0

    private val tmpRect = Rect()
    private val tmpRect2 = Rect()
    private val location = IntArray(2)
    private val ownLocation = IntArray(2)
    private val dotKeys = HashSet<Long>()

    private val addIcon = context.getDrawable(R.drawable.ic_plus) // reserved for future use
    private val addToHomeText = context.getString(R.string.oneui_drawer_drag_add_to_home)

    private val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlinePaint.strokeWidth = 2f * density
        plusPaint.strokeWidth = 1.8f * density
        bandTextPaint.textSize = 14f * density
        labelPaint.textSize = 11f * density
        val textColor = ColorTokens.TextColorPrimary.resolveColor(context)
        dotsPaint.color = ColorUtils.setAlphaComponent(textColor, 90)
        bandPaint.color = ColorUtils.setAlphaComponent(textColor, 32)
        bandTextPaint.color = textColor
        plusPaint.color = textColor
        pageDotPaint.color = ColorUtils.setAlphaComponent(textColor, 140)
        labelPaint.color = textColor
        dimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 96)
        cardPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 66)
        neighborPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 110)
    }

    /** Handles a long click from the all-apps adapter. Returns true when a drag was started. */
    fun startDragFromLongClick(view: View): Boolean {
        if (view !is BubbleTextView) return false
        val app = view.tag as? AppInfo ?: return false
        val recyclerView = appsView.activeRecyclerView ?: return false
        if (!isInside(view, recyclerView)) return false
        if (!launcher.isInState(LauncherState.ALL_APPS)) return false
        if (appsView.isSearching) return false
        if (launcher.workspace.isSwitchingState) return false
        if (!ItemLongClickListener.canStartDrag(launcher)) return false

        view.cancelLongPress()
        val options = DragOptions()
        options.isDrawerDrag = true
        launcher.workspace.beginDragShared(view, appsView, options)
        return true
    }

    private fun isInside(view: View, parent: View): Boolean {
        var current: ViewParent? = view.parent
        while (current != null) {
            if (current === parent) return true
            current = current.parent
        }
        return false
    }

    // region Data

    private val gridCols: Int get() = launcher.deviceProfile.inv.numColumns
    private val gridRows: Int get() = launcher.deviceProfile.inv.numRows
    private val cellsPerPage: Int get() = gridCols * gridRows

    /**
     * Snapshot of the drawer in display order. Entering the drag switches the drawer to the
     * custom sort (One UI does the same): the current display order seeds the custom order.
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
        if (prefs.drawerSortMode.get() != LawnchairAlphabeticalAppsList.SORT_CUSTOM) {
            prefs.drawerCustomOrder.set(keys.joinToString("\n") { it.toComponentKey().toString() })
            prefs.drawerSortMode.set(LawnchairAlphabeticalAppsList.SORT_CUSTOM)
        }
        ordered = keys
        return keys
    }

    private fun orderedIndex(key: String): Int = ordered?.indexOfFirst { it.toComponentKey().toString() == key } ?: -1

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
        page = pageOf(list.indexOf(app)).coerceAtLeast(0)
        pageOffset = page.toFloat()
        pageCount = pageCountFor(list.size)
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        // The overlay takes over completely; the live list (and its search capsule) hide.
        appsView.visibility = INVISIBLE
        invalidate()
    }

    override fun onDragEnd() {
        removeCallbacks(edgeFlipRunnable)
        edgeDirection = 0
        active = false
        dragged = null
        ordered = null
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
                ordered?.remove(app)
                commitOrder()
                fadeOutDragView(dragView)
            }
            targetIndex >= 0 -> {
                val list = ordered ?: return
                val from = list.indexOf(app)
                if (from >= 0) {
                    val to = targetIndex.coerceIn(0, list.size)
                    list.removeAt(from)
                    list.add(if (to > from) to - 1 else to, app)
                    commitOrder()
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
        val horizontal = (w * 0.065f).roundToInt()
        val top = (h * 0.14f).roundToInt()
        val bottom = (h * 0.26f).roundToInt()
        out.set(horizontal, top, w - horizontal, h - bottom)
        return out
    }

    private fun pageX(pageIndex: Int, card: Rect): Int =
        card.left + ((pageIndex - pageOffset) * (card.width() + 24f * density)).roundToInt()

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

        // Left / right edge band: flip pages while held; flipping past the last page creates one.
        val edge = 52f * density
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
        val appAt = list.getOrNull(pageStart + cell)
        targetIndex = when {
            appAt != null -> pageStart + cell + if (x > targetRect.centerX()) 1 else 0
            // Empty cell on the last page: append at the end of the order.
            pageStart + cell >= list.size && page == pageCount - 1 -> list.size
            else -> -1
        }
    }

    private fun pageCountFor(size: Int): Int = ceil(size / cellsPerPage.toDouble()).toInt().coerceAtLeast(1)

    // endregion

    // region Edge flip

    private fun startEdgeFlip(direction: Int) {
        if (edgeDirection == direction) return
        edgeDirection = direction
        removeCallbacks(edgeFlipRunnable)
        edgeFlipCountdown = 0
        post(edgeFlipRunnable)
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
            if (target < 0) {
                stopEdgeFlip()
                return
            }
            // Flipping past the last page creates a fresh one (One UI "+ page").
            pageCount = max(pageCountFor((ordered?.size ?: 0)), target + 1)
            animatePage(target.coerceAtMost(pageCount - 1))
            postDelayed(this, 340)
        }
    }

    private fun animatePage(target: Int) {
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageOffset, target.toFloat()).apply {
            duration = 200
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

    // region Drop animations

    private fun animateDragViewToTarget(dragView: DragView<Launcher>) {
        dragLayer.getLocationInDragLayer(this, location)
        val targetLeft = targetRect.centerX() - dragView.measuredWidth / 2 + location[0]
        val targetTop = targetRect.centerY() - dragView.measuredHeight / 2 + location[1]
        dragLayer.animateViewIntoPosition(
            dragView,
            targetLeft,
            targetTop,
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
        val iconSize = (minOf(cellW, cellH) * 0.52f).roundToInt()
        val gap = 24f * density

        for (p in page - 1..page + 1) {
            val isCurrent = p == page
            if (p < 0 || p >= pageCount) continue
            val left = pageX(p, card)
            val pageCard = RectF(left.toFloat(), card.top.toFloat(), (left + card.width()).toFloat(), card.bottom.toFloat())
            canvas.save()
            // Neighbouring pages peek from the sides with reduced presence.
            if (!isCurrent) {
                canvas.saveLayerAlpha(pageCard.left, pageCard.top, pageCard.right, pageCard.bottom, 70)
            } else {
                canvas.drawRoundRect(pageCard, 24f * density, 24f * density, cardPaint)
            }
            drawDots(canvas, pageCard, cellW, cellH)
            drawPageApps(canvas, list, p, pageCard, iconSize)
            if (isCurrent && targetIndex >= 0 && !targetRect.isEmpty) {
                drawTargetOutline(canvas)
            }
            canvas.restore()
        }

        if (addToHomeEdge != 0) {
            drawAddToHome(canvas, card)
        }
        drawPageDots(canvas, card, pageCount, page)
    }

    private fun drawDots(canvas: Canvas, pageCard: RectF, cellW: Float, cellH: Float) {
        dotKeys.clear()
        val radius = 1.4f * density
        for (row in 0..gridRows) {
            for (col in 0..gridCols) {
                val x = pageCard.left + col * cellW
                val y = pageCard.top + row * cellH
                val key = (x.toLong() shl 32) xor y.toLong()
                if (!dotKeys.add(key)) continue
                canvas.drawCircle(x, y, radius, dotsPaint)
            }
        }
    }

    private fun drawPageApps(canvas: Canvas, list: List<AppInfo>, page: Int, pageCard: RectF, iconSize: Int) {
        val cellW = pageCard.width() / gridCols
        val cellH = pageCard.height() / gridRows
        val draggedKey = dragged?.toComponentKey()?.toString()
        for (cell in 0 until cellsPerPage) {
            val app = list.getOrNull(page * cellsPerPage + cell) ?: continue
            if (draggedKey != null && app.toComponentKey().toString() == draggedKey) continue
            val col = cell % gridCols
            val row = cell / gridCols
            val cellLeft = pageCard.left + col * cellW
            val cellTop = pageCard.top + row * cellH
            val cx = cellLeft + cellW / 2f
            val cy = cellTop + cellH * 0.42f
            val drawable = app.bitmap.newIcon(context)
            drawable.setBounds(
                (cx - iconSize / 2f).roundToInt(),
                (cy - iconSize / 2f).roundToInt(),
                (cx + iconSize / 2f).roundToInt(),
                (cy + iconSize / 2f).roundToInt(),
            )
            drawable.draw(canvas)

            canvas.save()
            canvas.clipRect(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH)
            val metrics = labelPaint.fontMetrics
            val labelY = cy + iconSize / 2f + 14f * density - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(app.title.toString(), cx, labelY, labelPaint)
            canvas.restore()
        }
    }

    private fun drawTargetOutline(canvas: Canvas) {
        val outline = RectF(targetRect)
        outline.inset(3f * density, 3f * density)
        val radius = 18f * density
        canvas.drawRoundRect(outline, radius, radius, outlinePaint)
    }

    private fun drawAddToHome(canvas: Canvas, card: Rect) {
        val height = 48f * density
        val gap = 12f * density
        val top = if (addToHomeEdge == 1) {
            card.top - height - gap
        } else {
            card.bottom + gap
        }
        val margin = 40f * density
        val band = RectF(card.left + margin, top, card.right - margin, top + height)
        canvas.drawRoundRect(band, height / 2f, height / 2f, bandPaint)

        val circleX = band.left + 28f * density
        val circleY = band.centerY()
        val circleRadius = 9f * density
        canvas.drawCircle(circleX, circleY, circleRadius, plusPaint)
        val plusHalf = 4.5f * density
        canvas.drawLine(circleX - plusHalf, circleY, circleX + plusHalf, circleY, plusPaint)
        canvas.drawLine(circleX, circleY - plusHalf, circleX, circleY + plusHalf, plusPaint)

        val textX = band.left + 48f * density
        val metrics = bandTextPaint.fontMetrics
        val baseline = band.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(addToHomeText, textX, baseline, bandTextPaint)
    }

    private fun drawPageDots(canvas: Canvas, card: Rect, count: Int, activePage: Int) {
        val radius = 2.6f * density
        val spacing = 14f * density
        val total = count.coerceAtLeast(1)
        val centerX = card.exactCenterX()
        val y = card.bottom + 26f * density
        for (i in 0 until total) {
            pageDotPaint.color = ColorUtils.setAlphaComponent(
                pageDotPaint.color,
                if (i == activePage) 200 else 70,
            )
            canvas.drawCircle(centerX - (total - 1) * spacing / 2f + i * spacing, y, radius, pageDotPaint)
        }
        val plusX = centerX + (total - 1) * spacing / 2f + 18f * density
        val half = 4f * density
        canvas.drawLine(plusX - half, y, plusX + half, y, pageDotPaint)
        canvas.drawLine(plusX, y - half, plusX, y + half, pageDotPaint)
    }

    // endregion
}

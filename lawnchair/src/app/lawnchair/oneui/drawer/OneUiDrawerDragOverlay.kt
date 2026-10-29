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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.ViewParent
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemLongClickListener
import kotlin.math.max

/**
 * Drop target and visual overlay for dragging icons inside the app drawer.
 *
 * While an icon is dragged the drawer enters the One UI edit mode: the background is dimmed,
 * the app grid shrinks into a rounded card (search capsule hidden), a dot grid marks the cell
 * corners and a white outline previews the landing cell. Dropping above / below the card adds
 * the app to the home screen; the left / right edges flip the list like drawer pages.
 */
class OneUiDrawerDragOverlay(
    private val launcher: LawnchairLauncher,
) : View(launcher), DropTarget, DragController.DragListener {

    private val dragLayer: DragLayer get() = launcher.dragLayer
    private val appsView get() = launcher.appsView
    private val density = resources.displayMetrics.density

    private val dimPaint = Paint()
    private val dotsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    private var active = false
    private var sourceView: BubbleTextView? = null
    private var dragApp: AppInfo? = null

    private var targetApp: AppInfo? = null
    private val targetRect = Rect()
    private var insertAfter = false
    private var addToHomeEdge = 0
    private var scrollDirection = 0

    private var lastTouchX = 0
    private var lastTouchY = 0

    private val tmpRect = Rect()
    private val tmpRect2 = Rect()
    private val location = IntArray(2)
    private val ownLocation = IntArray(2)
    private val dotKeys = HashSet<Long>()
    private val addToHomeText = context.getString(R.string.oneui_drawer_drag_add_to_home)

    /** The app grid shrinks to this fraction of its size while dragging (One UI edit mode). */
    private val cardScale = 0.84f
    private val editAnimationDuration = 180L

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlinePaint.strokeWidth = 2f * density
        plusPaint.strokeWidth = 1.8f * density
        bandTextPaint.textSize = 14f * density
        val textColor = ColorTokens.TextColorPrimary.resolveColor(context)
        dotsPaint.color = ColorUtils.setAlphaComponent(textColor, 90)
        bandPaint.color = ColorUtils.setAlphaComponent(textColor, 32)
        bandTextPaint.color = textColor
        plusPaint.color = textColor
        pageDotPaint.color = ColorUtils.setAlphaComponent(textColor, 140)
        dimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 90)
        cardPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 60)
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

    // region DragController.DragListener

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        if (!options.isDrawerDrag) return
        active = true
        sourceView = dragObject.originalView as? BubbleTextView
        dragApp = dragObject.dragInfo as? AppInfo
        sourceView?.visibility = View.INVISIBLE
        addToHomeEdge = 0
        targetApp = null
        targetRect.setEmpty()
        scrollDirection = 0
        enterEditMode()
        invalidate()
    }

    override fun onDragEnd() {
        removeCallbacks(scrollRunnable)
        scrollDirection = 0
        active = false
        sourceView?.visibility = View.VISIBLE
        sourceView = null
        dragApp = null
        targetApp = null
        targetRect.setEmpty()
        addToHomeEdge = 0
        exitEditMode()
        invalidate()
    }

    // endregion

    // region Edit mode visuals

    private fun enterEditMode() {
        appsView.findViewById<View>(R.id.search_container_all_apps)
            ?.animate()?.alpha(0f)?.setDuration(editAnimationDuration)?.start()
        appsView.activeRecyclerView?.animate()
            ?.scaleX(cardScale)
            ?.scaleY(cardScale)
            ?.setDuration(editAnimationDuration)
            ?.start()
    }

    private fun exitEditMode() {
        appsView.findViewById<View>(R.id.search_container_all_apps)
            ?.animate()?.alpha(1f)?.setDuration(editAnimationDuration)?.start()
        appsView.activeRecyclerView?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(editAnimationDuration)
            ?.start()
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

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        // DragController calls this right before acceptDrop()/onDrop(): keep the state around.
    }

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = true

    override fun prepareAccessibilityDrop() = Unit

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        removeCallbacks(scrollRunnable)
        scrollDirection = 0
        @Suppress("UNCHECKED_CAST")
        val dragView = dragObject.dragView as? DragView<Launcher>
        val app = dragApp
        if (dragView == null || app == null) {
            resetTarget()
            return
        }
        when {
            addToHomeEdge != 0 -> {
                val added = launcher.workspace.addAppFromDrawer(app) != null
                val text = if (added) {
                    R.string.oneui_drawer_drag_added
                } else {
                    R.string.oneui_drawer_drag_no_space
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                fadeOutDragView(dragView)
            }
            targetApp != null && targetApp !== app -> {
                val target = targetApp!!
                val reordered = (appsView.activeRecyclerView?.apps
                    as? LawnchairAlphabeticalAppsList<*>)?.moveAppInCustomOrder(
                    app,
                    target,
                    insertAfter,
                ) == true
                if (reordered) {
                    animateDragViewToTarget(dragView)
                } else {
                    Toast.makeText(
                        context,
                        R.string.oneui_drawer_drag_alphabetical,
                        Toast.LENGTH_SHORT,
                    ).show()
                    flyBack(dragView)
                }
            }
            else -> flyBack(dragView)
        }
        resetTarget()
    }

    private fun resetTarget() {
        targetApp = null
        targetRect.setEmpty()
        addToHomeEdge = 0
        invalidate()
    }

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    // endregion

    // region Target computation

    private fun updateTarget(x: Int, y: Int) {
        val recyclerView = appsView.activeRecyclerView
        if (recyclerView == null || recyclerView.width == 0) {
            resetTarget()
            return
        }
        val cardRect = rectOf(recyclerView, tmpRect)
        if (cardRect.width() == 0) {
            resetTarget()
            return
        }

        // One UI edit mode: everything above / below the shrunk card is the home target.
        if (y < cardRect.top || y > cardRect.bottom) {
            addToHomeEdge = if (y < cardRect.top) 1 else 2
            targetApp = null
            targetRect.setEmpty()
            updateEdgeScroll(0)
            invalidate()
            return
        }
        addToHomeEdge = 0

        if (!cardRect.contains(x, y)) {
            updateEdgeScroll(0)
            resetTarget()
            invalidate()
            return
        }

        // Left / right edges flip the drawer like pages (the list scrolls by a page).
        val horizontalEdge = 48f * density
        val direction = when {
            x < cardRect.left + horizontalEdge -> -1
            x > cardRect.right - horizontalEdge -> 1
            else -> 0
        }
        updateEdgeScroll(direction)
        if (direction != 0) {
            targetApp = null
            targetRect.setEmpty()
            invalidate()
            return
        }

        updateGridTarget(x, y)
    }

    private fun updateGridTarget(x: Int, y: Int) {
        val recyclerView = appsView.activeRecyclerView ?: return
        var bestView: View? = null
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            if (child.tag !is AppInfo || child === sourceView) continue
            val rect = rectOf(child, tmpRect2)
            val dx = max(rect.left - x, max(0, x - rect.right)).toFloat()
            val dy = max(rect.top - y, max(0, y - rect.bottom)).toFloat()
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                bestView = child
            }
        }
        if (bestView == null) {
            targetApp = null
            targetRect.setEmpty()
            return
        }
        targetApp = bestView.tag as? AppInfo
        rectOf(bestView, targetRect)
        insertAfter = x > targetRect.centerX()
    }

    private fun updateEdgeScroll(direction: Int) {
        if (direction == scrollDirection) return
        scrollDirection = direction
        removeCallbacks(scrollRunnable)
        if (direction != 0) post(scrollRunnable)
    }

    private val scrollRunnable = object : Runnable {
        override fun run() {
            val recyclerView = appsView.activeRecyclerView
            if (!active || scrollDirection == 0 || recyclerView == null) return
            if (!recyclerView.canScrollVertically(scrollDirection)) {
                scrollDirection = 0
                return
            }
            val step = (recyclerView.height * 0.8f).toInt().coerceAtLeast(200)
            recyclerView.smoothScrollBy(0, step * scrollDirection)
            updateGridTarget(lastTouchX, lastTouchY)
            invalidate()
            postDelayed(this, 420)
        }
    }

    private fun rectOf(view: View, out: Rect): Rect {
        view.getLocationOnScreen(location)
        getLocationOnScreen(ownLocation)
        val left = location[0] - ownLocation[0]
        val top = location[1] - ownLocation[1]
        out.set(left, top, left + view.width, top + view.height)
        return out
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
        launcher.dragController.animateDragViewToOriginalPosition(null, sourceView, 160)
    }

    // endregion

    // region Drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val recyclerView = appsView.activeRecyclerView ?: return
        val cardRect = rectOf(recyclerView, tmpRect)
        if (cardRect.isEmpty) return

        // Dimmed backdrop; the shrunk grid sits on it like a One UI edit-mode card.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        val cardOutline = RectF(cardRect)
        cardOutline.inset(-6f * density, -6f * density)
        canvas.drawRoundRect(
            cardOutline,
            24f * density,
            24f * density,
            cardPaint,
        )

        drawDots(canvas, recyclerView, cardRect)
        if (targetApp != null && !targetRect.isEmpty) {
            drawTargetOutline(canvas)
        }
        if (addToHomeEdge != 0) {
            drawAddToHome(canvas, cardRect)
        }
        drawPageDots(canvas, cardRect)
    }

    private fun drawPageDots(canvas: Canvas, cardRect: Rect) {
        val radius = 2.6f * density
        val spacing = 14f * density
        val count = 3
        val totalWidth = (count - 1) * spacing
        val centerX = cardRect.centerX().toFloat()
        val y = cardRect.bottom + 26f * density
        val textColor = pageDotPaint.color
        for (i in 0 until count) {
            pageDotPaint.color = if (i == 1) textColor else ColorUtils.setAlphaComponent(textColor, 70)
            canvas.drawCircle(centerX - totalWidth / 2f + i * spacing, y, radius, pageDotPaint)
        }
        // "+" hint for an extra page, like the One UI drawer edit mode.
        val plusX = centerX + totalWidth / 2f + 18f * density
        val half = 4f * density
        canvas.drawLine(plusX - half, y, plusX + half, y, pageDotPaint)
        canvas.drawLine(plusX, y - half, plusX, y + half, pageDotPaint)
    }

    private fun drawDots(canvas: Canvas, recyclerView: AllAppsRecyclerView, listRect: Rect) {
        dotKeys.clear()
        val radius = 1.4f * density
        val save = canvas.save()
        canvas.clipRect(listRect)
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            if (child.tag !is AppInfo) continue
            val rect = rectOf(child, tmpRect2)
            drawDot(canvas, rect.left, rect.top, radius)
            drawDot(canvas, rect.right, rect.top, radius)
            drawDot(canvas, rect.left, rect.bottom, radius)
            drawDot(canvas, rect.right, rect.bottom, radius)
        }
        canvas.restoreToCount(save)
    }

    private fun drawDot(canvas: Canvas, x: Int, y: Int, radius: Float) {
        if (!dotKeys.add((x.toLong() shl 32) xor y.toLong())) return
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius, dotsPaint)
    }

    private fun drawTargetOutline(canvas: Canvas) {
        val outline = RectF(targetRect)
        outline.inset(3f * density, 3f * density)
        val radius = 18f * density
        canvas.drawRoundRect(outline, radius, radius, outlinePaint)
    }

    private fun drawAddToHome(canvas: Canvas, cardRect: Rect) {
        val height = 48f * density
        val gap = 12f * density
        // The band sits in the empty area outside the shrunk card (One UI edit mode).
        val top = if (addToHomeEdge == 1) {
            cardRect.top - height - gap
        } else {
            cardRect.bottom + gap
        }
        val margin = 40f * density
        val band = RectF(cardRect.left + margin, top, cardRect.right - margin, top + height)
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

    // endregion
}

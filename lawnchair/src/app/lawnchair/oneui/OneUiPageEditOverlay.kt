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

package app.lawnchair.oneui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.Themes
import com.android.launcher3.views.OptionsPopupView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One UI page overview editor (ref: home page edit screenshots).
 *
 * Drawn over the DragLayer while [LauncherState.EDIT_MODE] is active:
 *  - a house button at the very top that marks the current page as the
 *    home-key destination,
 *  - a trash button above the current page card which deletes that page
 *    (with a confirmation when the page still holds items),
 *  - a big plus on the trailing "add page" card (the empty screen appended
 *    while editing); tapping it appends a real page,
 *  - the four quick actions (wallpaper / theme / widgets / settings) along
 *    the bottom, replacing the hotseat while editing.
 *
 * Tapping anywhere on a page card leaves the editor and opens that page,
 * like One UI. All geometry is recomputed per frame from the workspace
 * views, so the buttons follow the scaled page cards.
 */
class OneUiPageEditOverlay(context: Context) : View(context) {

    private val launcher: LawnchairLauncher = Launcher.getLauncher(context) as LawnchairLauncher
    private val workspace get() = launcher.workspace

    private val dp = resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)) Color.BLACK else Color.WHITE
        strokeWidth = 1.8f * dp
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        textSize = 13f * dp
    }
    private val path = Path()

    // Hit zones, recomputed on every draw to stay in sync with the layout.
    private val homeRect = Rect()
    private val trashRect = Rect()
    private val addCardRect = Rect()
    private val bottomRects = arrayOfNulls<Rect>(BOTTOM_ITEMS.size)
    private val pageCardRect = Rect()

    // Edit-mode page rail, drawn by this overlay: ≡ 🏠 ● ● +.
    private val railRect = Rect()
    private val railHitRects = ArrayList<Rect>()
    private val railPageIndexes = ArrayList<Int>()
    private val railPlusRect = Rect()

    private val stateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState == LauncherState.EDIT_MODE) {
                // The trailing "add page" card exists only while editing.
                workspace.addExtraEmptyScreens()
            }
        }

        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState != LauncherState.EDIT_MODE) {
                workspace.removeExtraEmptyScreen(true)
            }
            visibility = if (finalState == LauncherState.EDIT_MODE) VISIBLE else GONE
            // The edit-mode rail is drawn here (the standalone indicator is
            // hidden so they never double up).
            launcher.findViewById<android.view.View>(R.id.page_indicator)?.visibility =
                if (finalState == LauncherState.EDIT_MODE) INVISIBLE else VISIBLE
            invalidate()
        }
    }

    init {
        visibility = GONE
        // Purely visual + tap targets; scrolling stays with the workspace below.
        setWillNotDraw(false)
    }

    fun onAttached() {
        launcher.stateManager.addStateListener(stateListener)
    }

    fun onDetached() {
        launcher.stateManager.removeStateListener(stateListener)
    }

    // region Geometry

    private fun currentPageIndex(): Int = workspace.getNextPage().coerceIn(0, workspace.childCount - 1)

    private fun pageCard(rect: Rect, pageIndex: Int): Boolean {
        if (pageIndex < 0 || pageIndex >= workspace.childCount) return false
        val cell = workspace.getChildAt(pageIndex) ?: return false
        launcher.dragLayer.getDescendantRectRelativeToSelf(cell, tmpRect)
        rect.set(tmpRect)
        return !rect.isEmpty
    }

    private val tmpRect = Rect()

    private fun isAddCardPage(pageIndex: Int): Boolean {
        val cell = workspace.getChildAt(pageIndex) as? com.android.launcher3.CellLayout ?: return false
        return workspace.getCellLayoutId(cell) == com.android.launcher3.Workspace.EXTRA_EMPTY_SCREEN_ID
    }

    // endregion

    // region Draw

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPage = currentPageIndex()

        // House button: top center (mark this page as default home).
        val homeSize = (46f * dp).roundToInt()
        homeRect.set(width / 2 - homeSize / 2, (14f * dp).roundToInt(),
            width / 2 + homeSize / 2, (14f * dp).roundToInt() + homeSize)
        drawHouse(canvas, homeRect.exactCenterX(), homeRect.exactCenterY(), 20f * dp, 20f * dp)

        // Trash button: in the strip the page card visually extends upwards —
        // outside the card content, so it never overlaps widgets like the clock.
        if (pageCard(pageCardRect, currentPage) && !isAddCardPage(currentPage)) {
            val t = (40f * dp).roundToInt()
            val trashCenterY = pageCardRect.top - (26f * dp).roundToInt()
            trashRect.set(
                pageCardRect.centerX() - t / 2,
                trashCenterY - t / 2,
                pageCardRect.centerX() + t / 2,
                trashCenterY + t / 2,
            )
            drawTrash(canvas, trashRect.exactCenterX(), trashRect.exactCenterY(), 11f * dp)
        } else {
            trashRect.setEmpty()
        }

        // Big plus on the trailing add-page card.
        addCardRect.setEmpty()
        for (i in 0 until workspace.childCount) {
            if (isAddCardPage(i)) {
                if (pageCard(pageCardRect, i)) {
                    addCardRect.set(pageCardRect)
                    drawPlus(canvas, pageCardRect.exactCenterX(), pageCardRect.exactCenterY(), 17f * dp)
                }
                break
            }
        }

        // Bottom quick actions.
        val itemW = width / BOTTOM_ITEMS.size
        val bottomH = (72f * dp).roundToInt()
        val inset = launcher.dragLayer.getInsets()
        val bottomY = height - inset.bottom - bottomH - (6f * dp).roundToInt()

        // Edit-mode page rail (≡ 🏠 ● ● +), above the quick actions.
        drawEditRail(canvas, bottomY - (10f * dp).roundToInt())

        for (i in BOTTOM_ITEMS.indices) {
            val rect = bottomRects[i] ?: Rect().also { bottomRects[i] = it }
            rect.set(i * itemW, bottomY, (i + 1) * itemW, bottomY + bottomH)
            val cx = rect.exactCenterX()
            val iconCy = rect.top + 24f * dp
            val labelCy = rect.top + 58f * dp
            when (BOTTOM_ITEMS[i]) {
                0 -> drawWallpaperIcon(canvas, cx, iconCy)
                1 -> drawThemeIcon(canvas, cx, iconCy)
                2 -> drawWidgetIcon(canvas, cx, iconCy)
                else -> drawSettingsIcon(canvas, cx, iconCy)
            }
            paint.style = Paint.Style.FILL
            paint.alpha = 235
            val label = resources.getString(BOTTOM_LABELS[i])
            val tw = paint.measureText(label)
            canvas.drawText(label, cx - tw / 2, labelCy, paint)
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
        }
    }

    private fun drawHouse(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, fill: Boolean = false) {
        val half = w / 2f
        val eave = cy - h / 2 + h * 0.32f
        path.reset()
        path.moveTo(cx - half, cy + h / 2)
        path.lineTo(cx - half, eave)
        path.lineTo(cx, cy - h / 2)
        path.lineTo(cx + half, eave)
        path.lineTo(cx + half, cy + h / 2)
        path.close()
        val oldStyle = paint.style
        paint.style = if (fill) Paint.Style.FILL_AND_STROKE else oldStyle
        canvas.drawPath(path, paint)
        paint.style = oldStyle
    }

    /**
     * The One UI edit-mode page rail: list button, house on the default-home
     * page, dots for the rest (bigger + brighter for the visible page) and a
     * trailing plus that appends a page. Drawn by this overlay because the
     * standalone indicator only knows the non-editing style.
     */
    private fun drawEditRail(canvas: Canvas, centerY: Int) {
        val pages = workspace.childCount
        if (pages <= 0) return
        val gap = 16f * dp
        val dotR = 3.2f * dp
        val defaultPage = workspace.getUserDefaultPage()
        val activePage = currentPageIndex()

        // Evenly spaced markers: [≡][page][page]...[+]. N pages give N+2
        // elements; the whole row stays centred like the One UI rail.
        val elements = pages + 2
        val rowW = (elements - 1) * gap
        val firstCx = width / 2f - rowW / 2f
        val hitH = (44f * dp).roundToInt()
        railRect.set(
            (firstCx - gap / 2f).roundToInt(), centerY - hitH / 2,
            (firstCx + rowW + gap / 2f).roundToInt(), centerY + hitH / 2,
        )
        railHitRects.clear()
        railPageIndexes.clear()

        // List button (negative screen placeholder — the feature itself is
        // deferred; the slot stays so it never collides with the home marker).
        val listCx = firstCx
        drawListButton(canvas, listCx, centerY.toFloat())
        railHitRects.add(Rect(
            (listCx - gap / 2f).roundToInt(), centerY - hitH / 2,
            (listCx + gap / 2f).roundToInt(), centerY + hitH / 2,
        ))
        railPageIndexes.add(-1) // list button: no page action yet

        for (i in 0 until pages) {
            val cx = firstCx + (i + 1) * gap
            val hit = Rect(
                (cx - gap / 2f).roundToInt(), centerY - hitH / 2,
                (cx + gap / 2f).roundToInt(), centerY + hitH / 2,
            )
            railHitRects.add(hit)
            railPageIndexes.add(i)
            if (i == defaultPage) {
                drawHouse(canvas, cx, centerY.toFloat(), 8.6f * dp, 8.4f * dp, fill = true)
            } else {
                paint.style = Paint.Style.FILL
                paint.alpha = if (i == activePage) 235 else 150
                canvas.drawCircle(cx, centerY.toFloat(), if (i == activePage) dotR * 1.25f else dotR, paint)
                paint.alpha = 255
                paint.style = Paint.Style.STROKE
            }
        }

        // Plus.
        val plusCx = firstCx + (pages + 1) * gap
        drawPlus(canvas, plusCx, centerY.toFloat(), 6f * dp)
        railPlusRect.set(
            (plusCx - gap / 2f).roundToInt(), centerY - hitH / 2,
            (plusCx + gap / 2f).roundToInt(), centerY + hitH / 2,
        )
    }

    private fun drawListButton(canvas: Canvas, cx: Float, cy: Float) {
        val half = 4.5f * dp
        val gapY = 3.2f * dp
        paint.alpha = 220
        for (i in -1..1) {
            canvas.drawLine(cx - half, cy + i * gapY, cx + half, cy + i * gapY, paint)
        }
        paint.alpha = 255
    }

    private fun drawTrash(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        // Lid
        canvas.drawLine(cx - half * 0.8f, cy - half * 0.6f, cx + half * 0.8f, cy - half * 0.6f, paint)
        canvas.drawLine(cx, cy - half * 0.95f, cx, cy - half * 0.6f, paint)
        // Body
        path.reset()
        path.moveTo(cx - half * 0.75f, cy - half * 0.35f)
        path.lineTo(cx - half * 0.6f, cy + half)
        path.lineTo(cx + half * 0.6f, cy + half)
        path.lineTo(cx + half * 0.75f, cy - half * 0.35f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawPlus(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        canvas.drawLine(cx - half, cy, cx + half, cy, paint)
        canvas.drawLine(cx, cy - half, cx, cy + half, paint)
    }

    private fun drawWallpaperIcon(canvas: Canvas, cx: Float, cy: Float) {
        val r = 11f * dp
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        canvas.drawRoundRect(rect, 3f * dp, 3f * dp, paint)
        path.reset()
        path.moveTo(cx - r * 0.55f, cy + r * 0.5f)
        path.lineTo(cx - r * 0.1f, cy - r * 0.15f)
        path.lineTo(cx + r * 0.35f, cy + r * 0.5f)
        canvas.drawPath(path, paint)
    }

    private fun drawThemeIcon(canvas: Canvas, cx: Float, cy: Float) {
        // Simplified paint-brush: angled head + handle.
        path.reset()
        path.moveTo(cx - 9f * dp, cy + 9f * dp)
        path.lineTo(cx - 2f * dp, cy + 2f * dp)
        path.lineTo(cx + 2f * dp, cy + 6f * dp)
        path.lineTo(cx - 5f * dp, cy + 13f * dp)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawLine(cx + 1f * dp, cy + 1f * dp, cx + 10f * dp, cy - 8f * dp, paint)
    }

    private fun drawWidgetIcon(canvas: Canvas, cx: Float, cy: Float) {
        val g = 2.2f * dp
        val r = 5.5f * dp
        val rect = RectF()
        for (dx in intArrayOf(-1, 1)) {
            for (dy in intArrayOf(-1, 1)) {
                if (dx == 1 && dy == -1) continue // One UI widget glyph skips top-right cell
                rect.set(cx + dx * g - r, cy + dy * g - r, cx + dx * g + r, cy + dy * g + r)
                canvas.drawRoundRect(rect, 2f * dp, 2f * dp, paint)
            }
        }
        canvas.drawCircle(cx + g + r * 0.2f, cy - g, r * 0.35f, paint)
    }

    private fun drawSettingsIcon(canvas: Canvas, cx: Float, cy: Float) {
        val r = 10f * dp
        // Simplified gear: circle + eight teeth.
        canvas.drawCircle(cx, cy, r * 0.45f, paint)
        val inner = r * 0.72f
        val outer = r
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            canvas.drawLine(
                (cx + inner * Math.cos(angle)).toFloat(), (cy + inner * Math.sin(angle)).toFloat(),
                (cx + outer * Math.cos(angle)).toFloat(), (cy + outer * Math.sin(angle)).toFloat(),
                paint,
            )
        }
    }

    // endregion

    // region Touch

    /** True when the point belongs to one of the overlay's own controls.
     *  Page cards are deliberately NOT claimed so the workspace keeps
     *  horizontal swiping in edit mode; tap-to-open-page lives in Workspace. */
    private fun hitSelf(x: Float, y: Float): Boolean {
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        if (homeRect.contains(xi, yi)) return true
        if (!trashRect.isEmpty && trashRect.contains(xi, yi)) return true
        if (railRect.contains(xi, yi)) return true
        if (y > bottomRects[0]!!.top) return true
        return false
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // Claim the stream only when the press lands on one of the
                // editor's controls; page swipes stay with the workspace.
                return hitSelf(x, y)
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (!hitSelf(x, y)) return false
                when {
                    homeRect.contains(x.roundToInt(), y.roundToInt()) -> {
                        workspace.setUserDefaultPage(currentPageIndex())
                        invalidate()
                        return true
                    }
                    railRect.contains(x.roundToInt(), y.roundToInt()) -> {
                        when {
                            railPlusRect.contains(x.roundToInt(), y.roundToInt()) -> {
                                workspace.addUserPage()
                            }
                            else -> {
                                for (i in railHitRects.indices) {
                                    if (railHitRects[i].contains(x.roundToInt(), y.roundToInt()) &&
                                        railPageIndexes[i] >= 0
                                    ) {
                                        workspace.snapToPage(railPageIndexes[i])
                                        break
                                    }
                                }
                            }
                        }
                        invalidate()
                        return true
                    }
                    !trashRect.isEmpty && trashRect.contains(x.roundToInt(), y.roundToInt()) -> {
                        confirmDeleteCurrentPage()
                        return true
                    }
                    y > bottomRects[0]!!.top -> {
                        for (i in BOTTOM_ITEMS.indices) {
                            val rect = bottomRects[i] ?: continue
                            if (rect.contains(x.roundToInt(), y.roundToInt())) {
                                openBottomAction(i)
                                return true
                            }
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun confirmDeleteCurrentPage() {
        val pageIndex = currentPageIndex()
        if (pageIndex < 0 || pageIndex >= workspace.childCount) return
        if (isAddCardPage(pageIndex)) return
        val cell = workspace.getChildAt(pageIndex) as? com.android.launcher3.CellLayout ?: return
        val itemCount = cell.shortcutsAndWidgets.childCount
        if (itemCount == 0) {
            workspace.removePageAt(pageIndex)
            invalidate()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.oneui_page_delete_title)
            .setMessage(resources.getString(R.string.oneui_page_delete_message, itemCount))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                workspace.removePageAt(pageIndex)
                invalidate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openBottomAction(index: Int) {
        // Mirrors OptionsPopupView's handlers (some are private there): widgets
        // reuses the public helper, wallpaper/settings launch the same intents.
        when (index) {
            0 -> run {
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                launcher.startActivitySafely(this, intent, null)
            }
            1 -> run {
                val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launcher.startActivitySafely(this, intent, null)
            }
            2 -> com.android.launcher3.views.OptionsPopupView.openWidgets(launcher)
            else -> run {
                val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                    .setPackage(launcher.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                launcher.startActivitySafely(this, intent, null)
            }
        }
    }

    // endregion

    companion object {
        // Indices double as stable ids for the drawing switch.
        private val BOTTOM_ITEMS = intArrayOf(0, 1, 2, 3)
        private val BOTTOM_LABELS = intArrayOf(
            R.string.oneui_edit_wallpaper,
            R.string.oneui_edit_theme,
            R.string.oneui_edit_widgets,
            R.string.oneui_edit_settings,
        )
    }
}

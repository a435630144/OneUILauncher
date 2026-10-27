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

package app.lawnchair.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.views.RecyclerViewFastScroller

/**
 * One UI styled fast scroller for the app drawer.
 *
 * Replaces the stock thick accent-coloured bar with the One UI treatment:
 *
 *  - a 3dp translucent track hugging the right edge,
 *  - a thumb that is part of the same line while idle and grows to 6dp
 *    while the user is dragging it,
 *  - a circular blue bubble with the current section letter that pops in
 *    when the drag starts.
 *
 * All the scrolling maths stays in [RecyclerViewFastScroller]; this class only
 * draws the track and positions the bubble.
 */
class OneUiFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerViewFastScroller(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val trackWidth = TRACK_WIDTH_DP * density
    private val thumbWidthIdle = THUMB_WIDTH_IDLE_DP * density
    private val thumbWidthActive = THUMB_WIDTH_ACTIVE_DP * density

    /** 0 = idle, 1 = fully expanded while dragging. */
    private var dragFraction = 0f
    private var expandAnimator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorTokens.TextColorPrimary.resolveColor(context)
        alpha = TRACK_ALPHA
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorTokens.TextColorPrimary.resolveColor(context)
        alpha = THUMB_ALPHA
    }

    private val thumbBounds = RectF()
    private val gestureExclusionRect = Rect()

    private var bubble: TextView? = null

    override fun setPopupView(popupView: TextView) {
        super.setPopupView(popupView)
        bubble = popupView
        popupView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BUBBLE_COLOR)
        }
        popupView.setTextColor(Color.WHITE)
        popupView.scaleX = 0f
        popupView.scaleY = 0f
    }

    override fun handleTouchEvent(ev: MotionEvent, offset: Point): Boolean {
        val wasDragging = isDraggingThumb
        val handled = super.handleTouchEvent(ev, offset)
        val isDragging = isDraggingThumb

        if (!wasDragging && isDragging) {
            bubble?.let { view ->
                view.scaleX = 0.7f
                view.scaleY = 0.7f
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(BUBBLE_POP_DURATION)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        if (wasDragging != isDragging) {
            animateThumbWidth(isDragging)
        }
        return handled
    }

    override fun setThumbOffsetY(y: Int) {
        super.setThumbOffsetY(y)
        positionBubble(y)
    }

    override fun onDraw(canvas: Canvas) {
        val rv = mRv ?: return
        val thumbOffset = thumbOffsetY
        if (thumbOffset < 0) return

        val trackTop = rv.scrollBarTop.toFloat()
        val trackHeight = rv.scrollbarTrackHeight.toFloat()
        if (trackHeight <= 0f) return

        val centerX = width / 2f
        val trackRadius = trackWidth / 2f

        canvas.drawRoundRect(
            centerX - trackRadius,
            trackTop,
            centerX + trackRadius,
            trackTop + trackHeight,
            trackRadius,
            trackRadius,
            trackPaint,
        )

        val thumbWidth = thumbWidthIdle + (thumbWidthActive - thumbWidthIdle) * dragFraction
        val halfThumb = thumbWidth / 2f
        val thumbRadius = halfThumb
        val top = trackTop + thumbOffset
        thumbBounds.set(centerX - halfThumb, top, centerX + halfThumb, top + getThumbHeight())
        canvas.drawRoundRect(thumbBounds, thumbRadius, thumbRadius, thumbPaint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thumbBounds.roundOut(gestureExclusionRect)
            systemGestureExclusionRects = listOf(gestureExclusionRect)
        }
    }

    private fun animateThumbWidth(dragging: Boolean) {
        expandAnimator?.cancel()
        expandAnimator = ValueAnimator.ofFloat(dragFraction, if (dragging) 1f else 0f).apply {
            duration = EXPAND_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dragFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Centers the bubble on the thumb. The bubble shares the scroller's layout
     * anchor, so the translation is measured from the scroller's own top.
     */
    private fun positionBubble(thumbOffset: Int) {
        val view = bubble ?: return
        val rv = mRv ?: return
        if (thumbOffset < 0 || view.height == 0) return

        val trackTop = rv.scrollBarTop
        val trackHeight = rv.scrollbarTrackHeight
        val center = trackTop + thumbOffset + getThumbHeight() / 2f
        val target = (center - view.height / 2f)
            .coerceIn(0f, (trackTop + trackHeight - view.height).toFloat())
        view.translationY = top + target - view.top
    }

    private companion object {
        const val TRACK_WIDTH_DP = 3f
        const val THUMB_WIDTH_IDLE_DP = 3f
        const val THUMB_WIDTH_ACTIVE_DP = 6f
        const val TRACK_ALPHA = 0x1A
        const val THUMB_ALPHA = 0x66
        const val BUBBLE_COLOR = 0xFF3E8EFF.toInt()
        const val EXPAND_DURATION = 150L
        const val BUBBLE_POP_DURATION = 160L
    }
}

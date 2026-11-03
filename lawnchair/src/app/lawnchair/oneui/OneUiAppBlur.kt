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

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import com.android.launcher3.Launcher

/**
 * Blurs the workspace while the app drawer opens (One UI behavior).
 *
 * Uses [RenderEffect], available from API 31; older versions simply keep the
 * dimming scrim without the blur. The effect is only rebuilt when the radius
 * changes by more than 1px so per-frame progress updates stay cheap.
 */
object OneUiAppBlur {

    private const val MAX_RADIUS = 24f
    private var lastRadius = 0f

    /** [revealFraction]: 0 = drawer closed, 1 = drawer fully open. */
    @JvmStatic
    fun apply(launcher: Launcher, revealFraction: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val workspace: android.view.View = launcher.workspace
        val radius = (revealFraction.coerceIn(0f, 1f) * MAX_RADIUS)
        if (radius < 1f) {
            if (lastRadius >= 1f) {
                workspace.setRenderEffect(null)
                lastRadius = 0f
            }
            return
        }
        if (kotlin.math.abs(radius - lastRadius) <= 1f) {
            return
        }
        workspace.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
        lastRadius = radius
    }
}

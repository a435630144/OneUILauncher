/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.launcher3.R

private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * One UI 8 type scale.
 *
 * One UI Sans is proprietary, so we pin Inter (already bundled for the
 * settings UI) which shares the same humanist skeleton and x-height. The
 * important part for the One UI look is the sizing/weight rhythm:
 *
 *  - screen titles       20sp / SemiBold
 *  - preference titles   16sp / Normal
 *  - summaries + captions 13sp / Normal, slightly looser line height
 *  - buttons             15sp / Medium
 */
private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterFontFamily, fontSize = 30.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = InterFontFamily, fontSize = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterFontFamily, fontSize = 22.sp),
    titleLarge = base.titleLarge.copy(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = base.titleSmall.copy(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    bodyLarge = base.bodyLarge.copy(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        fontFamily = InterFontFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = base.bodySmall.copy(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelMedium = base.labelMedium.copy(fontFamily = InterFontFamily, fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontFamily = InterFontFamily, fontSize = 11.sp),
)

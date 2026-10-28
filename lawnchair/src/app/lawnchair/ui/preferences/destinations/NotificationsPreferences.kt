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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorContrastWarning
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import com.android.launcher3.R

@Composable
fun NotificationsPreferences() {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.oneui_settings_notifications),
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            val enabled by remember { notificationDotsEnabled(context) }
                .collectAsStateWithLifecycle(initialValue = false)
            val serviceEnabled = notificationServiceEnabled()
            NotificationDotsPreference(enabled = enabled, serviceEnabled = serviceEnabled)
            if (enabled && serviceEnabled) {
                val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
                ColorPreference(preference = prefs2.notificationDotColor)
                SwitchPreference(
                    adapter = showNotificationCountAdapter,
                    label = stringResource(id = R.string.show_notification_count),
                )
                ExpandAndShrink(visible = showNotificationCountAdapter.state.value) {
                    DividerColumn {
                        ColorPreference(preference = prefs2.notificationDotTextColor)
                        NotificationDotColorContrastWarnings(
                            dotColor = prefs2.notificationDotColor.asState().value,
                            dotTextColor = prefs2.notificationDotTextColor.asState().value,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationDotColorContrastWarnings(
    dotColor: ColorOption,
    dotTextColor: ColorOption,
    modifier: Modifier = Modifier,
) {
    val dotColorIsDynamic = when (dotColor) {
        is ColorOption.SystemAccent,
        is ColorOption.WallpaperPrimary,
        is ColorOption.Default,
        -> true

        else -> false
    }

    if (dotColorIsDynamic && dotTextColor !is ColorOption.Default) {
        WarningPreference(
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_sometimes),
            modifier = modifier,
        )
    } else {
        ColorContrastWarning(
            foregroundColor = dotTextColor,
            backgroundColor = dotColor,
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_always),
            modifier = modifier,
        )
    }
}

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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R

/**
 * App drawer sort mode plus the editor for the custom order.
 *
 * The drawer itself can only show the result; reordering happens here because
 * the all-apps grid is driven by [com.android.launcher3.allapps.AlphabeticalAppsList]
 * and has no drag-reorder support of its own yet.
 */
@Composable
fun AppDrawerSortPreference(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val sortMode by prefs.drawerSortMode.getAdapter().state
    val customOrder by prefs.drawerCustomOrder.getAdapter().state
    val apps by appsState()

    val orderedApps = remember(apps, customOrder) {
        orderApps(apps, customOrder)
    }

    LoadingScreen(
        isLoading = apps.isEmpty(),
        modifier = modifier,
    ) {
        PreferenceLayout(
            label = stringResource(R.string.oneui_drawer_sort),
            backArrowVisible = true,
        ) {
            PreferenceGroup {
                ListPreference(
                    adapter = prefs.drawerSortMode.getAdapter(),
                    entries = listOf(
                        ListPreferenceEntry(LawnchairAlphabeticalAppsList.SORT_ALPHABETICAL) {
                            stringResource(R.string.oneui_drawer_sort_alphabetical)
                        },
                        ListPreferenceEntry(LawnchairAlphabeticalAppsList.SORT_CUSTOM) {
                            stringResource(R.string.oneui_drawer_sort_custom)
                        },
                    ),
                    label = stringResource(R.string.oneui_drawer_sort_mode),
                )
            }

            if (sortMode == LawnchairAlphabeticalAppsList.SORT_CUSTOM) {
                Text(
                    text = stringResource(R.string.oneui_drawer_sort_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                ReorderablePreferenceGroup(
                    label = stringResource(R.string.oneui_drawer_sort_edit_order),
                    items = orderedApps,
                    defaultList = apps,
                    onOrderChange = { list ->
                        prefs.drawerCustomOrder.set(
                            list.joinToString(separator = "\n") { it.key.toString() },
                        )
                    },
                ) { app, _, _, onDraggingChange ->
                    val interactionSource = remember { MutableInteractionSource() }
                    AppItem(
                        label = app.label,
                        icon = app.icon,
                        onClick = {},
                        endWidget = {
                            ReorderableDragHandle(
                                interactionSource = interactionSource,
                                scope = this,
                                onDragStop = { onDraggingChange(false) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sorts [apps] by the saved component-key order. Apps missing from the saved
 * list (new installs) keep their alphabetical position at the end.
 */
private fun orderApps(apps: List<App>, savedOrder: String): List<App> {
    if (savedOrder.isBlank()) return apps

    val index = HashMap<String, Int>()
    savedOrder.lineSequence().forEachIndexed { i, key ->
        if (key.isNotBlank()) index.putIfAbsent(key, i)
    }
    if (index.isEmpty()) return apps

    return apps.sortedBy { index[it.key.toString()] ?: Int.MAX_VALUE }
}

package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.navigation.About
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.ui.preferences.navigation.DebugMenu
import app.lawnchair.ui.preferences.navigation.Dock
import app.lawnchair.ui.preferences.navigation.Folders
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.Gestures
import app.lawnchair.ui.preferences.navigation.HomeScreen
import app.lawnchair.ui.preferences.navigation.Notifications
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.Quickstep
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.ui.util.addIf
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

/**
 * One UI style settings dashboard: grouped cards with text-only rows, no
 * leading icons, one card per task area (layout / appearance / interaction).
 */
@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SyncLiveInformation()
    val pref2 = preferenceManager2()
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val highlightShape = MaterialTheme.shapes.large

    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        backArrowVisible = false,
        actions = {
            if (enableDebug) {
                ClickableIcon(
                    imageVector = Icons.Rounded.Build,
                    onClick = { onNavigate(DebugMenu) },
                    modifier = Modifier.addIf(currentRoute == DebugMenu) {
                        Modifier
                            .clip(highlightShape)
                            .background(highlightColor)
                    },
                )
            }
        },
    ) {
        if (!context.isDefaultLauncher()) {
            PreferencesSetDefaultLauncherWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        PreferenceGroup(heading = stringResource(R.string.layout)) {
            NavigationActionPreference(
                label = stringResource(R.string.home_screen_label),
                subtitle = stringResource(R.string.home_screen_description),
                destination = HomeScreen,
            )

            val deckLayout = pref2.deckLayout.getAdapter()
            if (!deckLayout.state.value) {
                NavigationActionPreference(
                    label = stringResource(R.string.app_drawer_label),
                    subtitle = stringResource(R.string.app_drawer_description),
                    destination = AppDrawer,
                )
            }

            NavigationActionPreference(
                label = stringResource(R.string.dock_label),
                subtitle = stringResource(R.string.dock_description),
                destination = Dock,
            )

            NavigationActionPreference(
                label = stringResource(R.string.folders_label),
                subtitle = stringResource(R.string.folders_description),
                destination = Folders,
            )
        }

        PreferenceGroup(heading = stringResource(R.string.oneui_settings_section_look)) {
            NavigationActionPreference(
                label = stringResource(R.string.oneui_settings_appearance),
                subtitle = stringResource(R.string.general_description),
                destination = General,
            )

            NavigationActionPreference(
                label = stringResource(R.string.smartspace_widget),
                subtitle = stringResource(R.string.smartspace_widget_description),
                destination = Smartspace,
            )

            NavigationActionPreference(
                label = stringResource(R.string.search_bar_label),
                subtitle = stringResource(R.string.drawer_search_description),
                destination = Search(),
            )

            NavigationActionPreference(
                label = stringResource(R.string.oneui_settings_notifications),
                subtitle = stringResource(R.string.notification_dots),
                destination = Notifications,
            )
        }

        PreferenceGroup(heading = stringResource(R.string.oneui_settings_section_controls)) {
            NavigationActionPreference(
                label = stringResource(R.string.gestures_label),
                subtitle = stringResource(R.string.gestures_description),
                destination = Gestures,
            )

            if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
                NavigationActionPreference(
                    label = stringResource(R.string.quickstep_label),
                    subtitle = stringResource(R.string.quickstep_description),
                    destination = Quickstep,
                )
            }
        }

        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(R.string.about_label),
                subtitle = "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}",
                destination = About,
            )
        }
    }
}

@Composable
fun PreferencesSetDefaultLauncherWarning(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable {
                Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let { context.startActivity(it) }
                (context as? Activity)?.finish()
            },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.set_default_launcher_tip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}

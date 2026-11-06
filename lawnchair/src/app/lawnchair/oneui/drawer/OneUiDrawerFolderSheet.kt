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

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One UI style drawer folder panel.
 *
 * Shows the folder members in a 4-column grid. The header toggles an edit mode
 * where each member gets a red minus badge, a trailing "+" cell opens a
 * multi-select app picker, the title becomes tappable for renaming and a
 * "delete folder" action appears at the bottom.
 *
 * All mutations are written straight to [PreferenceManager.drawerFolders] /
 * [PreferenceManager.drawerCustomOrder] and reported through [onChanged] so
 * the paged grid can rebuild itself.
 */
class OneUiDrawerFolderSheet(
    private val context: Context,
    private val folderId: String,
    private val appsByKey: Map<String, AppInfo>,
    private val onChanged: () -> Unit,
) {

    private val prefs = PreferenceManager.getInstance(context)
    private val density = context.resources.displayMetrics.density
    private val dm = context.resources.displayMetrics
    private val nightMode = (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val panelColor = if (nightMode) 0xFF202024.toInt() else 0xFFF7F7FA.toInt()
    private val primaryText = if (nightMode) 0xFFF2F2F2.toInt() else 0xFF1B1B1F.toInt()
    private val secondaryText = if (nightMode) 0xFFA8A8AE.toInt() else 0xFF6E6E73.toInt()
    private val accent = 0xFF3E91FF.toInt()
    private val danger = 0xFFFF5252.toInt()

    private var folder: DrawerFolderStore.Folder? = null
    private var dialog: Dialog? = null
    private var editing = false
    private var panelWidth = 0

    private lateinit var titleView: TextView
    private lateinit var editView: TextView
    private lateinit var renameHint: TextView
    private lateinit var grid: GridLayout
    private lateinit var gridScroll: ScrollView
    private lateinit var deleteView: TextView
    private lateinit var divider: View

    fun show() {
        folder = DrawerFolderStore.loadFolders(prefs)[folderId] ?: return
        panelWidth = min((dm.widthPixels * 0.92f).roundToInt(), dp(420))
        buildDialog()
        dialog?.show()
    }

    private fun buildDialog() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
            background = rounded(panelColor, dp(28))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(context).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(primaryText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { if (editing) showRenameDialog() }
        }
        editView = TextView(context).apply {
            textSize = 15f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), 0, dp(6))
            setOnClickListener { toggleEditMode() }
        }
        header.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(editView)
        panel.addView(header)

        renameHint = TextView(context).apply {
            text = context.getString(com.android.launcher3.R.string.oneui_drawer_folder_rename_hint)
            textSize = 12f
            setTextColor(secondaryText)
            visibility = View.GONE
        }
        panel.addView(
            renameHint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(2) },
        )

        grid = GridLayout(context).apply { columnCount = 4 }
        gridScroll = ScrollView(context).apply {
            isFillViewport = false
            addView(
                grid,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        panel.addView(
            gridScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        divider = View(context).apply {
            setBackgroundColor(if (nightMode) 0x1FFFFFFF else 0x14000000)
            visibility = View.GONE
        }
        panel.addView(
            divider,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(4)
            },
        )

        deleteView = TextView(context).apply {
            text = context.getString(com.android.launcher3.R.string.oneui_drawer_folder_delete)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(danger)
            setPadding(0, dp(14), 0, dp(14))
            visibility = View.GONE
            setOnClickListener { confirmDeleteFolder() }
        }
        panel.addView(deleteView)

        refreshContent()

        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(panel)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.55f }
                setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            }
            setOnShowListener {
                window?.setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun refreshContent() {
        val f = folder ?: return
        titleView.text = f.title.ifBlank {
            context.getString(com.android.launcher3.R.string.oneui_drawer_folder_name)
        }
        editView.text = context.getString(
            if (editing) {
                com.android.launcher3.R.string.oneui_drawer_folder_done
            } else {
                com.android.launcher3.R.string.oneui_drawer_folder_edit
            },
        )
        divider.visibility = if (editing) View.VISIBLE else View.GONE
        deleteView.visibility = if (editing) View.VISIBLE else View.GONE
        renameHint.visibility = if (editing) View.VISIBLE else View.GONE

        grid.removeAllViews()
        val cellW = (panelWidth - dp(40)) / 4
        val cellH = dp(92)
        for (key in f.keys) {
            val app = appsByKey[key] ?: continue
            grid.addView(memberCell(app, key, cellW, cellH))
        }
        if (editing) {
            grid.addView(addCell(cellW, cellH))
        }

        val rows = ceil(grid.childCount / 4.0).toInt().coerceAtLeast(1)
        val maxHeight = (dm.heightPixels * 0.55f).toInt()
        gridScroll.layoutParams = gridScroll.layoutParams.apply {
            height = min(rows * cellH, maxHeight)
        }
        gridScroll.requestLayout()
    }

    private fun memberCell(app: AppInfo, key: String, width: Int, height: Int): View {
        val cell = FrameLayout(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val icon = ImageView(context).apply {
            setImageBitmap(app.bitmap.icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        column.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val label = TextView(context).apply {
            text = app.title
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        column.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        cell.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        cell.tag = app
        cell.setOnClickListener { view ->
            if (editing) return@setOnClickListener
            // Let the shared handler record the launch and animate it, then
            // close the sheet (the handler needs the view's window token).
            ItemClickHandler.INSTANCE.onClick(view)
            dismiss()
        }
        if (editing) {
            val badge = TextView(context).apply {
                text = "\u2212"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = oval(danger)
                setOnClickListener { removeMember(key) }
            }
            val size = dp(22)
            cell.addView(
                badge,
                FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(2)
                    rightMargin = dp(10)
                },
            )
        }
        cell.layoutParams = GridLayout.LayoutParams().apply {
            this.width = width
            this.height = height
        }
        return cell
    }

    private fun addCell(width: Int, height: Int): View {
        val cell = FrameLayout(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val plus = TextView(context).apply {
            text = "+"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(secondaryText)
            background = oval(if (nightMode) 0x24FFFFFF else 0x14000000)
        }
        column.addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)))
        val label = TextView(context).apply {
            text = context.getString(com.android.launcher3.R.string.oneui_drawer_folder_add)
            textSize = 12f
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(secondaryText)
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        column.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        cell.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        cell.setOnClickListener { showAddPicker() }
        cell.layoutParams = GridLayout.LayoutParams().apply {
            this.width = width
            this.height = height
        }
        return cell
    }

    private fun toggleEditMode() {
        editing = !editing
        refreshContent()
    }

    private fun removeMember(key: String) {
        val folders = DrawerFolderStore.loadFolders(prefs)
        val stored = folders[folderId] ?: return
        if (!stored.keys.remove(key)) return
        if (stored.keys.size <= 1) {
            // A folder must keep at least two apps: dissolve it and put the
            // remaining member plus the removed one back next to each other.
            val remaining = stored.keys.toList() + key
            DrawerFolderStore.dissolveFolderToken(prefs, stored.id, remaining)
            folders.remove(stored.id)
            DrawerFolderStore.saveFolders(prefs, folders)
            Toast.makeText(
                context,
                com.android.launcher3.R.string.oneui_drawer_folder_dissolved,
                Toast.LENGTH_SHORT,
            ).show()
            dismiss()
            onChanged()
            return
        }
        // Put the app back right after the folder token.
        DrawerFolderStore.insertAppsAfterFolder(prefs, stored.id, listOf(key))
        DrawerFolderStore.saveFolders(prefs, folders)
        folder = stored
        refreshContent()
        onChanged()
    }

    private fun showAddPicker() {
        val folder = folder ?: return
        val folders = DrawerFolderStore.loadFolders(prefs)
        val used = folders.values.flatMap { it.keys }.toSet()
        val candidates = appsByKey.values
            .filter { it.toComponentKey().toString() !in used }
            .sortedBy { it.title.toString().lowercase() }
        if (candidates.isEmpty()) {
            Toast.makeText(
                context,
                com.android.launcher3.R.string.oneui_drawer_folder_no_apps,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val labels = candidates.map { it.title.toString() }.toTypedArray()
        val checked = BooleanArray(candidates.size)
        AlertDialog.Builder(context)
            .setTitle(com.android.launcher3.R.string.oneui_drawer_folder_add_apps)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val keys = candidates.filterIndexed { index, _ -> checked[index] }
                    .map { it.toComponentKey().toString() }
                if (keys.isNotEmpty()) addApps(keys)
            }
            .show()
    }

    private fun addApps(keys: List<String>) {
        val folders = DrawerFolderStore.loadFolders(prefs)
        val stored = folders[folderId] ?: return
        DrawerFolderStore.detachApps(prefs, keys)
        for (key in keys) {
            if (key !in stored.keys) stored.keys.add(key)
        }
        DrawerFolderStore.saveFolders(prefs, folders)
        stored.refreshPlaceholder()
        folder = stored
        refreshContent()
        onChanged()
    }

    private fun showRenameDialog() {
        val folder = folder ?: return
        val input = EditText(context).apply {
            setText(
                folder.title.ifBlank {
                    context.getString(com.android.launcher3.R.string.oneui_drawer_folder_name)
                },
            )
            inputType = InputType.TYPE_CLASS_TEXT
            selectAll()
        }
        AlertDialog.Builder(context)
            .setTitle(com.android.launcher3.R.string.oneui_drawer_folder_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = input.text.toString().trim().ifBlank {
                    context.getString(com.android.launcher3.R.string.oneui_drawer_folder_name)
                }
                folder.title = title
                val folders = DrawerFolderStore.loadFolders(prefs)
                folders[folder.id]?.title = title
                DrawerFolderStore.saveFolders(prefs, folders)
                refreshContent()
                onChanged()
            }
            .show()
    }

    private fun confirmDeleteFolder() {
        val folder = folder ?: return
        AlertDialog.Builder(context)
            .setTitle(com.android.launcher3.R.string.oneui_drawer_folder_delete)
            .setMessage(com.android.launcher3.R.string.oneui_drawer_folder_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                DrawerFolderStore.dissolveFolderToken(prefs, folder.id, folder.keys.toList())
                val folders = DrawerFolderStore.loadFolders(prefs)
                folders.remove(folder.id)
                DrawerFolderStore.saveFolders(prefs, folders)
                dismiss()
                onChanged()
            }
            .show()
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
}

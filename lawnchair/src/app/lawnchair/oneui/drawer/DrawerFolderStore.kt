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

import app.lawnchair.preferences.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage for One UI drawer folders.
 *
 * The drawer's custom order ([app.lawnchair.preferences.PreferenceManager.drawerCustomOrder])
 * is a newline-separated sequence of tokens:
 *  - `K:<componentKey>`  a single app (the `K:` prefix is optional for
 *    backwards compatibility with the old prefix-less lines),
 *  - `F:<folderId>`      a drawer folder whose contents live in
 *    [app.lawnchair.preferences.PreferenceManager.drawerFolders] as JSON:
 *    `{"<id>": {"title": "...", "keys": ["<componentKey>", ...]}}`.
 *
 * Folders are rendered through a placeholder app (their first member) in the
 * editing order so the drag pipeline keeps working with plain [com.android.launcher3.model.data.AppInfo].
 */
object DrawerFolderStore {

    const val PREFIX_APP = "K:"
    const val PREFIX_FOLDER = "F:"

    class Folder(val id: String, var title: String, val keys: MutableList<String>) {
        /** The app shown for this folder in the order list and in drag flows. */
        var placeholderKey: String? = null
            private set

        fun refreshPlaceholder() {
            placeholderKey = keys.firstOrNull()
        }
    }

    fun loadFolders(prefs: PreferenceManager): MutableMap<String, Folder> {
        val out = LinkedHashMap<String, Folder>()
        val raw = prefs.drawerFolders.get()
        if (raw.isBlank()) return out
        runCatching {
            val root = JSONObject(raw)
            val ids = root.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val obj = root.getJSONObject(id)
                val keys = mutableListOf<String>()
                val arr = obj.optJSONArray("keys") ?: JSONArray()
                for (i in 0 until arr.length()) keys.add(arr.getString(i))
                val folder = Folder(id, obj.optString("title", ""), keys)
                folder.refreshPlaceholder()
                out[id] = folder
            }
        }
        return out
    }

    fun saveFolders(prefs: PreferenceManager, folders: Map<String, Folder>) {
        val root = JSONObject()
        for ((id, folder) in folders) {
            if (folder.keys.isEmpty()) continue
            val obj = JSONObject()
            obj.put("title", folder.title)
            obj.put("keys", JSONArray(folder.keys))
            root.put(id, obj)
        }
        prefs.drawerFolders.set(root.toString())
    }

    /** Splits the saved order into (tokenType, value) pairs, type being "K" or "F". */
    fun parseOrder(raw: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            when {
                trimmed.startsWith(PREFIX_FOLDER) -> out.add("F" to trimmed.substring(PREFIX_FOLDER.length))
                trimmed.startsWith(PREFIX_APP) -> out.add("K" to trimmed.substring(PREFIX_APP.length))
                else -> out.add("K" to trimmed)
            }
        }
        return out
    }

    /** Serialises the in-memory order; folders are written as `F:<id>` rows. */
    fun writeOrder(
        ordered: List<Any>,
        folderIdOf: (Any) -> String?,
        keyOf: (Any) -> String,
    ): String {
        return ordered.joinToString("\n") { entry ->
            val folderId = folderIdOf(entry)
            if (folderId != null) PREFIX_FOLDER + folderId else PREFIX_APP + keyOf(entry)
        }
    }
}

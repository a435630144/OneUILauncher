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

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import org.json.JSONObject

/**
 * Tracks how often apps are launched from the drawer so the "most used" sort
 * mode can rank them without any special permissions.
 *
 * Counts live in a single JSON preference; the preference's change listener
 * (refreshAllApps) refreshes the drawer whenever a count changes.
 */
object LaunchCounter {

    private const val MAX_TRACKED = 200

    @JvmStatic
    fun increment(context: Context, key: String) {
        val counts = readCounts(context)
        counts.put(key, (counts.optInt(key, 0) + 1).coerceAtMost(9999))
        writeCounts(context, counts)
    }

    fun count(context: Context, key: String): Int = readCounts(context).optInt(key, 0)

    /** Snapshot of all launch counts, for sort comparators. */
    fun snapshot(context: Context): Map<String, Int> {
        val json = readCounts(context)
        val out = HashMap<String, Int>()
        json.keys().forEach { k -> out[k] = json.optInt(k, 0) }
        return out
    }

    private fun readCounts(context: Context): JSONObject = try {
        JSONObject(PreferenceManager.getInstance(context).appLaunchCounts.get())
    } catch (_: Exception) {
        JSONObject()
    }

    private fun writeCounts(context: Context, counts: JSONObject) {
        // Keep the map bounded: drop the least used entries past the cap.
        val trimmed = JSONObject()
        val entries = mutableListOf<Pair<String, Int>>()
        counts.keys().forEach { k -> entries.add(k to counts.optInt(k, 0)) }
        entries.sortByDescending { it.second }
        entries.take(MAX_TRACKED).forEach { (k, v) -> trimmed.put(k, v) }
        PreferenceManager.getInstance(context).appLaunchCounts.set(trimmed.toString())
    }
}

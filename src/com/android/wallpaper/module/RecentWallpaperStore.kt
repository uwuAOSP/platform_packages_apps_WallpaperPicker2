/*
 * SPDX-FileCopyrightText: The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.wallpaper.module

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Stores the small set of wallpapers exposed by the launcher quick switcher. */
class RecentWallpaperStore(private val context: android.content.Context) {

    data class Entry(
        val id: String,
        val placeholderColor: Int,
        val lastUpdated: Long,
        val title: String?,
        val fileName: String?,
        val component: String?,
    )

    fun getRecentWallpapers(destination: Int): List<Entry> = readEntries(keyFor(destination))

    fun saveStaticWallpaper(
        which: Int,
        wallpaperId: String,
        bitmap: Bitmap,
        colors: android.app.WallpaperColors?,
        title: String?,
    ) {
        val fileName = fileNameFor(wallpaperId)
        val file = File(storageDirectory, fileName)
        file.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return
        }
        val entry = Entry(
            id = wallpaperId,
            placeholderColor = colors?.primaryColor?.toArgb() ?: Color.TRANSPARENT,
            lastUpdated = System.currentTimeMillis(),
            title = title,
            fileName = fileName,
            component = null,
        )
        if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
            saveEntry(WallpaperManager.FLAG_SYSTEM, entry)
        }
        if ((which and WallpaperManager.FLAG_LOCK) != 0) {
            saveEntry(WallpaperManager.FLAG_LOCK, entry)
        }
    }

    fun saveLiveWallpaper(
        which: Int,
        wallpaperId: String,
        component: String?,
        colors: android.app.WallpaperColors?,
        title: String?,
    ) {
        val entry = Entry(
            id = wallpaperId,
            placeholderColor = colors?.primaryColor?.toArgb() ?: Color.TRANSPARENT,
            lastUpdated = System.currentTimeMillis(),
            title = title,
            fileName = null,
            component = component,
        )
        if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
            saveEntry(WallpaperManager.FLAG_SYSTEM, entry)
        }
        if ((which and WallpaperManager.FLAG_LOCK) != 0) {
            saveEntry(WallpaperManager.FLAG_LOCK, entry)
        }
    }

    fun markApplied(destination: Int, wallpaperId: String): Boolean {
        val key = keyFor(destination)
        val entries = readEntries(key)
        val selected = entries.firstOrNull { it.id == wallpaperId } ?: return false
        writeEntries(key, listOf(selected) + entries.filterNot { it.id == wallpaperId })
        return true
    }

    fun fileFor(entry: Entry): File? = entry.fileName?.let { File(storageDirectory, it) }

    private fun saveEntry(destination: Int, entry: Entry) {
        val key = keyFor(destination)
        val entries = readEntries(key)
        writeEntries(key, (listOf(entry) + entries.filterNot { it.id == entry.id }).take(MAX_ENTRIES))
    }

    private fun readEntries(key: String): List<Entry> {
        val value = preferences.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(value)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val objectValue = array.getJSONObject(index)
                    val id = objectValue.optString(JSON_ID)
                    if (TextUtils.isEmpty(id)) continue
                    add(
                        Entry(
                            id = id,
                            placeholderColor = objectValue.optInt(JSON_COLOR, Color.TRANSPARENT),
                            lastUpdated = objectValue.optLong(JSON_UPDATED, 0L),
                            title = objectValue.optString(JSON_TITLE).takeUnless { it.isEmpty() },
                            fileName = objectValue.optString(JSON_FILE).takeUnless { it.isEmpty() },
                            component = objectValue.optString(JSON_COMPONENT)
                                .takeUnless { it.isEmpty() },
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(key: String, entries: List<Entry>) {
        val array = JSONArray()
        entries.take(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject()
                    .put(JSON_ID, entry.id)
                    .put(JSON_COLOR, entry.placeholderColor)
                    .put(JSON_UPDATED, entry.lastUpdated)
                    .put(JSON_TITLE, entry.title ?: "")
                    .put(JSON_FILE, entry.fileName ?: "")
                    .put(JSON_COMPONENT, entry.component ?: "")
            )
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun keyFor(destination: Int): String = when {
        (destination and WallpaperManager.FLAG_LOCK) != 0 &&
            (destination and WallpaperManager.FLAG_SYSTEM) == 0 -> KEY_LOCK
        else -> KEY_HOME
    }

    private fun fileNameFor(id: String): String =
        "${Integer.toHexString(id.hashCode())}.png"

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)

    private val storageDirectory = File(context.filesDir, STORAGE_DIRECTORY).apply { mkdirs() }

    companion object {
        private const val MAX_ENTRIES = 5
        private const val PREFERENCES_NAME = "wallpaper"
        private const val STORAGE_DIRECTORY = "recent_wallpapers"
        private const val KEY_HOME = "launcher_recent_wallpapers_home"
        private const val KEY_LOCK = "launcher_recent_wallpapers_lock"
        private const val JSON_ID = "id"
        private const val JSON_COLOR = "color"
        private const val JSON_UPDATED = "updated"
        private const val JSON_TITLE = "title"
        private const val JSON_FILE = "file"
        private const val JSON_COMPONENT = "component"
    }
}

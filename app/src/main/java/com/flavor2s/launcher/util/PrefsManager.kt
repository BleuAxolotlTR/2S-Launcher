package com.flavor2s.launcher.util

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SharedPreferences üzerinden Essentials yuvaları, gizli uygulamalar,
 * duvar kağıdı URI, arka plan rengi ve Kilit Ekranı PIN kodunu saklar.
 */
class PrefsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "launcher_2s_prefs"
        private const val KEY_ESSENTIALS = "essentials_slots"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_WALLPAPER_OFFSET_X = "wallpaper_offset_x"
        private const val KEY_WALLPAPER_OFFSET_Y = "wallpaper_offset_y"
        private const val KEY_BG_COLOR = "background_color"
        private const val KEY_LOCK_PIN = "lock_pin"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        const val SLOT_COUNT = 6  // Exactly 6 Essentials slots
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ======================== ESSENTIALS SLOTS ========================

    fun saveEssentials(slots: Array<String?>) {
        val json = gson.toJson(slots.toList())
        prefs.edit().putString(KEY_ESSENTIALS, json).apply()
    }

    fun loadEssentials(): Array<String?> {
        val json = prefs.getString(KEY_ESSENTIALS, null) ?: return arrayOfNulls<String>(SLOT_COUNT)
        return try {
            val type = object : TypeToken<List<String?>>() {}.type
            val list: List<String?> = gson.fromJson(json, type)
            Array(SLOT_COUNT) { index -> list.getOrNull(index) }
        } catch (e: Exception) {
            arrayOfNulls<String>(SLOT_COUNT)
        }
    }

    // ======================== HIDDEN APPS ========================

    fun saveHiddenApps(packageNames: Set<String>) {
        val json = gson.toJson(packageNames.toList())
        prefs.edit().putString(KEY_HIDDEN_APPS, json).apply()
    }

    fun loadHiddenApps(): MutableSet<String> {
        val json = prefs.getString(KEY_HIDDEN_APPS, null) ?: return mutableSetOf()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, type)
            list.toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    fun hideApp(packageName: String) {
        val hidden = loadHiddenApps()
        hidden.add(packageName)
        saveHiddenApps(hidden)
    }

    fun unhideApp(packageName: String) {
        val hidden = loadHiddenApps()
        hidden.remove(packageName)
        saveHiddenApps(hidden)
    }

    fun isHidden(packageName: String): Boolean {
        return loadHiddenApps().contains(packageName)
    }

    // ======================== WALLPAPER & POSITIONING ========================

    fun saveWallpaperUri(uri: String?) {
        prefs.edit().putString(KEY_WALLPAPER_URI, uri).apply()
    }

    fun loadWallpaperUri(): String? {
        return prefs.getString(KEY_WALLPAPER_URI, null)
    }

    fun saveWallpaperOffset(offsetX: Float, offsetY: Float) {
        prefs.edit()
            .putFloat(KEY_WALLPAPER_OFFSET_X, offsetX)
            .putFloat(KEY_WALLPAPER_OFFSET_Y, offsetY)
            .apply()
    }

    fun loadWallpaperOffsetX(): Float = prefs.getFloat(KEY_WALLPAPER_OFFSET_X, 0f)
    fun loadWallpaperOffsetY(): Float = prefs.getFloat(KEY_WALLPAPER_OFFSET_Y, 0f)

    // ======================== BACKGROUND COLOR ========================

    fun saveBackgroundColor(color: Int) {
        prefs.edit().putInt(KEY_BG_COLOR, color).apply()
    }

    fun loadBackgroundColor(): Int {
        return prefs.getInt(KEY_BG_COLOR, Color.parseColor("#141424"))
    }

    // ======================== LOCK SCREEN ========================

    fun setLockScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    fun isLockScreenEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_ENABLED, true)
    }

    fun saveLockPin(pin: String) {
        prefs.edit().putString(KEY_LOCK_PIN, pin).apply()
    }

    fun getLockPin(): String {
        return prefs.getString(KEY_LOCK_PIN, "") ?: ""
    }
}

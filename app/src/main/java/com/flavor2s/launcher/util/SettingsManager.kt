package com.flavor2s.launcher.util

import android.content.Context

/**
 * SettingsManager — launcher appearance & behaviour preferences.
 *
 * Backed by SharedPreferences for synchronous access from the main thread.
 * Adding a new setting is a two-line change: add a KEY_ constant and a pair
 * of save/load functions.
 *
 * Planned settings (all optional — sane defaults work on first launch):
 *   - divider_weight        : float, weight of the Essentials pane (Others gets 2-weight)
 *   - show_app_names        : boolean, show labels under icons in Others
 *   - icon_shape            : string enum, "rounded_square" | "circle" | "squircle"
 */
class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "launcher_2s_settings"

        // ── Keys ────────────────────────────────────────────────
        const val KEY_DIVIDER_WEIGHT    = "divider_weight"
        const val KEY_SHOW_APP_NAMES    = "show_app_names"
        const val KEY_ICON_SHAPE        = "icon_shape"
        const val KEY_TRANSPARENCY_MODE  = "transparency_mode"
        const val KEY_TRANSPARENCY_ALPHA = "transparency_alpha"
        // Accent color settings removed – launcher now always uses dynamic system colors
        const val KEY_THEME_MODE         = "theme_mode"

        // ── Defaults ─────────────────────────────────────────────
        const val DEFAULT_DIVIDER_WEIGHT    = 0.80f   // Essentials pane weight (others = ~1.20)
        const val DEFAULT_SHOW_APP_NAMES    = true
        const val DEFAULT_ICON_SHAPE        = IconShape.ROUNDED_SQUARE
        const val DEFAULT_TRANSPARENCY_ALPHA = 0.80f
        // Default accent colors removed – using system dynamic colors
        const val DEFAULT_THEME_MODE        = ThemeMode.SYSTEM
    }

    /** Icon shape options — extend here without touching other code. */
    object IconShape {
        const val ROUNDED_SQUARE = "rounded_square"
        const val CIRCLE         = "circle"
        const val SQUIRCLE       = "squircle"
    }



    /** Theme mode options — System Default, Light, Dark */
    object ThemeMode {
        const val SYSTEM = "system"
        const val LIGHT  = "light"
        const val DARK   = "dark"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Divider ─────────────────────────────────────────────────────────────

    /**
     * The layout_weight of the Essentials pane.
     * Valid range: 0.3 .. 1.7 (total weight of both panes is 2.0).
     */
    var dividerWeight: Float
        get() = prefs.getFloat(KEY_DIVIDER_WEIGHT, DEFAULT_DIVIDER_WEIGHT)
        set(value) { prefs.edit().putFloat(KEY_DIVIDER_WEIGHT, value.coerceIn(0.3f, 1.7f)).apply() }

    /** Weight of the Others pane, derived so the two panes always sum to 2.0. */
    val othersPaneWeight: Float get() = 2.0f - dividerWeight

    // ── App name labels ──────────────────────────────────────────────────────

    var showAppNames: Boolean
        get() = prefs.getBoolean(KEY_SHOW_APP_NAMES, DEFAULT_SHOW_APP_NAMES)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_APP_NAMES, value).apply() }

    // ── Icon shape ───────────────────────────────────────────────────────────

    var iconShape: String
        get() = prefs.getString(KEY_ICON_SHAPE, DEFAULT_ICON_SHAPE) ?: DEFAULT_ICON_SHAPE
        set(value) { prefs.edit().putString(KEY_ICON_SHAPE, value).apply() }

    // ── Transparency ──────────────────────────────────────────────────────────

    /**
     * Opacity of surface containers (Essentials, Others, strips).
     * Range: 0.30 (very transparent) .. 1.0 (fully opaque). Default = 0.85.
     */
    var transparencyAlpha: Float
        get() = prefs.getFloat(KEY_TRANSPARENCY_ALPHA, DEFAULT_TRANSPARENCY_ALPHA)
        set(value) { prefs.edit().putFloat(KEY_TRANSPARENCY_ALPHA, value.coerceIn(0.30f, 1.0f)).apply() }

    // ── Accent Color ─────────────────────────────────────────────────────────

    // Accent color properties removed – system dynamic colors are used

    // ── Theme Mode ───────────────────────────────────────────────────────────

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        set(value) { prefs.edit().putString(KEY_THEME_MODE, value).apply() }
}

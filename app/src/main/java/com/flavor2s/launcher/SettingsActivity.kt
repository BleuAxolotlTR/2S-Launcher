package com.flavor2s.launcher

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flavor2s.launcher.databinding.ActivitySettingsBinding
import com.flavor2s.launcher.util.SettingsManager
import com.google.android.material.slider.Slider

/**
 * SettingsActivity — launcher preferences (R5, R6).
 *
 * Changes are written immediately via SettingsManager.
 * The main activity re-reads settings on resume so changes take effect
 * without any additional signalling.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsManager(this)

        // Apply theme mode & accent color overlay before inflation
        applyThemeAndAccent(settings)

        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // Apply transparency to the settings window background (not to content, only the surface)
        window.decorView.background?.mutate()?.alpha =
            (settings.transparencyAlpha * 255f).toInt().coerceIn(0, 255)

        setupWindowInsets()
        loadSettings()
        setupListeners()
    }

    private fun applyThemeAndAccent(settings: SettingsManager) {
        // Theme mode (Dark / Light / System)
        val mode = when (settings.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.ThemeMode.DARK  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else                            -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Apply dynamic colors (Material You) – no accent overlay
        try {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        } catch (_: Exception) {}
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val density = resources.displayMetrics.density
            val topHeight = maxOf(statusBars.top, (44 * density).toInt()) + (8 * density).toInt()
            binding.settingsStatusBarSpacer.layoutParams.height = topHeight
            binding.settingsStatusBarSpacer.requestLayout()
            insets
        }
    }

    private fun loadSettings() {
        // Show app names
        binding.switchShowAppNames.isChecked = settings.showAppNames

        // Transparency slider (30–100 integer, stored as 0.30–1.00 float)
        val alphaPct = (settings.transparencyAlpha * 100f).toInt().coerceIn(30, 100)
        binding.sliderTransparency.value = alphaPct.toFloat()
        binding.tvTransparencyValue.text = "$alphaPct%"

        // Icon shape
        when (settings.iconShape) {
            SettingsManager.IconShape.CIRCLE   -> binding.chipCircle.isChecked = true
            SettingsManager.IconShape.SQUIRCLE -> binding.chipSquircle.isChecked = true
            else                                -> binding.chipRoundedSquare.isChecked = true
        }

        // Theme mode
        when (settings.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> binding.chipThemeLight.isChecked = true
            SettingsManager.ThemeMode.DARK  -> binding.chipThemeDark.isChecked = true
            else                            -> binding.chipThemeSystem.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnSettingsBack.setOnClickListener { finish() }

        // Show app names
        binding.rowShowAppNames.setOnClickListener {
            binding.switchShowAppNames.isChecked = !binding.switchShowAppNames.isChecked
        }
        binding.switchShowAppNames.setOnCheckedChangeListener { _, isChecked ->
            settings.showAppNames = isChecked
        }

        // Transparency slider
        binding.sliderTransparency.addOnChangeListener { _, value, _ ->
            val pct = value.toInt()
            binding.tvTransparencyValue.text = "$pct%"
            settings.transparencyAlpha = pct / 100f
        }

        // Icon shape chips
        binding.chipGroupIconShape.setOnCheckedStateChangeListener { _, checkedIds ->
            val shape = when {
                checkedIds.contains(binding.chipCircle.id)   -> SettingsManager.IconShape.CIRCLE
                checkedIds.contains(binding.chipSquircle.id) -> SettingsManager.IconShape.SQUIRCLE
                else                                          -> SettingsManager.IconShape.ROUNDED_SQUARE
            }
            settings.iconShape = shape
        }

        // Theme mode chips
        binding.chipGroupThemeMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when {
                checkedIds.contains(binding.chipThemeLight.id) -> SettingsManager.ThemeMode.LIGHT
                checkedIds.contains(binding.chipThemeDark.id)  -> SettingsManager.ThemeMode.DARK
                else                                           -> SettingsManager.ThemeMode.SYSTEM
            }
            if (settings.themeMode != mode) {
                settings.themeMode = mode
                val nightMode = when (mode) {
                    SettingsManager.ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    SettingsManager.ThemeMode.DARK  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else                            -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}

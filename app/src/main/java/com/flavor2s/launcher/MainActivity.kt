package com.flavor2s.launcher

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flavor2s.launcher.adapter.OthersAdapter
import com.flavor2s.launcher.model.AppInfo
import com.flavor2s.launcher.util.AppLoader
import com.flavor2s.launcher.util.PrefsManager
import com.flavor2s.launcher.util.SettingsManager
import kotlin.math.sqrt

/**
 * 2S Launcher — MainActivity
 *
 * Architecture:
 *  ViewModel (LauncherViewModel) owns all mutable app state:
 *    - allInstalledApps, appInfoMap
 *    - essentialsSlots (6 slots, nullable package names)
 *    - hiddenPackages
 *    - selectedTargetSlotIndex (slot-picker mode)
 *  MainActivity observes the ViewModel and drives the UI.
 *
 *  Two layout configurations:
 *    - Portrait (phone default): vertically stacked panes (activity_main.xml)
 *    - Landscape (tablet ≥9"): side-by-side panes (layout-land/activity_main.xml)
 *
 *  configChanges is declared in the manifest, so the activity handles rotation
 *  itself — it just re-inflates the layout and re-binds from the ViewModel
 *  without losing state.
 */
class MainActivity : AppCompatActivity() {

    // ── ViewModel ────────────────────────────────────────────────────────────

    private val viewModel: LauncherViewModel by viewModels()

    // ── Managers ─────────────────────────────────────────────────────────────

    private lateinit var prefsManager: PrefsManager
    private lateinit var settingsManager: SettingsManager

    // ── View references (re-bound on every layout inflation) ─────────────────

    private lateinit var rootBackground: FrameLayout
    private lateinit var statusBarSpacer: View
    private lateinit var essentialsContainer: View
    private lateinit var othersContainer: View
    private lateinit var othersRecyclerView: RecyclerView
    private lateinit var othersAdapter: OthersAdapter
    private lateinit var othersLayoutManager: GridLayoutManager

    /** Slot FrameLayout references (6 total). */
    private val slotViews = ArrayList<FrameLayout>(PrefsManager.SLOT_COUNT)

    // ── Landscape-only views (null in portrait) ───────────────────────────────
    private var hiddenFolderRowLand: View? = null
    private var hiddenFolderCountLand: TextView? = null
    private var settingsRowLand: View? = null

    // ── State ─────────────────────────────────────────────────────────────────

    private var appsLoaded = false
    private var launcherAppsCallback: LauncherApps.Callback? = null
    private var currentHiddenAppsDialog: AlertDialog? = null
    private var isLandscapeTablet = false
    private var lastLoadedIconShape: String? = null
    private var lastAppliedShowAppNames: Boolean? = null
    private var lastAppliedDividerWeight: Float? = null
    private var lastAppliedTransparencyAlpha: Float? = null
    private var lastAppliedThemeMode: String? = null

    companion object {
        private const val REQUEST_CODE_UNLOCK_HIDDEN = 99
        private const val REQUEST_CODE_PERMISSIONS   = 100
        /** Screen diagonal threshold in inches for tablet layout (R8.1). */
        private const val TABLET_DIAGONAL_INCHES     = 9.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Package-change monitoring
    // ─────────────────────────────────────────────────────────────────────────

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart
            when (action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED -> loadInstalledAppsList()

                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!isReplacing && pkg != null) handlePackageRemoved(pkg)
                    else loadInstalledAppsList()
                }
            }
        }
    }

    /** Instantly removes an uninstalled package from ViewModel and UI. */
    private fun handlePackageRemoved(packageName: String) {
        runOnUiThread {
            val allApps = viewModel.allInstalledApps.value?.filter { it.packageName != packageName } ?: emptyList()
            val newMap = viewModel.appInfoMap.value?.toMutableMap()?.also { it.remove(packageName) } ?: emptyMap()
            viewModel.allInstalledApps.value = allApps
            viewModel.appInfoMap.value = newMap

            val slots = viewModel.essentialsSlots.value ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
            var slotsChanged = false
            for (i in 0 until PrefsManager.SLOT_COUNT) {
                if (slots[i] == packageName) { slots[i] = null; slotsChanged = true }
            }
            if (slotsChanged) viewModel.updateEssentials(slots)

            val hidden = viewModel.hiddenPackages.value?.toMutableSet() ?: mutableSetOf()
            if (hidden.remove(packageName)) viewModel.updateHiddenPackages(hidden)

            refreshAllSlotsUI()
            refreshOthersAdapter()
            loadInstalledAppsList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        prefsManager    = PrefsManager(this)
        settingsManager = SettingsManager(this)

        applyThemeAndAccent(settingsManager)

        super.onCreate(savedInstanceState)

        isLandscapeTablet = isLandscape() && isTablet()
        inflateLayout()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        setupWindowInsets()
        setupEssentialsSlots()
        applyTransparencyMode()
        checkAndRequestLauncherPermissions()
        registerPackageChangeReceiver()
        registerLauncherAppsCallback()
        observeViewModel()
        loadInstalledAppsList()
    }

    private fun applyThemeAndAccent(settings: SettingsManager) {
        val mode = when (settings.themeMode) {
            SettingsManager.ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.ThemeMode.DARK  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else                            -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Apply dynamic system colors (Material You) – no custom accent overlay
        try {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        } catch (_: Exception) {}

        try {
            window.setBackgroundDrawableResource(android.R.color.transparent)
        } catch (_: Exception) {}
    }

    private fun applyTransparencyMode() {
        val alpha255 = (settingsManager.transparencyAlpha * 255f).toInt().coerceIn(0, 255)
        applyBgAlpha(findViewById(R.id.essentialsFrame), alpha255)
        applyBgAlpha(findViewById(R.id.othersFrame), alpha255)
        hiddenFolderRowLand?.let { applyBgAlpha(it, alpha255) }
        settingsRowLand?.let { applyBgAlpha(it, alpha255) }
    }

    /** Sets alpha only on a view's background drawable, keeping child content fully opaque. */
    private fun applyBgAlpha(view: View?, alpha: Int) {
        view?.background?.mutate()?.alpha = alpha
    }

    override fun onResume() {
        super.onResume()

        val currentShape = settingsManager.iconShape
        val currentShowNames = settingsManager.showAppNames
        val currentDividerWeight = settingsManager.dividerWeight
        val currentTransparencyAlpha = settingsManager.transparencyAlpha
        // Accent color removed – using system dynamic colors
        val currentThemeMode = settingsManager.themeMode
        val freshHidden = prefsManager.loadHiddenApps()

        // Restart activity only if theme mode changed
        if (lastAppliedThemeMode != null && lastAppliedThemeMode != currentThemeMode) {
            lastAppliedThemeMode = currentThemeMode
            recreate()
            return
        }

        val needsAppReload = !appsLoaded || lastLoadedIconShape != currentShape
        val needsAdapterRefresh = lastAppliedShowAppNames != currentShowNames
        val needsDividerUpdate = lastAppliedDividerWeight != currentDividerWeight
        val needsTransparencyUpdate = lastAppliedTransparencyAlpha != currentTransparencyAlpha
        val needsHiddenUpdate = viewModel.hiddenPackages.value != freshHidden

        lastAppliedShowAppNames = currentShowNames
        lastAppliedDividerWeight = currentDividerWeight
        lastAppliedTransparencyAlpha = currentTransparencyAlpha
        // lastAppliedAccentColor = currentAccent // accent removed
        lastAppliedThemeMode = currentThemeMode

        if (needsTransparencyUpdate) {
            applyTransparencyMode()
        }

        if (needsHiddenUpdate) {
            viewModel.hiddenPackages.value = freshHidden
        }

        if (needsDividerUpdate) {
            applyDividerWeight()
        }

        if (needsAppReload) {
            loadInstalledAppsList()
        } else if (needsAdapterRefresh) {
            refreshOthersAdapter()
        }
    }

    override fun onStop() {
        super.onStop()
        // Dismiss hidden folder every time the user leaves (R4.3 / R4.4)
        currentHiddenAppsDialog?.dismiss()
        currentHiddenAppsDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packageChangeReceiver) } catch (_: Exception) {}
        unregisterLauncherAppsCallback()
    }

    /** Handle rotation ourselves (configChanges declared in manifest). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val wasLandTablet = isLandscapeTablet
        isLandscapeTablet = isLandscape() && isTablet()

        if (wasLandTablet != isLandscapeTablet) {
            // Layout type changed — save scroll, re-inflate, re-bind
            saveOthersScrollPosition()

            // Animate the transition
            val root = window.decorView as ViewGroup
            root.animate().alpha(0f).setDuration(180).withEndAction {
                inflateLayout()
                setupWindowInsets()
                setupEssentialsSlots()
                observeViewModel()
                refreshAllSlotsUI()
                refreshOthersAdapter()
                root.animate().alpha(1f).setDuration(180).start()
            }.start()
        } else {
            // Same layout type — just refresh weights/insets
            applyDividerWeight()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Launcher stays — no back navigation
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout inflation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inflates the correct layout for the current configuration,
     * then binds all view references.
     */
    private fun inflateLayout() {
        setContentView(R.layout.activity_main)
        bindViews()
        buildOthersAdapter()
    }

    private fun bindViews() {
        rootBackground        = findViewById(R.id.rootBackground)
        statusBarSpacer       = findViewById(R.id.statusBarSpacer)
        essentialsContainer   = findViewById(R.id.essentialsContainer)
        othersContainer       = findViewById(R.id.othersContainer)
        othersRecyclerView    = findViewById(R.id.othersRecyclerView)

        // Collect 6 slot views
        slotViews.clear()
        for (id in intArrayOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3, R.id.slot4, R.id.slot5)) {
            slotViews.add(findViewById(id))
        }

        // Landscape-only views (null-safe — not present in portrait layout)
        hiddenFolderRowLand  = findViewById(R.id.hiddenFolderRowLand)
        hiddenFolderCountLand = hiddenFolderRowLand?.let { findViewById(R.id.hiddenFolderCountLand) }
        settingsRowLand       = findViewById(R.id.settingsRowLand)

        // Wire landscape-specific buttons
        hiddenFolderRowLand?.setOnClickListener { authenticateAndShowHiddenFolder() }
        settingsRowLand?.setOnClickListener { openSettings() }

        // RecyclerView setup
        val spanCount = if (isLandscapeTablet) 5 else 4
        othersLayoutManager = GridLayoutManager(this, spanCount)
        othersRecyclerView.layoutManager = othersLayoutManager
        othersRecyclerView.setHasFixedSize(false)
        othersRecyclerView.setItemViewCacheSize(20)
        othersRecyclerView.recycledViewPool.setMaxRecycledViews(0, 30)
        othersRecyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Restore scroll position after rotation
        val scrollPos = viewModel.othersScrollPosition
        val scrollOffset = viewModel.othersScrollOffset
        if (scrollPos > 0) {
            othersRecyclerView.post {
                othersLayoutManager.scrollToPositionWithOffset(scrollPos, scrollOffset)
            }
        }

        // Auto-scale essentials slots to prevent overflow on any screen size/aspect ratio
        findViewById<View>(R.id.essentialsFrame)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            adjustEssentialsSlotSizes()
        }
    }

    private fun buildOthersAdapter() {
        val visibleApps   = viewModel.getVisibleOthersApps()
        val hiddenAppList = viewModel.getHiddenAppList()
        val folderName    = getString(R.string.hidden_folder_name)
        val showNames     = settingsManager.showAppNames

        // In landscape tablet the folder/settings rows are in the static layout,
        // not in the adapter.  We still pass the folder name so the adapter can
        // show a stripped-down row (or none at all on tablet).
        val showFolder = !isLandscapeTablet

        othersAdapter = OthersAdapter(
            apps              = visibleApps,
            hiddenApps        = hiddenAppList,
            hiddenFolderName  = if (showFolder) folderName else "",
            showAppNames      = showNames,
            onAppClick        = { app ->
                val target = viewModel.selectedTargetSlotIndex
                if (target != null) {
                    viewModel.selectedTargetSlotIndex = null
                    assignAppToSlot(app.packageName, target)
                    showToast(getString(R.string.added_to_essentials, app.label))
                } else {
                    launchApp(app.packageName)
                }
            },
            onAppLongClick    = { app -> showOthersContextMenu(app) },
            onHiddenFolderClick = { authenticateAndShowHiddenFolder() },
            onSettingsClick   = { openSettings() }
        )
        othersAdapter.attachSpanSizeLookup(othersLayoutManager)
        othersRecyclerView.adapter = othersAdapter
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Window insets & divider weight
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootBackground) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density

            val topSpacerHeight = maxOf(statusBars.top, (44 * density).toInt()) + (8 * density).toInt()
            statusBarSpacer.layoutParams.height = topSpacerHeight
            statusBarSpacer.requestLayout()

            val h = resources.getDimensionPixelSize(R.dimen.others_frame_margin_horizontal)
            val v = resources.getDimensionPixelSize(R.dimen.others_frame_margin_vertical)

            essentialsContainer.setPadding(h, 0, h, v)

            if (isLandscapeTablet) {
                othersContainer.setPadding(h, 0, h, navBars.bottom + v)
            } else {
                othersContainer.setPadding(h, v, h, navBars.bottom + v + 8)
            }

            insets
        }
    }

    /**
     * Reads the divider weight from settings and applies it to the pane weights.
     * Works for both portrait (vertical LinearLayout) and landscape (horizontal).
     */
    private fun applyDividerWeight() {
        val essW = settingsManager.dividerWeight
        val othW = settingsManager.othersPaneWeight

        val essParams = essentialsContainer.layoutParams as? LinearLayout.LayoutParams ?: return
        val othParams = othersContainer.layoutParams as? LinearLayout.LayoutParams ?: return

        essParams.weight = essW
        othParams.weight = othW

        essentialsContainer.layoutParams = essParams
        othersContainer.layoutParams = othParams
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel observation
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.allInstalledApps.observe(this) {
            refreshAllSlotsUI()
            refreshOthersAdapter()
        }
        viewModel.essentialsSlots.observe(this) {
            refreshAllSlotsUI()
            refreshOthersAdapter()
        }
        viewModel.hiddenPackages.observe(this) {
            refreshOthersAdapter()
            updateLandscapeHiddenCount()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Essentials slots setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupEssentialsSlots() {
        for (i in 0 until PrefsManager.SLOT_COUNT) {
            val slotView  = slotViews[i]
            val slotIndex = i

            slotView.setOnClickListener {
                vibrateClick()
                val pkgName = viewModel.essentialsSlots.value?.getOrNull(slotIndex)
                if (pkgName.isNullOrEmpty()) {
                    // Toggle slot-picker mode
                    viewModel.selectedTargetSlotIndex =
                        if (viewModel.selectedTargetSlotIndex == slotIndex) null else slotIndex
                    if (viewModel.selectedTargetSlotIndex != null) {
                        showToast(getString(R.string.select_app_from_others))
                    }
                    refreshAllSlotsUI()
                } else {
                    viewModel.selectedTargetSlotIndex = null
                    refreshAllSlotsUI()
                    launchApp(pkgName)
                }
            }

            slotView.setOnLongClickListener {
                val pkgName = viewModel.essentialsSlots.value?.getOrNull(slotIndex)
                if (!pkgName.isNullOrEmpty()) {
                    viewModel.selectedTargetSlotIndex = null
                    refreshAllSlotsUI()
                    showEssentialsContextMenu(pkgName, slotIndex)
                } else {
                    viewModel.selectedTargetSlotIndex = slotIndex
                    refreshAllSlotsUI()
                    showToast(getString(R.string.select_app_from_others))
                }
                true
            }
        }
        refreshAllSlotsUI()
        applyDividerWeight()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Context menus (R2)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showAppContextMenu(
        app: AppInfo,
        options: List<Triple<String, Int, () -> Unit>>
    ) {
        vibrateClick()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_context_menu, null)
        val iconView    = dialogView.findViewById<ImageView>(R.id.menuAppIcon)
        val titleView   = dialogView.findViewById<TextView>(R.id.menuAppTitle)
        val subtitleView = dialogView.findViewById<TextView>(R.id.menuAppSubtitle)
        val container   = dialogView.findViewById<android.widget.LinearLayout>(R.id.menuOptionsContainer)

        iconView.setImageDrawable(app.icon)
        titleView.text = app.label
        subtitleView.text = app.packageName

        val dialog = AlertDialog.Builder(this, R.style.DS_Dialog)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        for ((title, iconRes, action) in options) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_context_menu_option, container, false)
            itemView.findViewById<ImageView>(R.id.menuOptionIcon).setImageResource(iconRes)
            itemView.findViewById<TextView>(R.id.menuOptionText).text = title
            itemView.setOnClickListener { dialog.dismiss(); action() }
            container.addView(itemView)
        }

        dialog.show()
        // Apply transparency to the dialog background only — icons/text stay fully opaque
        applyBgAlpha(dialogView, (settingsManager.transparencyAlpha * 255f).toInt().coerceIn(0, 255))
    }

    /**
     * R2 — Essentials long-press menu.
     * Options: Remove from Essentials, Uninstall, App Info.
     * NOTE: "Hide" is intentionally absent per spec (R2).
     */
    private fun showEssentialsContextMenu(packageName: String, slotIndex: Int) {
        val app = viewModel.appInfoMap.value?.get(packageName) ?: AppInfo(
            label = packageName, packageName = packageName, activityName = "",
            icon = packageManager.defaultActivityIcon
        )
        val options = listOf(
            Triple(getString(R.string.menu_remove_from_essentials), R.drawable.ic_pin)
                { removeFromEssentials(slotIndex) },
            Triple(getString(R.string.menu_app_info), R.drawable.ic_info)
                { openAppInfo(packageName) },
            Triple(getString(R.string.menu_uninstall), R.drawable.ic_trash)
                { uninstallApp(packageName) }
        )
        showAppContextMenu(app, options)
    }

    /**
     * R2 — Others long-press menu.
     * Options: Add to Essentials (only if not full), Uninstall, App Info, Hide.
     */
    private fun showOthersContextMenu(app: AppInfo) {
        val options = mutableListOf<Triple<String, Int, () -> Unit>>()

        if (!viewModel.essentialsIsFull()) {
            options.add(Triple(getString(R.string.menu_add_to_essentials), R.drawable.ic_pin) {
                val target = viewModel.selectedTargetSlotIndex ?: viewModel.firstFreeSlot()
                if (target != -1) {
                    viewModel.selectedTargetSlotIndex = null
                    assignAppToSlot(app.packageName, target)
                    showToast(getString(R.string.added_to_essentials, app.label))
                }
            })
        }
        options.add(Triple(getString(R.string.menu_hide_app), R.drawable.ic_eye_off)
            { hideApp(app.packageName) })
        options.add(Triple(getString(R.string.menu_app_info), R.drawable.ic_info)
            { openAppInfo(app.packageName) })
        options.add(Triple(getString(R.string.menu_uninstall), R.drawable.ic_trash)
            { uninstallApp(app.packageName) })

        showAppContextMenu(app, options)
    }

    /** Long-press menu inside the hidden folder dialog. */
    private fun showHiddenAppContextMenu(app: AppInfo, onDismiss: () -> Unit) {
        val options = listOf(
            Triple(getString(R.string.menu_unhide_app), R.drawable.ic_eye) {
                unhideApp(app.packageName); onDismiss()
            },
            Triple(getString(R.string.menu_app_info), R.drawable.ic_info) { openAppInfo(app.packageName) },
            Triple(getString(R.string.menu_uninstall), R.drawable.ic_trash) { uninstallApp(app.packageName) }
        )
        showAppContextMenu(app, options)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // App actions
    // ─────────────────────────────────────────────────────────────────────────

    private fun assignAppToSlot(packageName: String, slotIndex: Int) {
        val slots = viewModel.essentialsSlots.value?.copyOf() ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
        // Dedup: remove from any existing slot
        for (i in 0 until PrefsManager.SLOT_COUNT) {
            if (slots[i] == packageName) slots[i] = null
        }
        slots[slotIndex] = packageName
        viewModel.updateEssentials(slots)
    }

    private fun removeFromEssentials(slotIndex: Int) {
        val slots = viewModel.essentialsSlots.value?.copyOf() ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
        slots[slotIndex] = null
        viewModel.updateEssentials(slots)
        showToast(getString(R.string.app_removed))
    }

    private fun hideApp(packageName: String) {
        // If pinned in Essentials, remove it first (app can't be in both)
        val slots = viewModel.essentialsSlots.value?.copyOf() ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
        var changed = false
        for (i in 0 until PrefsManager.SLOT_COUNT) {
            if (slots[i] == packageName) { slots[i] = null; changed = true }
        }
        if (changed) viewModel.updateEssentials(slots)

        val hidden = viewModel.hiddenPackages.value?.toMutableSet() ?: mutableSetOf()
        hidden.add(packageName)
        viewModel.updateHiddenPackages(hidden)
        showToast(getString(R.string.app_hidden))
    }

    private fun unhideApp(packageName: String) {
        val hidden = viewModel.hiddenPackages.value?.toMutableSet() ?: mutableSetOf()
        hidden.remove(packageName)
        viewModel.updateHiddenPackages(hidden)
        showToast(getString(R.string.app_unhidden))
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showToast(getString(R.string.launch_failed))
        }
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun launchApp(packageName: String) {
        vibrateClick()
        try {
            val launchIntent = AppLoader.getLaunchIntent(this, packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                showToast(getString(R.string.launch_failed))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.launch_failed))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hidden folder — biometric authentication (R4)
    // ─────────────────────────────────────────────────────────────────────────

    private fun authenticateAndShowHiddenFolder() {
        val hiddenList = viewModel.getHiddenAppList()
        if (hiddenList.isEmpty()) {
            showToast(getString(R.string.no_hidden_apps))
            return
        }

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isDeviceSecure != true && km?.isKeyguardSecure != true) {
            showToast(getString(R.string.device_not_secured))
            return
        }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showHiddenAppsDialog()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        launchKeyguardFallback(km)
                    }
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showToast(getString(R.string.auth_failed))
                }
            })

            try {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.hidden_folder_name))
                    .setSubtitle(getString(R.string.auth_required_for_hidden))
                    .setAllowedAuthenticators(authenticators)
                    .build()
                prompt.authenticate(promptInfo)
                return
            } catch (_: Exception) {}
        }

        launchKeyguardFallback(km)
    }

    private fun launchKeyguardFallback(km: KeyguardManager?) {
        if (km != null && (km.isDeviceSecure || km.isKeyguardSecure)) {
            @Suppress("DEPRECATION")
            val intent = km.createConfirmDeviceCredentialIntent(
                getString(R.string.hidden_folder_name),
                getString(R.string.auth_required_for_hidden)
            )
            if (intent != null) {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_UNLOCK_HIDDEN)
                    return
                } catch (_: Exception) {}
            }
        }
        showToast(getString(R.string.auth_required_for_hidden))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_UNLOCK_HIDDEN && resultCode == RESULT_OK) {
            showHiddenAppsDialog()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hidden folder dialog
    // ─────────────────────────────────────────────────────────────────────────

    private fun showHiddenAppsDialog() {
        val hiddenList = viewModel.getHiddenAppList()
        if (hiddenList.isEmpty()) {
            showToast(getString(R.string.no_hidden_apps))
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_hidden_apps, null)
        dialogView.findViewById<TextView>(R.id.hiddenDialogTitle).text = getString(R.string.hidden_folder_name)

        var dialog: AlertDialog? = null
        dialogView.findViewById<ImageView>(R.id.btnHiddenDialogClose).setOnClickListener {
            dialog?.dismiss()
        }

        val recycler = dialogView.findViewById<RecyclerView>(R.id.hiddenAppsRecyclerView)
        val hiddenAdapter = OthersAdapter(
            apps = hiddenList,
            hiddenApps = emptyList(),
            hiddenFolderName = "",
            showAppNames = settingsManager.showAppNames,
            showSettingsRow = false,
            onAppClick = { app ->
                launchApp(app.packageName)
                dialog?.dismiss()
            },
            onAppLongClick = { app ->
                showHiddenAppContextMenu(app) { dialog?.dismiss() }
            },
            onHiddenFolderClick = {},
            onSettingsClick = {}
        )
        val hiddenLayoutManager = GridLayoutManager(this, 4)
        hiddenAdapter.attachSpanSizeLookup(hiddenLayoutManager)
        recycler.layoutManager = hiddenLayoutManager
        recycler.adapter = hiddenAdapter

        dialog = AlertDialog.Builder(this, R.style.DS_Dialog)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        currentHiddenAppsDialog = dialog
        dialog.setOnDismissListener { currentHiddenAppsDialog = null }
        dialog.show()
        applyBgAlpha(dialogView, (settingsManager.transparencyAlpha * 255f).toInt().coerceIn(0, 255))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // App list loading
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadInstalledAppsList() {
        val iconShape = settingsManager.iconShape
        lastLoadedIconShape = iconShape
        Thread {
            val apps = AppLoader.loadInstalledApps(this, iconShape)
            val installedPackageSet = apps.map { it.packageName }.toSet()

            runOnUiThread {
                viewModel.allInstalledApps.value = apps
                viewModel.appInfoMap.value = apps.associateBy { it.packageName }
                cleanStalePackages(installedPackageSet)
                appsLoaded = true
                refreshAllSlotsUI()
                refreshOthersAdapter()
            }
        }.start()
    }

    private fun cleanStalePackages(currentInstalled: Set<String>) {
        val slots = viewModel.essentialsSlots.value?.copyOf() ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
        var changed = false
        for (i in 0 until PrefsManager.SLOT_COUNT) {
            val pkg = slots[i]
            if (pkg != null && pkg !in currentInstalled) { slots[i] = null; changed = true }
        }
        if (changed) viewModel.updateEssentials(slots)

        val hidden = viewModel.hiddenPackages.value?.toMutableSet() ?: mutableSetOf()
        val stale = hidden.filter { it !in currentInstalled }
        if (stale.isNotEmpty()) {
            hidden.removeAll(stale.toSet())
            viewModel.updateHiddenPackages(hidden)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI refresh
    // ─────────────────────────────────────────────────────────────────────────

    private fun refreshOthersAdapter() {
        if (!::othersAdapter.isInitialized) return
        val visible   = viewModel.getVisibleOthersApps()
        val hidden    = viewModel.getHiddenAppList()
        othersAdapter.updateApps(visible)
        othersAdapter.updateHiddenApps(hidden)
        othersAdapter.updateShowAppNames(settingsManager.showAppNames)
        updateLandscapeHiddenCount()
    }

    private fun refreshAllSlotsUI() {
        val slots  = viewModel.essentialsSlots.value ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
        val appMap = viewModel.appInfoMap.value ?: emptyMap()
        val selectedSlot = viewModel.selectedTargetSlotIndex

        for (i in 0 until PrefsManager.SLOT_COUNT) {
            val slotView     = slotViews.getOrNull(i) ?: continue
            val emptyContainer = slotView.findViewById<FrameLayout>(R.id.slotEmptyContainer)
            val filledCard   = slotView.findViewById<View>(R.id.slotFilledCard)
            val iconView     = slotView.findViewById<ImageView>(R.id.slotIcon)
            val packageName  = slots[i]

            if (packageName.isNullOrEmpty()) {
                emptyContainer.visibility = View.VISIBLE
                filledCard.visibility = View.GONE
                emptyContainer.setBackgroundResource(
                    if (selectedSlot == i) R.drawable.bg_slot_selecting else R.drawable.bg_slot_empty
                )
            } else {
                val appInfo = appMap[packageName]
                val icon = appInfo?.icon ?: try {
                    AppLoader.shapeIcon(this, packageManager.getApplicationIcon(packageName), settingsManager.iconShape)
                } catch (e: Exception) { null }

                if (icon != null) {
                    emptyContainer.visibility = View.GONE
                    filledCard.visibility = View.VISIBLE
                    iconView.setImageDrawable(icon)
                } else {
                    // App gone — clear stale slot
                    val s = viewModel.essentialsSlots.value?.copyOf() ?: arrayOfNulls<String>(PrefsManager.SLOT_COUNT)
                    s[i] = null
                    viewModel.updateEssentials(s)
                    emptyContainer.visibility = View.VISIBLE
                    filledCard.visibility = View.GONE
                    emptyContainer.setBackgroundResource(R.drawable.bg_slot_empty)
                }
            }
        }
    }

    /** Updates the hidden-app count badge in the landscape static layout (R8.2). */
    private fun updateLandscapeHiddenCount() {
        hiddenFolderCountLand?.text = (viewModel.hiddenPackages.value?.size ?: 0).toString()
    }

    /** Dynamically resizes Essentials slots so they never overflow essentialsFrame on any screen size. */
    private fun adjustEssentialsSlotSizes() {
        val frame = findViewById<View>(R.id.essentialsFrame) ?: return
        val availableWidth = frame.width - frame.paddingLeft - frame.paddingRight
        val availableHeight = frame.height - frame.paddingTop - frame.paddingBottom

        if (availableWidth <= 0 || availableHeight <= 0) return

        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        val maxSlotW = (availableWidth - 8 * marginPx) / 4
        val maxSlotH = (availableHeight - 4 * marginPx) / 2

        val maxAllowedPx = (68 * density).toInt()
        val minAllowedPx = (40 * density).toInt()

        val calculatedSlotSize = minOf(maxSlotW, maxSlotH, maxAllowedPx).coerceAtLeast(minAllowedPx)

        for (slotView in slotViews) {
            val params = slotView.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (params.width != calculatedSlotSize || params.height != calculatedSlotSize) {
                params.width = calculatedSlotSize
                params.height = calculatedSlotSize
                params.setMargins(marginPx, marginPx, marginPx, marginPx)
                slotView.layoutParams = params
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scroll state (R8.4)
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveOthersScrollPosition() {
        val firstVisible = othersLayoutManager.findFirstVisibleItemPosition()
        val firstView = othersLayoutManager.findViewByPosition(firstVisible)
        viewModel.othersScrollPosition = firstVisible
        viewModel.othersScrollOffset   = firstView?.top ?: 0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkAndRequestLauncherPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) loadInstalledAppsList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Package-change receivers
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun registerLauncherAppsCallback() {
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
            val callback = object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) =
                    handlePackageRemoved(packageName)
                override fun onPackageAdded(packageName: String, user: UserHandle) =
                    runOnUiThread { loadInstalledAppsList() }
                override fun onPackageChanged(packageName: String, user: UserHandle) =
                    runOnUiThread { loadInstalledAppsList() }
                override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                    runOnUiThread { loadInstalledAppsList() }
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                    runOnUiThread { for (pkg in packageNames) handlePackageRemoved(pkg) }
            }
            launcherApps.registerCallback(callback)
            launcherAppsCallback = callback
        } catch (_: Exception) {}
    }

    private fun unregisterLauncherAppsCallback() {
        try {
            launcherAppsCallback?.let {
                (getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps)?.unregisterCallback(it)
                launcherAppsCallback = null
            }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tablet / orientation detection (R8)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true when the device's physical screen diagonal is >= 9 inches.
     * Uses DisplayMetrics density + screen pixel dimensions (R8.1).
     */
    private fun isTablet(): Boolean {
        return try {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            val widthInches  = dm.widthPixels.toDouble()  / dm.xdpi
            val heightInches = dm.heightPixels.toDouble() / dm.ydpi
            val diagonal = sqrt(widthInches * widthInches + heightInches * heightInches)
            diagonal >= TABLET_DIAGONAL_INCHES
        } catch (_: Exception) {
            false
        }
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ─────────────────────────────────────────────────────────────────────────
    // Haptic feedback
    // ─────────────────────────────────────────────────────────────────────────

    private fun vibrateClick() {
        try {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

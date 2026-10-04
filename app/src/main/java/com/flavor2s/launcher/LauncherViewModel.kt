package com.flavor2s.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.flavor2s.launcher.model.AppInfo
import com.flavor2s.launcher.util.PrefsManager

/**
 * LauncherViewModel — holds UI-critical state so it survives rotation and
 * configuration changes (R8.4).
 *
 * MainActivity observes LiveData here instead of owning the raw fields.
 * The ViewModel does NOT perform I/O itself; MainActivity drives loads and
 * posts results here so the ViewModel stays testable.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager(application)

    // ── App lists ────────────────────────────────────────────────────────────

    /** All installed launchable apps, sorted alphabetically, excluding self. */
    val allInstalledApps = MutableLiveData<List<AppInfo>>(emptyList())

    /** Flat map of packageName → AppInfo for fast lookups. */
    val appInfoMap = MutableLiveData<Map<String, AppInfo>>(emptyMap())

    // ── Essentials ───────────────────────────────────────────────────────────

    /**
     * The 6 Essentials slot contents.
     * Null means the slot is empty.
     * Updated from MainActivity; persisted to PrefsManager immediately on change.
     */
    val essentialsSlots = MutableLiveData<Array<String?>>(prefs.loadEssentials())

    fun updateEssentials(slots: Array<String?>) {
        essentialsSlots.value = slots
        prefs.saveEssentials(slots)
    }

    // ── Hidden apps ──────────────────────────────────────────────────────────

    val hiddenPackages = MutableLiveData<Set<String>>(prefs.loadHiddenApps())

    fun updateHiddenPackages(packages: Set<String>) {
        hiddenPackages.value = packages
        prefs.saveHiddenApps(packages)
    }

    // ── Scroll position (Others RecyclerView) ───────────────────────────────

    /** Saves scroll position so rotation does not scroll Others back to top. */
    var othersScrollPosition: Int = 0
    var othersScrollOffset: Int = 0

    // ── App picker selection ─────────────────────────────────────────────────

    /**
     * When the user taps an empty Essentials slot, this is set to the slot
     * index awaiting an app tap in Others.  Null when no picker is active.
     */
    var selectedTargetSlotIndex: Int? = null

    // ── Derived helpers ──────────────────────────────────────────────────────

    fun getVisibleOthersApps(): List<AppInfo> {
        val all = allInstalledApps.value ?: return emptyList()
        val slots = essentialsSlots.value ?: emptyArray()
        val hidden = hiddenPackages.value ?: emptySet()
        val pinnedSet = slots.filterNotNull().toSet()
        return all.filter { it.packageName !in pinnedSet && it.packageName !in hidden }
    }

    fun getHiddenAppList(): List<AppInfo> {
        val all = allInstalledApps.value ?: return emptyList()
        val hidden = hiddenPackages.value ?: emptySet()
        return all.filter { it.packageName in hidden }
    }

    fun essentialsIsFull(): Boolean =
        essentialsSlots.value?.none { it.isNullOrEmpty() } == true

    fun firstFreeSlot(): Int =
        essentialsSlots.value?.indexOfFirst { it.isNullOrEmpty() } ?: -1
}

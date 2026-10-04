package com.flavor2s.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flavor2s.launcher.R
import com.flavor2s.launcher.model.AppInfo

/**
 * Others-pane RecyclerView adapter.
 * Uses DiffUtil to update only changed items for jank-free scrolling.
 *
 * View types (position order):
 *   TYPE_APP            — regular app cell in the 4-column grid
 *   TYPE_HIDDEN_FOLDER  — full-width Hidden Folder strip (2nd-to-last item)
 *   TYPE_SETTINGS       — full-width Settings strip (last item, R5)
 *
 * The Hidden Folder strip is always shown (even with 0 hidden apps so the
 * row is always reachable). The Settings strip is always shown below it.
 */
class OthersAdapter(
    private var apps: List<AppInfo>,
    private var hiddenApps: List<AppInfo> = emptyList(),
    private val hiddenFolderName: String = "",
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit = {},
    private val onHiddenFolderClick: () -> Unit = {},
    private val onSettingsClick: () -> Unit = {},
    private var showAppNames: Boolean = true,
    private val showSettingsRow: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_APP            = 0
        private const val TYPE_HIDDEN_FOLDER  = 1
        private const val TYPE_SETTINGS       = 2
    }

    init {
        setHasStableIds(true)
    }

    private val showHiddenFolder: Boolean
        get() = hiddenFolderName.isNotEmpty()

    // ── Item counts & types ───────────────────────────────────────────────────

    override fun getItemCount(): Int {
        var count = apps.size
        if (showHiddenFolder) count++  // hidden folder row
        if (showSettingsRow) count++   // settings row
        return count
    }

    override fun getItemViewType(position: Int): Int {
        val appCount = apps.size
        val folderPosition = if (showHiddenFolder) appCount else -1
        val settingsPosition = when {
            showHiddenFolder && showSettingsRow -> appCount + 1
            showSettingsRow -> appCount
            else -> -1
        }
        return when (position) {
            folderPosition   -> TYPE_HIDDEN_FOLDER
            settingsPosition -> TYPE_SETTINGS
            else             -> TYPE_APP
        }
    }

    override fun getItemId(position: Int): Long {
        return when (getItemViewType(position)) {
            TYPE_HIDDEN_FOLDER -> Long.MAX_VALUE - 1
            TYPE_SETTINGS      -> Long.MAX_VALUE
            else               -> apps[position].packageName.hashCode().toLong()
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.appIcon)
        val label: TextView = itemView.findViewById(R.id.appLabel)
        var boundPackage: String? = null
    }

    inner class HiddenFolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderRow: View    = itemView.findViewById(R.id.hiddenFolderRow)
        val folderLabel: TextView = itemView.findViewById(R.id.hiddenFolderLabel)
        val folderCount: TextView = itemView.findViewById(R.id.hiddenFolderCount)
    }

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val settingsRow: View = itemView.findViewById(R.id.settingsRow)
    }

    // ── Inflate ───────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HIDDEN_FOLDER -> {
                val view = inflater.inflate(R.layout.view_hidden_folder, parent, false)
                HiddenFolderViewHolder(view)
            }
            TYPE_SETTINGS -> {
                val view = inflater.inflate(R.layout.view_settings_strip, parent, false)
                SettingsViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_app, parent, false)
                AppViewHolder(view)
            }
        }
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HiddenFolderViewHolder -> bindHiddenFolder(holder)
            is SettingsViewHolder     -> bindSettings(holder)
            is AppViewHolder          -> bindApp(holder, apps[position])
        }
    }

    private fun bindApp(holder: AppViewHolder, app: AppInfo) {
        holder.icon.setImageDrawable(app.icon)
        holder.boundPackage = app.packageName
        holder.label.text = app.label

        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        if (showAppNames) {
            holder.label.visibility = View.VISIBLE
            val iconSize = (48 * density).toInt()
            val params = holder.icon.layoutParams
            if (params.width != iconSize || params.height != iconSize) {
                params.width = iconSize
                params.height = iconSize
                holder.icon.layoutParams = params
            }
            val pVert = (5 * density).toInt()
            holder.itemView.setPadding(holder.itemView.paddingLeft, pVert, holder.itemView.paddingRight, pVert)
        } else {
            holder.label.visibility = View.GONE
            val iconSize = (52 * density).toInt()
            val params = holder.icon.layoutParams
            if (params.width != iconSize || params.height != iconSize) {
                params.width = iconSize
                params.height = iconSize
                holder.icon.layoutParams = params
            }
            val pVert = (8 * density).toInt()
            holder.itemView.setPadding(holder.itemView.paddingLeft, pVert, holder.itemView.paddingRight, pVert)
        }

        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }
    }

    private fun bindHiddenFolder(holder: HiddenFolderViewHolder) {
        holder.folderLabel.text = hiddenFolderName
        holder.folderCount.text = hiddenApps.size.toString()
        holder.folderRow.setOnClickListener { onHiddenFolderClick() }
    }

    private fun bindSettings(holder: SettingsViewHolder) {
        holder.settingsRow.setOnClickListener { onSettingsClick() }
    }

    // ── Span lookup ───────────────────────────────────────────────────────────

    /** Full-width for the folder and settings rows; 1 column for app cells. */
    fun attachSpanSizeLookup(layoutManager: GridLayoutManager) {
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (getItemViewType(position) == TYPE_APP) 1 else layoutManager.spanCount
        }
    }

    // ── Data updates ──────────────────────────────────────────────────────────

    /** Applies DiffUtil to update visible apps without interrupting scroll. */
    fun updateApps(newApps: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = apps.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                apps[oldPos].packageName == newApps[newPos].packageName
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                apps[oldPos].label == newApps[newPos].label &&
                apps[oldPos].packageName == newApps[newPos].packageName &&
                apps[oldPos].icon == newApps[newPos].icon
        })
        apps = newApps
        diff.dispatchUpdatesTo(this)
    }

    /** Refreshes the hidden app count badge without rebuilding the adapter. */
    fun updateHiddenApps(newHiddenApps: List<AppInfo>) {
        hiddenApps = newHiddenApps
        val folderPos = apps.size
        if (showHiddenFolder) notifyItemChanged(folderPos)
    }

    /** Refreshes app-name label visibility without rebuilding the adapter. */
    fun updateShowAppNames(show: Boolean) {
        showAppNames = show
        notifyItemRangeChanged(0, apps.size)
    }
}

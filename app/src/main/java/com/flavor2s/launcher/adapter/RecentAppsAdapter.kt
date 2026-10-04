package com.flavor2s.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.flavor2s.launcher.R
import com.flavor2s.launcher.model.AppInfo

/**
 * Görev Yöneticisi (Recent Apps) için yatay kart adaptörü.
 */
class RecentAppsAdapter(
    private var recentApps: MutableList<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppCloseClick: (AppInfo, Int) -> Unit
) : RecyclerView.Adapter<RecentAppsAdapter.RecentViewHolder>() {

    inner class RecentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.recentAppIcon)
        val label: TextView = itemView.findViewById(R.id.recentAppLabel)
        val btnClose: TextView = itemView.findViewById(R.id.btnRecentClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_app_card, parent, false)
        return RecentViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val app = recentApps[position]

        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        holder.btnClose.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onAppCloseClick(app, pos)
            }
        }
    }

    override fun getItemCount(): Int = recentApps.size

    fun removeAt(position: Int) {
        if (position in 0 until recentApps.size) {
            recentApps.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, recentApps.size)
        }
    }

    fun clearAll() {
        val count = recentApps.size
        recentApps.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun updateList(newList: List<AppInfo>) {
        recentApps = newList.toMutableList()
        notifyDataSetChanged()
    }
}

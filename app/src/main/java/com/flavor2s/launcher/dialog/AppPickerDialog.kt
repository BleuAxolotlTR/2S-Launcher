package com.flavor2s.launcher.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flavor2s.launcher.R
import com.flavor2s.launcher.adapter.OthersAdapter
import com.flavor2s.launcher.model.AppInfo

/**
 * Boş Essentials yuvasına ("+") tıklandığında açılan ve kullanıcının
 * cihaza yüklü uygulamalardan birini seçmesini sağlayan diyalog penceresi.
 */
class AppPickerDialog(
    context: Context,
    private val apps: List<AppInfo>,
    private val onAppSelected: (AppInfo) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_app_picker)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.65).toInt()
        )

        val recyclerView = findViewById<RecyclerView>(R.id.pickerRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 4)

        val adapter = OthersAdapter(
            apps = apps,
            onAppClick = { selectedApp ->
                onAppSelected(selectedApp)
                dismiss()
            }
        )

        recyclerView.adapter = adapter
    }
}

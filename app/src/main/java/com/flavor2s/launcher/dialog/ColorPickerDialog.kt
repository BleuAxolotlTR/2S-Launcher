package com.flavor2s.launcher.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ViewAnimator
import com.flavor2s.launcher.R

/**
 * Kullanıcının ana arka plan rengini HSV kaydırıcıları veya hazır palet üzerinden
 * seçmesini sağlayan özel diyalog penceresi.
 */
class ColorPickerDialog(
    context: Context,
    private val initialColor: Int,
    private val onColorSelected: (Int) -> Unit
) : Dialog(context) {

    private var currentColor: Int = initialColor
    private val hsv = FloatArray(3)

    // Popüler Nintendo / Retro temalı hazır renkler
    private val presetColors = intArrayOf(
        Color.parseColor("#0F0F23"), // Koyu Lacivert (Varsayılan)
        Color.parseColor("#1E1E2E"), // Mocha Koyu
        Color.parseColor("#2B1055"), // Gece Moru
        Color.parseColor("#1B2A4A"), // 3DS Okyanus Mavisi
        Color.parseColor("#3A1C28"), // Bordo Kırmızı
        Color.parseColor("#1A3026"), // Orman Yeşili
        Color.parseColor("#181818")  // Saf Mat Siyah
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_color_picker)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        Color.colorToHSV(currentColor, hsv)

        val colorPreview = findViewById<android.view.View>(R.id.colorPreview)
        val seekBarHue = findViewById<SeekBar>(R.id.seekBarHue)
        val seekBarSat = findViewById<SeekBar>(R.id.seekBarSat)
        val seekBarVal = findViewById<SeekBar>(R.id.seekBarVal)
        val presetLayout = findViewById<LinearLayout>(R.id.presetColorsLayout)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnApply = findViewById<Button>(R.id.btnApply)

        fun updateColorPreview() {
            currentColor = Color.HSVToColor(hsv)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(currentColor)
                setStroke(2, Color.WHITE)
            }
            colorPreview.background = drawable
        }

        seekBarHue.progress = hsv[0].toInt()
        seekBarSat.progress = (hsv[1] * 100).toInt()
        seekBarVal.progress = (hsv[2] * 100).toInt()

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    when (seekBar?.id) {
                        R.id.seekBarHue -> hsv[0] = progress.toFloat()
                        R.id.seekBarSat -> hsv[1] = progress / 100f
                        R.id.seekBarVal -> hsv[2] = progress / 100f
                    }
                    updateColorPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekBarHue.setOnSeekBarChangeListener(seekBarListener)
        seekBarSat.setOnSeekBarChangeListener(seekBarListener)
        seekBarVal.setOnSeekBarChangeListener(seekBarListener)

        // Hazır renk butonları oluştur
        presetLayout.removeAllViews()
        val circleSize = (32 * context.resources.displayMetrics.density).toInt()
        val marginSize = (4 * context.resources.displayMetrics.density).toInt()

        for (preset in presetColors) {
            val circleView = android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                    setMargins(marginSize, 0, marginSize, 0)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset)
                    setStroke(2, Color.parseColor("#88FFFFFF"))
                }
                background = bg
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentColor = preset
                    Color.colorToHSV(preset, hsv)
                    seekBarHue.progress = hsv[0].toInt()
                    seekBarSat.progress = (hsv[1] * 100).toInt()
                    seekBarVal.progress = (hsv[2] * 100).toInt()
                    updateColorPreview()
                }
            }
            presetLayout.addView(circleView)
        }

        updateColorPreview()

        btnCancel.setOnClickListener { dismiss() }
        btnApply.setOnClickListener {
            onColorSelected(currentColor)
            dismiss()
        }
    }
}

package com.flavor2s.launcher.model

import android.graphics.drawable.Drawable

/**
 * Cihazda yüklü bir uygulamayı temsil eden veri modeli.
 * Simge (Drawable) arka planda yüklenerek önbelleğe alınır,
 * böylece arayüzde 120 FPS sıfır kasma ile akıcı kaydırma sağlanır.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null
)

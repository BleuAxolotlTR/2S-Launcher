package com.flavor2s.launcher.util

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import com.flavor2s.launcher.model.AppInfo

/**
 * Loads installed apps and converts their icons to the user's chosen shape.
 *
 * Supported shapes (mirrors SettingsManager.IconShape):
 *   "rounded_square"  — squircle with ~14 dp corner radius (default)
 *   "circle"          — perfect circle mask
 *   "squircle"        — super-ellipse via continuous curvature path
 */
object AppLoader {

    fun loadInstalledApps(
        context: Context,
        iconShape: String = SettingsManager.IconShape.ROUNDED_SQUARE
    ): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedApps: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
        val ownPackage = context.packageName

        return resolvedApps
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val rawIcon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                } ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!

                val shapedIcon = shapeIcon(context, rawIcon, iconShape)

                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = pkgName,
                    activityName = resolveInfo.activityInfo.name,
                    icon = shapedIcon
                )
            }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }
    }

    /**
     * Shapes a drawable according to the supplied shape constant.
     * Returns the original drawable unchanged on error.
     */
    fun shapeIcon(
        context: Context,
        icon: Drawable,
        shape: String = SettingsManager.IconShape.ROUNDED_SQUARE,
        sizeDp: Int = 56
    ): Drawable {
        return when (shape) {
            SettingsManager.IconShape.CIRCLE    -> circleIcon(context, icon, sizeDp)
            SettingsManager.IconShape.SQUIRCLE  -> squircleIcon(context, icon, sizeDp)
            else                                -> getSoftlyRoundedIcon(context, icon, sizeDp)
        }
    }

    // ── Rounded square (default) ─────────────────────────────────────────────

    /**
     * Rounded-square / squircle icon with a fixed 14 dp corner radius.
     * Used both for Essentials slots and for Others grid icons.
     */
    fun getSoftlyRoundedIcon(
        context: Context,
        icon: Drawable,
        sizeDp: Int = 56,
        cornerRadiusDp: Float = 14f
    ): Drawable {
        return try {
            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()
            val radiusPx = cornerRadiusDp * density

            val outputBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val clipPath = Path().apply {
                addRoundRect(
                    0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                    radiusPx, radiusPx,
                    Path.Direction.CW
                )
            }
            canvas.clipPath(clipPath)

            drawIconOnCanvas(canvas, icon, sizePx)
            BitmapDrawable(context.resources, outputBitmap)
        } catch (_: Exception) {
            icon
        }
    }

    // ── Circle ───────────────────────────────────────────────────────────────

    private fun circleIcon(context: Context, icon: Drawable, sizeDp: Int): Drawable {
        return try {
            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()
            val radius = sizePx / 2f

            val outputBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val clipPath = Path().apply {
                addCircle(radius, radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            drawIconOnCanvas(canvas, icon, sizePx)
            BitmapDrawable(context.resources, outputBitmap)
        } catch (_: Exception) {
            icon
        }
    }

    // ── Squircle (super-ellipse) ──────────────────────────────────────────────

    /**
     * Squircle approximated by a continuous quadratic bezier path.
     * Visually distinct from the rounded rectangle — the corners "flow" into
     * the straight edges rather than kinking.
     */
    private fun squircleIcon(context: Context, icon: Drawable, sizeDp: Int): Drawable {
        return try {
            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()
            val s = sizePx.toFloat()
            val r = s * 0.45f   // control-point radius for super-ellipse curvature

            val outputBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val path = Path().apply {
                // Top edge, starting from midpoint of the top side
                moveTo(s / 2f, 0f)
                cubicTo(s / 2f + r, 0f, s, s / 2f - r, s, s / 2f)
                cubicTo(s, s / 2f + r, s / 2f + r, s, s / 2f, s)
                cubicTo(s / 2f - r, s, 0f, s / 2f + r, 0f, s / 2f)
                cubicTo(0f, s / 2f - r, s / 2f - r, 0f, s / 2f, 0f)
                close()
            }
            canvas.clipPath(path)

            drawIconOnCanvas(canvas, icon, sizePx)
            BitmapDrawable(context.resources, outputBitmap)
        } catch (_: Exception) {
            icon
        }
    }

    // ── Shared drawing ────────────────────────────────────────────────────────

    private fun drawIconOnCanvas(canvas: Canvas, icon: Drawable, sizePx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            val inset = (sizePx * 0.18f).toInt()
            val bg = icon.background
            val fg = icon.foreground
            if (bg != null) {
                bg.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                bg.draw(canvas)
            }
            if (fg != null) {
                fg.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                fg.draw(canvas)
            }
        } else {
            icon.setBounds(0, 0, sizePx, sizePx)
            icon.draw(canvas)
        }
    }

    // ── Launch helpers ────────────────────────────────────────────────────────

    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }
}

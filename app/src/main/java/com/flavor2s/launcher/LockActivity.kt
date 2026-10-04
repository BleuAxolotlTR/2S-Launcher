package com.flavor2s.launcher

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flavor2s.launcher.util.PrefsManager

class LockActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var lockRootBackground: FrameLayout
    private lateinit var lockStatusBarSpacer: View
    private lateinit var lockBottomContainer: View
    private lateinit var lockWallpaper: ImageView
    private lateinit var pinBoxContainer: LinearLayout
    private lateinit var tvLockHint: TextView

    private val dotViews = ArrayList<View>()
    private val enteredPin = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_lock)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        prefsManager = PrefsManager(this)

        initViews()
        setupWindowInsets()
        loadPreferences()
        setupKeypad()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    private fun initViews() {
        lockRootBackground = findViewById(R.id.lockRootBackground)
        lockStatusBarSpacer = findViewById(R.id.lockStatusBarSpacer)
        lockBottomContainer = findViewById(R.id.lockBottomContainer)
        lockWallpaper = findViewById(R.id.lockWallpaper)
        pinBoxContainer = findViewById(R.id.pinBoxContainer)
        tvLockHint = findViewById(R.id.tvLockHint)

        dotViews.clear()
        dotViews.add(findViewById(R.id.dot0))
        dotViews.add(findViewById(R.id.dot1))
        dotViews.add(findViewById(R.id.dot2))
        dotViews.add(findViewById(R.id.dot3))
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(lockRootBackground) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density

            val topSpacerHeight = maxOf(statusBars.top, (44 * density).toInt()) + (12 * density).toInt()
            lockStatusBarSpacer.layoutParams.height = topSpacerHeight
            lockStatusBarSpacer.requestLayout()

            lockBottomContainer.setPadding(
                resources.getDimensionPixelSize(R.dimen.others_frame_margin_horizontal),
                resources.getDimensionPixelSize(R.dimen.others_frame_margin_vertical),
                resources.getDimensionPixelSize(R.dimen.others_frame_margin_horizontal),
                navBars.bottom + resources.getDimensionPixelSize(R.dimen.others_frame_margin_vertical) + 6
            )

            insets
        }
    }

    private fun loadPreferences() {
        val bgColor = prefsManager.loadBackgroundColor()
        lockRootBackground.setBackgroundColor(bgColor)
        window.navigationBarColor = bgColor
        window.statusBarColor = Color.TRANSPARENT

        val wallpaperUriStr = prefsManager.loadWallpaperUri()
        if (!wallpaperUriStr.isNullOrEmpty()) {
            try {
                contentResolver.openInputStream(Uri.parse(wallpaperUriStr))?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    lockWallpaper.setImageBitmap(bitmap)
                    lockWallpaper.translationX = prefsManager.loadWallpaperOffsetX()
                    lockWallpaper.translationY = prefsManager.loadWallpaperOffsetY()
                }
            } catch (e: Exception) {
                lockWallpaper.setImageDrawable(null)
            }
        }
    }

    private fun setupKeypad() {
        val numberButtons = intArrayOf(
            R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3,
            R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, R.id.btnKey7,
            R.id.btnKey8, R.id.btnKey9
        )

        for (id in numberButtons) {
            val btn = findViewById<Button>(id)
            val digit = btn.text.toString()
            btn.setOnClickListener {
                vibrateKeypress()
                if (enteredPin.length < 4) {
                    enteredPin.append(digit)
                    updatePinDots()

                    if (enteredPin.length == 4) {
                        checkPinAndUnlock()
                    }
                }
            }
        }

        // Geri / Silme Butonu (⌫)
        findViewById<Button>(R.id.btnKeyDelete).setOnClickListener {
            vibrateKeypress()
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
            }
        }

        // Kilit Açma Butonu (✓)
        findViewById<Button>(R.id.btnKeyUnlock).setOnClickListener {
            vibrateKeypress()
            val savedPin = prefsManager.getLockPin()
            if (savedPin.isEmpty() || enteredPin.toString() == savedPin) {
                unlockLauncher()
            } else {
                showWrongPinAnimation()
            }
        }
    }

    private fun updatePinDots() {
        val len = enteredPin.length
        for (i in 0 until 4) {
            if (i < len) {
                dotViews[i].setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                dotViews[i].setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun checkPinAndUnlock() {
        val savedPin = prefsManager.getLockPin()
        if (savedPin.isEmpty() || enteredPin.toString() == savedPin) {
            vibrateSuccess()
            unlockLauncher()
        } else {
            showWrongPinAnimation()
        }
    }

    private fun unlockLauncher() {
        finish()
    }

    private fun showWrongPinAnimation() {
        vibrateWrong()
        val shake = ObjectAnimator.ofFloat(pinBoxContainer, "translationX", 0f, 25f, -25f, 20f, -20f, 10f, -10f, 0f)
        shake.duration = 400
        shake.start()

        tvLockHint.text = "Hatalı şifre!"
        tvLockHint.setTextColor(Color.parseColor("#FFFF5252"))

        pinBoxContainer.postDelayed({
            enteredPin.clear()
            updatePinDots()
            tvLockHint.text = "Şifreyi tuşlayın veya ✓ butonuna basın"
            tvLockHint.setTextColor(Color.parseColor("#88FFFFFF"))
        }, 600)
    }

    private fun vibrateKeypress() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun vibrateSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun vibrateWrong() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}

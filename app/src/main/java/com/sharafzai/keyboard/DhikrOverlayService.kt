package com.sharafzai.keyboard

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Independent adhkar overlay service; settings work without opening the keyboard. */
class DhikrOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var index = 0
    private val adhkar = listOf(
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ",
        "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
        "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "سُبْحَانَ اللَّهِ وَالْحَمْدُ لِلَّهِ وَلَا إِلَٰهَ إِلَّا اللَّهُ وَاللَّهُ أَكْبَرُ",
        "اللَّهُمَّ اهْدِنِي وَسَدِّدْنِي",
        "رَبِّ زِدْنِي عِلْمًا",
        "اللَّهُمَّ بَارِكْ لَنَا فِيمَا رَزَقْتَنَا",
        "الْحَمْدُ لِلَّهِ عَلَى كُلِّ حَالٍ",
        "اللَّهُمَّ أَجِرْنِي مِنَ النَّارِ"
    )
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = applySettings()
    }
    private val ticker = object : Runnable {
        override fun run() {
            val view = overlay ?: return
            val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
            if (!prefs.getBoolean("dhikrEnabled", true)) {
                stopSelf()
                return
            }
            // Slide the current dhikr out to one side, then bring the next one
            // in from the opposite side with a restrained 3D depth effect.
            val travel = resources.displayMetrics.widthPixels.toFloat() + dp(48)
            val direction = if (index % 2 == 0) 1f else -1f
            view.animate().cancel()
            view.animate()
                .alpha(0f)
                .translationX(direction * travel)
                .rotationY(direction * 24f)
                .setDuration(520L)
                .withEndAction {
                    index = (index + 1) % adhkar.size
                    view.text = adhkar[index]
                    val position = prefs.getInt("dhikrPosition", 0).coerceIn(0, 2)
                    view.gravity = Gravity.CENTER_VERTICAL or when (position) {
                        0 -> Gravity.END
                        1 -> Gravity.START
                        else -> if (index % 2 == 0) Gravity.END else Gravity.START
                    }
                    view.translationX = -direction * travel
                    view.rotationY = -direction * 24f
                    view.scaleX = 0.94f
                    view.alpha = 0.92f
                    view.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .setDuration(760L)
                        .start()
                }.start()
            handler.postDelayed(this, prefs.getInt("dhikrIntervalMs", 10000).coerceIn(2500, 20000).toLong())
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applySettings()
        return START_STICKY
    }

    private fun applySettings() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        if (!prefs.getBoolean("dhikrEnabled", true) ||
            (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) {
            removeOverlay()
            if (!prefs.getBoolean("dhikrEnabled", true)) stopSelf()
            return
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val yExtra = when (prefs.getInt("dhikrPosition", 0).coerceIn(0, 2)) {
            1 -> dp(42)
            2 -> dp(82)
            else -> dp(5)
        }
        val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val y = (if (statusId > 0) resources.getDimensionPixelSize(statusId) else dp(24)) + yExtra
        val current = overlay
        if (current != null && current.isAttachedToWindow) {
            current.textSize = prefs.getInt("dhikrFontSize", 18).coerceIn(16, 32).toFloat()
            (current.layoutParams as? WindowManager.LayoutParams)?.let {
                it.y = y
                runCatching { manager.updateViewLayout(current, it) }
            }
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, prefs.getInt("dhikrIntervalMs", 10000).coerceIn(2500, 20000).toLong())
            return
        }
        val view = TextView(this).apply {
            text = adhkar[index]
            textSize = prefs.getInt("dhikrFontSize", 18).coerceIn(16, 32).toFloat()
            val position = prefs.getInt("dhikrPosition", 0).coerceIn(0, 2)
            gravity = Gravity.CENTER_VERTICAL or when (position) {
                0 -> Gravity.END
                1 -> Gravity.START
                else -> Gravity.END
            }
            maxLines = 2
            includeFontPadding = false
            setPadding(dp(18), dp(3), dp(18), dp(3))
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(Color.rgb(255, 246, 218))
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(226, 3, 24, 55), Color.argb(218, 5, 67, 92), Color.argb(226, 3, 24, 55))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(210, 220, 178, 82))
            }
            elevation = dp(6).toFloat()
            isClickable = false
            isFocusable = false
        }
        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.y = y
        }
        runCatching {
            manager.addView(view, params)
            overlay = view
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, prefs.getInt("dhikrIntervalMs", 10000).coerceIn(2500, 20000).toLong())
        }.onFailure {
            Log.w("SUNDUS_DHIKR", "Overlay could not be attached", it)
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(ticker)
        overlay?.let { view ->
            runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        }
        overlay = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeOverlay()
        runCatching { unregisterReceiver(settingsReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.sharafzai.keyboard.ACTION_DHIKR_SETTINGS_CHANGED"
    }
}

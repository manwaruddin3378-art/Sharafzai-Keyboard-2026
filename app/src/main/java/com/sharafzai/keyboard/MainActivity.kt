package com.sharafzai.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.rgb(20, 25, 35))
        }
        val logoFrame = TextView(this).apply {
            text = "سندس\nSUNDUS"
            textSize = 22f
            gravity = Gravity.CENTER
            maxLines = 2
            includeFontPadding = false
            setTextColor(Color.rgb(255, 222, 145))
            typeface = Typeface.create("serif", Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 76, 99), Color.rgb(3, 24, 55))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.rgb(226, 181, 67))
            }
            contentDescription = "SUNDUS – سندس"
        }
        root.addView(logoFrame, LinearLayout.LayoutParams(dp(118), dp(78)))
        root.addView(TextView(this).apply {
            text = "SUNDUS  •  سندس"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 8, 8, 10)
        })
        root.addView(TextView(this).apply {
            text = "څلور ژبې، چټک لیکل او شخصي محرمیت"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 8, 8, 28)
        })
        val enable = Button(this).apply {
            text = "۱ ـ سندس کیبورډ فعال کړئ"
            isAllCaps = false
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .onFailure { Toast.makeText(this@MainActivity, "د کیبورډ تنظیمات پرانیستل نه شول", Toast.LENGTH_LONG).show() }
            }
        }
        root.addView(enable, LinearLayout.LayoutParams(-1, dp(54)))
        val choose = Button(this).apply {
            text = "۲ ـ سندس کیبورډ وټاکئ"
            isAllCaps = false
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        root.addView(choose, LinearLayout.LayoutParams(-1, dp(54)))

        val dhikrInfo = TextView(this).apply {
            text = "۳ ـ اذکار به د سندس کیبورډ په پورتنۍ برخه کې ښکاري؛ د سکرین جلا اجازې ته اړتیا نشته."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(dhikrInfo, LinearLayout.LayoutParams(-1, dp(54)))
        val dhikrOptions = Button(this).apply {
            text = "۴ ـ د اذکارو تنظیمات او انتخابونه"
            isAllCaps = false
            setOnClickListener { showDhikrSettings() }
        }
        root.addView(dhikrOptions, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(TextView(this).apply {
            text = "د مفتي محمد انوارالدين شرفزي لخوا\n\nسندس یوازې د کیبورډ اپلیکیشن دی. اعلانونه او د انټرنېټ اجازه نه لري. بشپړ لیکل شوی متن او د کلیپ‌بورډ تاریخچه نه ساتي. که اتومات زده کړه فعاله وي، یوازې بشپړې شوې کلمې د وړاندیزونو لپاره په همدې موبایل کې خوندي کوي؛ دا اختیار د قاموس له برخې بندولای شئ. شخصي کلمې او چټکې جملې هم په موبایل کې ساتل کېږي."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 28, 8, 8)
        })
        setContentView(root)
        root.applySundusSystemBarInsets()
    }

    override fun onResume() {
        super.onResume()
        syncDhikrService()
    }

    private fun syncDhikrService() {
        // The adhkar ticker is part of the keyboard view, not a system overlay.
        runCatching { stopService(Intent(this, DhikrOverlayService::class.java)) }
    }

    private fun showDhikrSettings() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val enabled = prefs.getBoolean("dhikrEnabled", true)
        val size = prefs.getInt("dhikrFontSize", 18).coerceIn(16, 32)
        val interval = prefs.getInt("dhikrIntervalMs", 10000).coerceIn(2500, 20000)
        val sizeLabel = when (size) { 18 -> "وړه"; 22 -> "منځنۍ"; 26 -> "لویه"; else -> "$size sp" }
        val speedLabel = when (interval) { 5000 -> "۵ ثانیې"; 10000 -> "۱۰ ثانیې"; 15000 -> "۱۵ ثانیې"; else -> "${interval / 1000} ثانیې" }
        val options = arrayOf(
            if (enabled) "اذکار: فعال دي (د بندولو لپاره ټک)" else "اذکار: بند دي (د فعالولو لپاره ټک)",
            "د لیک اندازه: $sizeLabel",
            "د ذکر د بدلېدو وخت: $speedLabel"
        )
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("د SUNDUS اذکار – تنظیمات")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> prefs.edit().putBoolean("dhikrEnabled", !enabled).apply()
                    1 -> prefs.edit().putInt("dhikrFontSize", when (size) { 18 -> 22; 22 -> 26; else -> 18 }).apply()
                    2 -> prefs.edit().putInt("dhikrIntervalMs", when (interval) { 5000 -> 10000; 10000 -> 15000; else -> 5000 }).apply()
                }
                notifyDhikrSettingsChanged()
                window.decorView.post { showDhikrSettings() }
            }
            .setNegativeButton("بند", null)
            .show()
    }

    private fun notifyDhikrSettingsChanged() {
        sendBroadcast(Intent(DhikrOverlayService.ACTION_SETTINGS_CHANGED).setPackage(packageName))
        syncDhikrService()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

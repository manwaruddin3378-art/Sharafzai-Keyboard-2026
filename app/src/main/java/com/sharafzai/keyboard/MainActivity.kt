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
import android.widget.ImageView
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
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.sundus_brand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "SUNDUS – سندس"
        }, LinearLayout.LayoutParams(dp(150), dp(104)))
        root.addView(TextView(this).apply {
            text = "SUNDUS  •  سندس"
            textSize = 28f
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

        val dhikrPermission = Button(this).apply {
            text = "۳ ـ د سکرین پر سر د اذکارو اجازه"
            isAllCaps = false
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this@MainActivity)) {
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${this@MainActivity.packageName}")
                            )
                        )
                    }.onFailure {
                        Toast.makeText(this@MainActivity, "د اذکارو د ښودلو اجازه پرانیستل نه شوه", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "د اذکارو د ښودلو اجازه فعاله ده؛ کیبورډ یو ځل پرانیزئ.", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(dhikrPermission, LinearLayout.LayoutParams(-1, dp(54)))
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
    }

    private fun showDhikrSettings() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val enabled = prefs.getBoolean("dhikrEnabled", true)
        val size = prefs.getInt("dhikrFontSize", 23).coerceIn(16, 32)
        val interval = prefs.getInt("dhikrIntervalMs", 4300).coerceIn(2500, 12000)
        val position = prefs.getInt("dhikrPosition", 0).coerceIn(0, 2)
        val sizeLabel = when (size) { 18 -> "وړه"; 23 -> "منځنۍ"; 28 -> "لویه"; else -> "$size sp" }
        val speedLabel = when (interval) { 3000 -> "چټک (۳ ثانیې)"; 4300 -> "منځنی (۴.۳ ثانیې)"; 7000 -> "ورو (۷ ثانیې)"; else -> "$interval ms" }
        val positionLabel = listOf("د سکرین په سر", "د ساعت لاندې", "لږ ښکته")[position]
        val options = arrayOf(
            if (enabled) "اذکار: فعال دي (د بندولو لپاره ټک)" else "اذکار: بند دي (د فعالولو لپاره ټک)",
            "د لیک اندازه: $sizeLabel",
            "د ذکر د بدلېدو سرعت: $speedLabel",
            "د ښودلو ځای: $positionLabel",
            "د سکرین پر سر د ښودلو اجازه"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("د SUNDUS اذکار – تنظیمات")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val next = !enabled
                        prefs.edit().putBoolean("dhikrEnabled", next).apply()
                        Toast.makeText(this, if (next) "اذکار فعال شول؛ کیبورډ بیا پرانیزئ." else "اذکار بند شول.", Toast.LENGTH_LONG).show()
                    }
                    1 -> {
                        val next = when (size) { 18 -> 23; 23 -> 28; 28 -> 18; else -> 23 }
                        prefs.edit().putInt("dhikrFontSize", next).apply()
                        showDhikrSettings()
                    }
                    2 -> {
                        val next = when (interval) { 3000 -> 4300; 4300 -> 7000; 7000 -> 3000; else -> 4300 }
                        prefs.edit().putInt("dhikrIntervalMs", next).apply()
                        showDhikrSettings()
                    }
                    3 -> {
                        prefs.edit().putInt("dhikrPosition", (position + 1) % 3).apply()
                        showDhikrSettings()
                    }
                    4 -> {
                        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                            runCatching {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                            }.onFailure {
                                Toast.makeText(this, "د اذکارو اجازه پرانیستل نه شوه", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, "د اذکارو د ښودلو اجازه فعاله ده.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("بند", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

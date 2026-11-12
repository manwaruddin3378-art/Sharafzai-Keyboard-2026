package com.sharafzai.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        root.addView(TextView(this).apply {
            text = "SUNDUS  •  سندس"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 18, 8, 10)
        })
        root.addView(TextView(this).apply {
            text = "پنځه ژبې، چټک لیکل او شخصي محرمیت"
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
        root.addView(TextView(this).apply {
            text = "د مفتي محمد انوارالدين شرفزي لخوا\n\nسندس یوازې د کیبورډ اپلیکیشن دی. اعلانونه او د انټرنېټ اجازه نه لري. ستاسې شخصي لغتونه او تنظیمات په همدې موبایل کې ساتل کېږي."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 28, 8, 8)
        })
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

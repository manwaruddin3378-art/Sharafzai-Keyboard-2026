package com.sharafzai.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import android.graphics.Color

/** AutoText editor hosted in a normal Activity so the selected IME can type into its fields. */
class AutoTextActivity : Activity() {
    companion object { const val EXTRA_LANGUAGE = "autotext_language" }
    private val prefs by lazy { getSharedPreferences("SharafzaiSettings", MODE_PRIVATE) }
    private val language by lazy { intent.getIntExtra(EXTRA_LANGUAGE, 0).coerceIn(0, 3) }
    private val storageKey by lazy { "autoText_$language" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        manager()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun base(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.rgb(245, 248, 250))
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 48, 77)); setPadding(0, dp(8), 0, dp(12))
        })
        setContentView(root)
        return root
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }
    private fun rows(): List<Pair<String, String>> =
        prefs.getStringSet(storageKey, emptySet()).orEmpty().mapNotNull { item ->
            val parts = item.split("|||", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.sortedBy { it.first.lowercase() }

    private fun manager() {
        val root = base("شخصي AutoText لنډیزونه")
        root.addView(TextView(this).apply {
            text = "لنډ کوډ د سندس کیبورډ د فاصلې تڼۍ په وهلو بشپړ متن ته پراخېږي."
            textSize = 14f; setTextColor(Color.DKGRAY); setPadding(dp(4), 0, dp(4), dp(10))
        })
        root.addView(button("＋ نوی لنډیز") { editor(null) }, LinearLayout.LayoutParams(-1, dp(48)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val current = rows()
        if (current.isEmpty()) list.addView(TextView(this).apply {
            text = "تر اوسه لنډیز نه دی ثبت شوی."; textSize = 15f
            gravity = Gravity.CENTER; setPadding(0, dp(28), 0, dp(28))
        })
        current.forEach { pair ->
            list.addView(TextView(this).apply {
                text = "${pair.first}  →  ${pair.second}"
                textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(12), dp(14), dp(12), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(5, 48, 77)); cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { editor(pair) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@AutoTextActivity).setTitle("لنډیز حذف کړئ؟")
                        .setMessage(pair.first).setNegativeButton("بېرته", null)
                        .setPositiveButton("هو، حذف") { _, _ ->
                            val updated = prefs.getStringSet(storageKey, emptySet()).orEmpty().toMutableSet()
                            updated.remove("${pair.first}|||${pair.second}")
                            if (prefs.edit().putStringSet(storageKey, HashSet(updated)).commit()) manager()
                            else toast("لنډیز حذف نه شو")
                        }.show()
                    true
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
        root.addView(button("بندول") { finish() }, LinearLayout.LayoutParams(-1, dp(46)))
    }

    private fun editor(old: Pair<String, String>?) {
        val root = base(if (old == null) "نوی AutoText لنډیز" else "AutoText اصلاح کړئ")
        val code = EditText(this).apply {
            hint = "لنډ کوډ، لکه سلام"; setSingleLine(true); textSize = 17f; setText(old?.first.orEmpty())
        }
        val expansion = EditText(this).apply {
            hint = "بشپړه جمله یا متن"; minLines = 3
            gravity = Gravity.TOP or Gravity.START; setSingleLine(false); textSize = 17f
            setText(old?.second.orEmpty())
        }
        root.addView(code, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(expansion, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("＋ ساتل") {
            val shortcut = code.text.toString().trim()
            val fullText = expansion.text.toString().trim()
            if (shortcut.isBlank() || fullText.isBlank()) toast("لنډ کوډ او بشپړ متن دواړه ولیکئ")
            else {
                val updated = prefs.getStringSet(storageKey, emptySet()).orEmpty().toMutableSet()
                if (old != null) updated.remove("${old.first}|||${old.second}")
                updated.removeAll { it.substringBefore("|||") == shortcut }
                updated.add("$shortcut|||$fullText")
                if (prefs.edit().putStringSet(storageKey, HashSet(updated)).commit()) {
                    toast("AutoText لنډیز خوندي شو"); manager()
                } else toast("لنډیز خوندي نه شو؛ بیا هڅه وکړئ")
            }
        }, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(button("بېرته") { manager() }, LinearLayout.LayoutParams(-1, dp(46)))
        code.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

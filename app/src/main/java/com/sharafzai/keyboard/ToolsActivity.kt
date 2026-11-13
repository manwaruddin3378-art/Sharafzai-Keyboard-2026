package com.sharafzai.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Standalone utilities screen; these tools are deliberately kept outside the typing keyboard. */
class ToolsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(Color.rgb(8, 28, 55))
        }
        root.addView(TextView(this).apply {
            text = "SUNDUS  |  د سندس وسایل"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(18))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "کلکولیټر او نورې اسانتیاوې"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 225, 245))
            setPadding(0, 0, 0, dp(20))
        })
        addTool(root, "🧮  کلکولیټر", "حساب او محاسبه") { showCalculator() }
        addTool(root, "📅  کلینډر", "نېټه او مهال‌وېش") {
            runExternal(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI), "کلیندر په دې موبایل کې ونه موندل شو")
        }
        addTool(root, "⏰  الارم", "نوی الارم جوړول") {
            runExternal(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_SKIP_UI, false), "د الارم اپلیکیشن موجود نه دی")
        }
        addTool(root, "📝  یادښت", "لنډ یادښت لیکل") { showNote() }
        val back = Button(this).apply {
            text = "بېرته"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, dp(50)))
        setContentView(root)
        when (intent.getStringExtra("open_page")) {
            "calendar" -> root.post { runExternal(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI), "کلیندر په دې موبایل کې ونه موندل شو") }
            "alarms" -> root.post { runExternal(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_SKIP_UI, false), "د الارم اپلیکیشن موجود نه دی") }
        }
    }

    private fun addTool(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = "$title\n$subtitle"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(66)).apply {
            bottomMargin = dp(10)
        })
    }

    private fun showCalculator() {
        val input = EditText(this).apply {
            hint = "لکه: 125 × (4 + 2)"
            textSize = 22f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("🧮 کلکولیټر")
            .setMessage("د +  −  ×  ÷ او قوسونو حساب ولیکئ")
            .setView(input)
            .setPositiveButton("حساب") { _, _ ->
                val result = runCatching { ExpressionParser(input.text.toString()).parse() }.getOrNull()
                if (result == null) Toast.makeText(this, "عبارت سم نه دی", Toast.LENGTH_LONG).show()
                else AlertDialog.Builder(this).setTitle("پایله").setMessage(result.toString())
                    .setPositiveButton("سمه ده", null).show()
            }
            .setNegativeButton("بند", null)
            .show()
    }

    private fun showNote() {
        val note = EditText(this).apply {
            hint = "خپل یادښت دلته ولیکئ"
            minLines = 4
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("نوی یادښت").setView(note)
            .setPositiveButton("کاپي") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SUNDUS", note.text))
                Toast.makeText(this, "یادښت کاپي شو", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("بند", null).show()
    }

    private fun runExternal(intent: Intent, error: String) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class ExpressionParser(private val source: String) {
        private var pos = 0
        fun parse(): Double {
            val value = expression()
            spaces()
            require(pos == source.length && value.isFinite())
            return value
        }
        private fun expression(): Double {
            var value = term()
            while (true) {
                spaces()
                value = when {
                    take('+') -> value + term()
                    take('-') || take('−') -> value - term()
                    else -> return value
                }
            }
        }
        private fun term(): Double {
            var value = factor()
            while (true) {
                spaces()
                value = when {
                    take('*') || take('×') -> value * factor()
                    take('/') || take('÷') -> value / factor()
                    else -> return value
                }
            }
        }
        private fun factor(): Double {
            spaces()
            if (take('+')) return factor()
            if (take('-') || take('−')) return -factor()
            if (take('(')) {
                val value = expression()
                spaces()
                require(take(')'))
                return value
            }
            val start = pos
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
            require(pos > start)
            return source.substring(start, pos).toDouble()
        }
        private fun spaces() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
        private fun take(char: Char): Boolean {
            if (pos < source.length && source[pos] == char) { pos++; return true }
            return false
        }
    }
}

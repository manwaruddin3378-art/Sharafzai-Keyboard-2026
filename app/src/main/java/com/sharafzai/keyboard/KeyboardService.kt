package com.sharafzai.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.PopupWindow
class KeyboardService : InputMethodService() {

    private var language = 0
    private var shifted = false
    private var caps = false
    private var suggestionContainer: LinearLayout? = null
 private val offlineWords = mapOf(
        0 to listOf(
            "الله", "محمد", "اسلام", "قرآن", "کتاب",
            "ښوونځی", "پوهنتون", "افغانستان", "پښتو",
            "ژبه", "علم", "استاد", "زده کړه", "ملګری",
            "ورور", "خور", "کور", "کار", "ژوند",
            "ښه", "مننه", "سلام", "ورځ", "شپه"
        ),
        1 to listOf(
            "الله", "محمد", "الإسلام", "القرآن",
            "كتاب", "مدرسة", "جامعة", "لغة",
            "العربية", "علم", "أستاذ", "طالب",
            "صديق", "بيت", "عمل", "حياة",
            "شكرا", "مرحبا", "السلام"
        ),
        2 to listOf(
            "the", "this", "that", "there", "their",
            "they", "these", "those", "hello",
            "help", "have", "here", "how",
            "good", "great", "go", "get",
            "keyboard", "language", "computer",
            "school", "student", "thank", "thanks"
        )
    )
    private data class KeySpec(
        val normal: String,
        val shifted: String = "",
        val longPress: String = ""
    )

    private fun k(a: String, b: String = "", c: String = "") =
        KeySpec(a, b, c)

    private val pashtoRows = listOf(
        listOf(
            k("ض", "ْ"), k("ص", "ٌ"), k("ث", "ٍ"),
            k("ق", "ً"), k("ف", "ُ"), k("غ", "ِ"),
            k("ع", "َ"), k("ه", "ّ", "هـ"),
            k("خ", "ځ"), k("ح", "څ"),
            k("ج", "]"), k("چ", "["),
            k("\\", "|")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ۍ"), k("ی", "ي"),
            k("ب", "پ"), k("ل", "أ"), k("ا", "آ"),
            k("ت", "ټ"), k("ن", "ڼ"), k("م", "ة"),
            k("ک", ":"), k("ګ", "؛")
        ),
        listOf(
            k("ظ", "ئ"), k("ط", "ې"), k("ز", "ژ"),
            k("ر", "ء"), k("ذ", "‌"), k("د", "ډ"),
            k("ړ", "ؤ"), k("و", "،"), k("ږ", "."),
            k("/", "؟")
        )
    )

    private val arabicRows = listOf(
        listOf(
            k("ض", "ْ"), k("ص", "ٌ"), k("ث", "ٍ"),
            k("ق", "ً"), k("ف", "ُ"), k("غ", "ِ"),
            k("ع", "َ"), k("ه", "ّ"), k("خ", "أ"),
            k("ح", "آ"), k("ج", "ئ"), k("د", "ء"),
            k("ذ", "ؤ")
        ),
        listOf(
            k("ش", "ْ"), k("س", "ٌ"), k("ي", "ٍ"),
            k("ب", "ً"), k("ل", "ُ"), k("ا", "ِ"),
            k("ت", "َ"), k("ن", "ّ"), k("م", "أ"),
            k("ك", "آ"), k("ة", "ئ"), k("ؤ", "ء")
        ),
        listOf(
            k("ز", "َ"), k("ر", "ِ"), k("ط", "ّ"),
            k("ظ", "ْ"), k("و", "ٌ"), k("ء", "ٍ"),
            k("ئ", "ً"), k("أ", "ُ"), k("آ", "ِ"),
            k("ى", "َ")
        )
    )

    private val englishRows = listOf(
        listOf(
            k("q", "Q", "1"), k("w", "W", "2"),
            k("e", "E", "3"), k("r", "R", "4"),
            k("t", "T", "5"), k("y", "Y", "6"),
            k("u", "U", "7"), k("i", "I", "8"),
            k("o", "O", "9"), k("p", "P", "0"),
            k("[", "{"), k("]", "}")
        ),
        listOf(
            k("a", "A"), k("s", "S"), k("d", "D"),
            k("f", "F"), k("g", "G"), k("h", "H"),
            k("j", "J"), k("k", "K"), k("l", "L"),
            k(";", ":"), k("'", "\"")
        ),
        listOf(
            k("z", "Z"), k("x", "X"), k("c", "C"),
            k("v", "V"), k("b", "B"), k("n", "N"),
            k("m", "M"), k(",", "<"), k(".", ">"),
            k("/", "?")
        )
    )

    private val pashtoNumbers = listOf(
        k("۱", "!", "1"), k("۲", "٬", "2"),
        k("۳", "٫", "3"), k("۴", "؋", "4"),
        k("۵", "٪", "5"), k("۶", "×", "6"),
        k("۷", "»", "7"), k("۸", "«", "8"),
        k("۹", ")", "9"), k("۰", "(", "0"),
        k("-", "ـ"), k("=", "+")
    )

    private val arabicNumbers = listOf(
        k("١", "!", "1"), k("٢", "@", "2"),
        k("٣", "#", "3"), k("٤", "$", "4"),
        k("٥", "%", "5"), k("٦", "^", "6"),
        k("٧", "&", "7"), k("٨", "*", "8"),
        k("٩", "(", "9"), k("٠", ")", "0"),
        k("-", "_"), k("=", "+")
    )

    private val englishNumbers = listOf(
        k("1", "!"), k("2", "@"), k("3", "#"),
        k("4", "$"), k("5", "%"), k("6", "^"),
        k("7", "&"), k("8", "*"), k("9", "("),
        k("0", ")"), k("-", "_"), k("=", "+")
    )

    override fun onCreateInputView(): View {
        return buildKeyboard()
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(3), dp(4), dp(3), dp(4))
        root.setBackgroundColor(Color.rgb(27, 32, 43))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = when (language) {
            0 -> "SHARAFZAI  •  پښتو"
            1 -> "SHARAFZAI  •  العربية"
            else -> "SHARAFZAI  •  ENGLISH"
        }
        title.textSize = 14f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(29), 1f)
        )

        addSpecial(header, "🌐", 0.8f, 29) {
            language = (language + 1) % 3
            shifted = false
            refreshKeyboard()
        }

        root.addView(header, rowParams(31))

        val numberRow = when (language) {
            0 -> pashtoNumbers
            1 -> arabicNumbers
            else -> englishNumbers
        }
        addKeyRow(root, numberRow, 37)

        val rows = when (language) {
            0 -> pashtoRows
            1 -> arabicRows
            else -> englishRows
        }

        for (row in rows) {
            addKeyRow(root, row, 42)
        }

        val controlRow = LinearLayout(this)
        controlRow.orientation = LinearLayout.HORIZONTAL

        addSpecial(controlRow, "Tab", 0.9f, 39) {
            currentInputConnection?.commitText("\t", 1)
        }

        addSpecial(controlRow, "Caps", 1.1f, 39) {
            caps = !caps
            refreshKeyboard()
        }

        addSpecial(controlRow, "⇧", 1f, 39) {
            shifted = !shifted
            refreshKeyboard()
        }

        addSpecial(controlRow, "⌫", 1.2f, 39) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        root.addView(controlRow, rowParams(41))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
addSpecial(bottom, "←", 0.8f, 42) {
    sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
}

addSpecial(bottom, "→", 0.8f, 42) {
    sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
}      

        addSpecial(bottom, "،", 0.7f, 42) {
            val value = if (language == 2) "," else "،"
            currentInputConnection?.commitText(value, 1)
        }

        addSpecial(bottom, "Space", 3.5f, 42) {
            currentInputConnection?.commitText(" ", 1)
        }

        addSpecial(bottom, ".", 0.7f, 42) {
            currentInputConnection?.commitText(".", 1)
        }

        addSpecial(bottom, "↵", 1f, 42) {
            sendKey(KeyEvent.KEYCODE_ENTER)
        }

        root.addView(bottom, rowParams(44))

        return root
    }

    private fun addKeyRow(
        parent: LinearLayout,
        keys: List<KeySpec>,
        height: Int
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER

        for ((index, spec) in keys.withIndex()) {
            val button = Button(this)

            val value = when {
                shifted && spec.shifted.isNotEmpty() -> spec.shifted
                language == 2 && (caps || shifted) ->
                    spec.normal.uppercase()
                else -> spec.normal
            }

            button.text = value
            button.textSize = when {
                keys.size >= 12 -> 13f
                else -> 15f
            }
            button.typeface = Typeface.DEFAULT_BOLD
            button.isAllCaps = false
            button.setTextColor(Color.WHITE)
            button.minimumWidth = 0
            button.minimumHeight = 0
            button.setPadding(0, 0, 0, 0)

            val colors = intArrayOf(
                Color.rgb(55, 92, 130),
                Color.rgb(61, 105, 139),
                Color.rgb(72, 87, 126),
                Color.rgb(57, 112, 117)
            )

            button.background = roundedBackground(
                colors[index % colors.size],
                dp(8)
            )

            button.setOnClickListener {
               val before = currentInputConnection
    ?.getTextBeforeCursor(30, 0)
    ?.toString().orEmpty()

val previous = before.trimEnd().lastOrNull()

val sentenceStart = before.isBlank() ||
    previous == null ||
    previous in listOf('.', '?', '!', '؟', '\n')

val output = when {
    shifted && spec.shifted.isNotEmpty() ->
        spec.shifted

    language == 2 && (caps || shifted || sentenceStart) ->
        spec.normal.uppercase()

    else -> spec.normal
}
                currentInputConnection?.commitText(output, 1)

                if (shifted && !caps) {
                    shifted = false
                    refreshKeyboard()
                }
            }

           button.setOnLongClickListener {
    val alternate = when {
        spec.longPress.isNotEmpty() -> spec.longPress
        spec.shifted.isNotEmpty() -> spec.shifted
        language == 2 -> spec.normal.uppercase()
        else -> ""
    }

    if (alternate.isNotEmpty()) {
        val preview = TextView(this)
        preview.text = alternate
        preview.textSize = 25f
        preview.gravity = Gravity.CENTER
        preview.setTextColor(Color.WHITE)
        preview.background = roundedBackground(
            Color.rgb(28, 137, 157), dp(10)
        )

        val popup = PopupWindow(
            preview, dp(60), dp(52), false
        )

        popup.showAsDropDown(
            button, 0, -button.height - dp(52)
        )

        currentInputConnection?.commitText(alternate, 1)

        button.postDelayed({
            if (popup.isShowing) popup.dismiss()
        }, 1000)
    }

    true
}

            val params = LinearLayout.LayoutParams(
                0, dp(height), 1f
            )
            params.setMargins(dp(1), dp(2), dp(1), dp(2))
            row.addView(button, params)
        }

        parent.addView(row, rowParams(height + 3))
    }

    private fun addSpecial(
        parent: LinearLayout,
        label: String,
        weight: Float,
        height: Int,
        action: () -> Unit
    ) {
        val button = Button(this)
        button.text = label
        button.textSize = 12f
        button.isAllCaps = false
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(Color.WHITE)
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.setPadding(0, 0, 0, 0)
        button.background = roundedBackground(
            Color.rgb(28, 137, 157),
            dp(8)
        )
        if (label == "⌫") {
    val handler = Handler(Looper.getMainLooper())
    var repeatTask: Runnable? = null

    button.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                action()

                val task = object : Runnable {
                    override fun run() {
                        action()
                        handler.postDelayed(this, 65)
                    }
                }

                repeatTask = task
                handler.postDelayed(task, 400)
                true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                repeatTask?.let {
                    handler.removeCallbacks(it)
                }
                repeatTask = null
                true
            }

            else -> true
        }
    }
} else {
    button.setOnClickListener { action() }
}

        val params = LinearLayout.LayoutParams(
            0, dp(height), weight
        )
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        parent.addView(button, params)
    }
private fun getOfflineSuggestions(): List<String> {
    val before = currentInputConnection
        ?.getTextBeforeCursor(60, 0)
        ?.toString().orEmpty()

    val currentWord = before
        .takeLastWhile { it.isLetter() }

    if (currentWord.isEmpty()) return emptyList()

    return offlineWords[language]
        .orEmpty()
        .filter {
            it.startsWith(currentWord, ignoreCase = true) &&
            !it.equals(currentWord, ignoreCase = true)
        }
        .take(3)
}
    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), Color.rgb(90, 115, 145))
        }
    }

    private fun sendKey(code: Int) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, code)
        )
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, code)
        )
    }

    private fun refreshKeyboard() {
        setInputView(buildKeyboard())
    }

    private fun rowParams(height: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(height)
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

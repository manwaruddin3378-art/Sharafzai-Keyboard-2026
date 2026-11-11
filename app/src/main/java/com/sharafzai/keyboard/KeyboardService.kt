
package com.sharafzai.keyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock

class KeyboardService : InputMethodService() {

    private var language = 0
    private var shifted = false
    private var caps = false
    private var themeIndex = 0
    private var keySizeLevel = 1
    private var suggestionContainer: LinearLayout? = null
    private var emojiPanelVisible = false
    private var diacriticsPanelVisible = false
    private var symbolsPanelVisible = false
    private var calculatorPanelVisible = false
    private var toolsPanelVisible = false
    private var settingsPanelVisible = false
    private var calculatorExpression = ""

    private data class KeyboardTheme(
        val background: Int,
        val keyColors: List<Int>,
        val special: Int,
        val suggestion: Int,
        val accent: Int,
        val border: Int
    )

    private val themes = listOf(
        KeyboardTheme(
            Color.rgb(27, 32, 43),
            listOf(
                Color.rgb(55, 92, 130),
                Color.rgb(61, 105, 139),
                Color.rgb(72, 87, 126),
                Color.rgb(57, 112, 117)
            ),
            Color.rgb(28, 137, 157),
            Color.rgb(38, 45, 58),
            Color.rgb(255, 205, 80),
            Color.rgb(90, 115, 145)
        ),
        KeyboardTheme(
            Color.rgb(30, 25, 40),
            listOf(
                Color.rgb(100, 65, 135),
                Color.rgb(115, 75, 145),
                Color.rgb(80, 65, 125)
            ),
            Color.rgb(155, 85, 170),
            Color.rgb(48, 38, 60),
            Color.rgb(255, 190, 230),
            Color.rgb(135, 100, 155)
        ),
        KeyboardTheme(
            Color.rgb(20, 38, 35),
            listOf(
                Color.rgb(45, 100, 80),
                Color.rgb(55, 115, 90),
                Color.rgb(65, 90, 75)
            ),
            Color.rgb(35, 145, 110),
            Color.rgb(30, 55, 48),
            Color.rgb(190, 245, 190),
            Color.rgb(80, 135, 105)
        ),
        KeyboardTheme(
            Color.rgb(35, 35, 35),
            listOf(
                Color.rgb(75, 75, 75),
                Color.rgb(90, 90, 90),
                Color.rgb(65, 65, 65)
            ),
            Color.rgb(100, 100, 100),
            Color.rgb(48, 48, 48),
            Color.rgb(255, 220, 120),
            Color.rgb(120, 120, 120)
        )
    )

    private val theme: KeyboardTheme
        get() = themes[themeIndex]

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(
            "SharafzaiSettings",
            MODE_PRIVATE
        )

        language = prefs.getInt("language", 0).coerceIn(0, 3)
        themeIndex = prefs.getInt("themeIndex", 0)
            .coerceIn(0, themes.size - 1)
        keySizeLevel = prefs.getInt("keySizeLevel", 1).coerceIn(0, 2)
    }

    private data class KeySpec(
        val normal: String,
        val shifted: String = "",
        val longPress: String = ""
    )

    private fun k(
        a: String,
        b: String = "",
        c: String = ""
    ) = KeySpec(a, b, c)

    private val offlineWords = mapOf(
        0 to listOf(
            "الله", "محمد", "اسلام", "قرآن", "کتاب",
            "ښوونځی", "پوهنتون", "پښتو", "ژبه",
            "علم", "استاد", "زده کړه", "ملګری",
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
        ),
        3 to listOf(
            "الله", "محمد", "اسلام", "قرآن", "کتاب",
            "مکتب", "پوهنتون", "دري", "زبان",
            "علم", "استاد", "شاگرد", "دوست",
            "برادر", "خواهر", "خانه", "کار", "زندگی",
            "خوب", "تشکر", "سلام", "روز", "شب"
        )
    )

    // Microsoft Windows Pashto (Afghanistan), KLID 00000463.
    // Rows follow the physical QWERTY positions; shifted characters match Windows.
    private val pashtoRows = listOf(
        listOf(
            k("ض", "ْ", "€"), k("ص", "ٌ", "ٱ"), k("ث", "ٍ", "ى"),
            k("ق", "ً", "\u200E"), k("ف", "ُ", "\u200F"), k("غ", "ِ", "ٓ"),
            k("ع", "َ", "ٙ"), k("ه", "ّ", "ٰ"),
            k("خ", "ځ", "'"), k("ح", "څ", "\""),
            k("ج", "]", "}"), k("چ", "[", "{"),
            k("\\", "*", "|")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ۍ"), k("ی", "ي", "ے"),
            k("ب", "پ", "ں"), k("ل", "أ", "ڷ"), k("ا", "آ", "إ"),
            k("ت", "ټ", "ٹ"), k("ن", "ڼ", ">"), k("م", "ة", "<"),
            k("ک", ":", "ك"), k("ګ", "؛", "گ")
        ),
        listOf(
            k("ظ", "ئ", "?"), k("ط", "ې", ";"), k("ز", "ژ", "ء"),
            k("ر", "ء"), k("ذ", "‌"), k("د", "ډ", "ڈ"),
            k("ړ", "ؤ", "ڑ"), k("و", "،", ","), k("ږ", ".", "ۇ"),
            k("/", "؟", "ۉ")
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

    private val dariRows = listOf(
        listOf(
            k("ض", "ْ"), k("ص", "ٌ"), k("ث", "ٍ"),
            k("ق", "ً"), k("ف", "ُ"), k("غ", "ِ"),
            k("ع", "َ"), k("ه", "ّ"), k("خ", "أ"),
            k("ح", "آ"), k("ج", "ئ"), k("چ", "ء")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ث"), k("ی", "ي"),
            k("ب", "پ"), k("ل", "ل"), k("ا", "آ"),
            k("ت", "ټ"), k("ن", "ڼ"), k("م", "ة"),
            k("ک", "ك"), k("گ", "ګ")
        ),
        listOf(
            k("ظ", "ژ"), k("ط", "ئ"), k("ز", "ذ"),
            k("ر", "ړ"), k("د", "ډ"), k("و", "ؤ"),
            k("پ", "ث"), k("،", "؛"), k("/", "؟")
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

    // Windows Pashto (Afghanistan) number row: base, Shift, and AltGr layers.
    private val pashtoNumbers = listOf(
        k("\u200D", "ٔ", "€"),
        k("۱", "!", "ٱ"), k("۲", "٬", "ى"),
        k("۳", "٫", "\u200E"), k("۴", "؋", "\u200F"),
        k("۵", "٪", "ٓ"), k("۶", "×", "ٙ"),
        k("۷", "»", "ٰ"), k("۸", "«", "ے"),
        k("۹", ")", "ں"), k("۰", "(", "ڷ"),
        k("-", "ـ", "_"), k("=", "+", "÷")
    )

    private val arabicNumbers = listOf(
        k("١", "!", "1"), k("٢", "@", "2"),
        k("٣", "#", "3"), k("٤", "$", "4"),
        k("٥", "%", "5"), k("٦", "^", "6"),
        k("٧", "&", "7"), k("٨", "*", "8"),
        k("٩", "(", "9"), k("٠", ")", "0"),
        k("-", "_"), k("=", "+")
    )

    private val dariNumbers = listOf(
        k("۱", "!"), k("۲", "@"), k("۳", "#"),
        k("۴", "$"), k("۵", "%"), k("۶", "^"),
        k("۷", "&"), k("۸", "*"), k("۹", "("),
        k("۰", ")"), k("-", "_"), k("=", "+")
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

    override fun onStartInputView(
        info: EditorInfo,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        updateSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        updateSuggestions()
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
            setBackgroundColor(theme.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = when (language) {
                0 -> "SHARAFZAI  •  پښتو"
                1 -> "SHARAFZAI  •  العربية"
                2 -> "SHARAFZAI  •  ENGLISH"
                else -> "SHARAFZAI  •  دری"
            }
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(29), 1f)
        )

        addSpecial(header, "🌐", 0.8f, 29) {
            language = (language + 1) % 4
            shifted = false
            caps = false
            emojiPanelVisible = false
            diacriticsPanelVisible = false
            symbolsPanelVisible = false
            calculatorPanelVisible = false
            toolsPanelVisible = false
            settingsPanelVisible = false
            saveSettings()
            refreshKeyboard()
        }

        addSpecial(header, "＋", 0.8f, 29) {
            addCurrentWordToDictionary()
        }

        addSpecial(header, "↕", 0.8f, 29) {
            keySizeLevel = (keySizeLevel + 1) % 3
            saveSettings()
            refreshKeyboard()
            val sizeLabel = listOf("وړه", "منځنۍ", "لویه")[keySizeLevel]
            Toast.makeText(this, "د تڼیو اندازه: $sizeLabel", Toast.LENGTH_SHORT).show()
        }

        addSpecial(header, "🎨", 0.8f, 29) {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }

        addSpecial(header, "⚙", 0.8f, 29) {
            settingsPanelVisible = !settingsPanelVisible
            if (settingsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                calculatorPanelVisible = false
                toolsPanelVisible = false
            }
            refreshKeyboard()
        }

        root.addView(header, rowParams(31))

        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(theme.suggestion)
        }

        root.addView(suggestionContainer, rowParams(32))

        val numberRow = when (language) {
            0 -> pashtoNumbers
            1 -> arabicNumbers
            2 -> englishNumbers
            else -> dariNumbers
        }

        addKeyRow(root, numberRow, 37)

        val rows = when (language) {
            0 -> pashtoRows
            1 -> arabicRows
            2 -> englishRows
            else -> dariRows
        }

        rows.forEach { row ->
            addKeyRow(root, row, 42)
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addSpecial(controlRow, "Tab", 0.9f, 39) {
            currentInputConnection?.commitText("\t", 1)
        }

        addSpecial(controlRow, "☺", 0.9f, 39) {
            emojiPanelVisible = !emojiPanelVisible
            if (emojiPanelVisible) {
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                calculatorPanelVisible = false
                toolsPanelVisible = false
                settingsPanelVisible = false
            }
            refreshKeyboard()
        }

        if (language == 1 || language == 3) {
            addSpecial(controlRow, "◌َ", 0.9f, 39) {
                diacriticsPanelVisible = !diacriticsPanelVisible
                if (diacriticsPanelVisible) {
                    emojiPanelVisible = false
                    symbolsPanelVisible = false
                    calculatorPanelVisible = false
                    toolsPanelVisible = false
                    settingsPanelVisible = false
                }
                refreshKeyboard()
            }
        }

        addSpecial(controlRow, "#+=", 0.9f, 39) {
            symbolsPanelVisible = !symbolsPanelVisible
            if (symbolsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                calculatorPanelVisible = false
                toolsPanelVisible = false
                settingsPanelVisible = false
            }
            refreshKeyboard()
        }

        addSpecial(controlRow, "🧮", 0.9f, 39) {
            calculatorPanelVisible = !calculatorPanelVisible
            if (calculatorPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                toolsPanelVisible = false
                settingsPanelVisible = false
            }
            refreshKeyboard()
        }

        addSpecial(controlRow, "Tools", 1.0f, 39) {
            toolsPanelVisible = !toolsPanelVisible
            if (toolsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                calculatorPanelVisible = false
                settingsPanelVisible = false
            }
            refreshKeyboard()
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
            deletePreviousCharacter()
            updateSuggestions()
        }

        root.addView(controlRow, rowParams(41))

        if (diacriticsPanelVisible && (language == 1 || language == 3)) {
            val diacriticKeys = listOf(
                k("َ"), k("ً"), k("ُ"), k("ٌ"),
                k("ِ"), k("ٍ"), k("ْ"), k("ّ"),
                k("ٰ"), k("ٓ"), k("ٔ"), k("ٖ")
            )
            addKeyRow(root, diacriticKeys, 34)
        }

        if (symbolsPanelVisible) {
            val symbolKeys = listOf(
                k("@"), k("#"), k("$"), k("%"),
                k("&"), k("*"), k("+"), k("="),
                k("/"), k("\\"), k("("), k(")")
            )
            addKeyRow(root, symbolKeys, 34)
        }

        if (emojiPanelVisible) {
            val emojiKeys = listOf(
                k("😀"), k("😂"), k("🥰"), k("😍"),
                k("😊"), k("😉"), k("😭"), k("😎"),
                k("🙏"), k("❤️"), k("👍"), k("🌹")
            )
            addKeyRow(root, emojiKeys, 34)
        }

        if (calculatorPanelVisible) {
            addCalculatorPanel(root)
        }

        if (toolsPanelVisible) {
            addToolsPanel(root)
        }

        if (settingsPanelVisible) {
            addSettingsPanel(root)
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addSpecial(bottom, "←", 0.8f, 42) {
            sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
        }

        addSpecial(bottom, "→", 0.8f, 42) {
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        addSpecial(bottom, "،", 0.7f, 42) {
            val value = if (language == 2) "," else "،"
            currentInputConnection?.commitText(value, 1)
            updateSuggestions()
        }

        addSpecial(bottom, "Space", 3.5f, 42) {
            currentInputConnection?.commitText(" ", 1)
            updateSuggestions()
        }

        addSpecial(bottom, ".", 0.7f, 42) {
            currentInputConnection?.commitText(".", 1)
            updateSuggestions()
        }

        addSpecial(bottom, "↵", 1f, 42) {
            performEnterAction()
        }

        root.addView(bottom, rowParams(44))

        return root
    }

    private fun addSettingsPanel(parent: LinearLayout) {
        val title = TextView(this).apply {
            text = "د شرفزی کیبورډ تنظیمات"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        parent.addView(title, rowParams(30))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addSpecial(row, "رنګ: ${themeIndex + 1}", 1.2f, 40) {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(row, "اندازه: ${listOf("وړه", "منځنۍ", "لویه")[keySizeLevel]}", 1.4f, 40) {
            keySizeLevel = (keySizeLevel + 1) % 3
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(row, "ژبه: ${listOf("پښتو", "عربي", "English", "دري")[language]}", 1.5f, 40) {
            language = (language + 1) % 4
            shifted = false
            caps = false
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(row, "بند", 0.7f, 40) {
            settingsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(row, rowParams(44))
    }

    private fun addToolsPanel(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addSpecial(row, "📅 Calendar", 1.5f, 40) {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR)
            openSystemFeature(intent, "Calendar")
        }
        addSpecial(row, "⏰ Alarms", 1.5f, 40) {
            openSystemFeature(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Clock")
        }
        addSpecial(row, "Close", 1f, 40) {
            toolsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(row, rowParams(43))
    }

    private fun openSystemFeature(intent: Intent, featureName: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(
                this,
                "$featureName اپلیکیشن په وسیله کې ونه موندل شو",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addCalculatorPanel(parent: LinearLayout) {
        val display = TextView(this).apply {
            text = calculatorExpression.ifEmpty { "0" }
            textSize = 23f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            background = roundedBackground(theme.suggestion, dp(8))
        }
        parent.addView(display, rowParams(42))

        fun addCalculatorRow(labels: List<String>) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            labels.forEach { label ->
                addSpecial(row, label, 1f, 36) {
                    when (label) {
                        "C" -> calculatorExpression = ""
                        "⌫" -> calculatorExpression = calculatorExpression.dropLast(1)
                        "=" -> {
                            val result = runCatching {
                                CalculatorParser(calculatorExpression).parse()
                            }.getOrNull()
                            if (result == null) {
                                Toast.makeText(this, "د محاسبې عبارت سم نه دی", Toast.LENGTH_SHORT).show()
                            } else {
                                val answer = java.math.BigDecimal.valueOf(result)
                                    .stripTrailingZeros()
                                    .toPlainString()
                                currentInputConnection?.commitText(answer, 1)
                                calculatorExpression = answer
                                calculatorPanelVisible = false
                            }
                        }
                        else -> calculatorExpression += when (label) {
                            "×" -> "*"
                            "÷" -> "/"
                            else -> label
                        }
                    }
                    refreshKeyboard()
                }
            }
            parent.addView(row, rowParams(39))
        }

        addCalculatorRow(listOf("7", "8", "9", "÷"))
        addCalculatorRow(listOf("4", "5", "6", "×"))
        addCalculatorRow(listOf("1", "2", "3", "−"))
        addCalculatorRow(listOf("C", "0", ".", "+"))
        addCalculatorRow(listOf("(", ")", "⌫", "="))
    }

    private class CalculatorParser(private val input: String) {
        private var position = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            require(position == input.length && value.isFinite())
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    take('+') -> value + parseTerm()
                    take('-') || take('−') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                value = when {
                    take('*') || take('×') -> value * parseFactor()
                    take('/') || take('÷') -> {
                        val divisor = parseFactor()
                        require(divisor != 0.0)
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (take('+')) return parseFactor()
            if (take('-') || take('−')) return -parseFactor()
            if (take('(')) {
                val value = parseExpression()
                skipSpaces()
                require(take(')'))
                return value
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = position
            var hasDigit = false
            while (position < input.length && input[position].isDigit()) {
                position++
                hasDigit = true
            }
            if (position < input.length && input[position] == '.') {
                position++
                while (position < input.length && input[position].isDigit()) {
                    position++
                    hasDigit = true
                }
            }
            require(hasDigit)
            return input.substring(start, position).toDouble()
        }

        private fun take(char: Char): Boolean {
            if (position < input.length && input[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) {
                position++
            }
        }
    }

    private fun addKeyRow(
        parent: LinearLayout,
        keys: List<KeySpec>,
        height: Int
    ) {
        val actualHeight = (height * listOf(0.90f, 1.0f, 1.10f)[keySizeLevel]).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEachIndexed { index, spec ->

            val frame = FrameLayout(this)

            val value = when {
                shifted && spec.shifted.isNotEmpty() ->
                    spec.shifted

                language == 2 && (caps || shifted) ->
                    spec.normal.uppercase()

                else -> spec.normal
            }

            val mainText = TextView(this).apply {
                text = value
                textSize = if (keys.size >= 12) 15f else 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }

            frame.addView(
                mainText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            if (spec.shifted.isNotEmpty() ||
                spec.longPress.isNotEmpty()
            ) {
                val hint = TextView(this).apply {
                    text = "اوږد"
                    textSize = 7f
                    setTextColor(theme.accent)
                    gravity = Gravity.TOP or Gravity.END
                    setPadding(0, dp(2), dp(3), 0)
                }

                frame.addView(
                    hint,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    )
                )
            }

            frame.background = roundedBackground(
                theme.keyColors[index % theme.keyColors.size],
                dp(8)
            )

            frame.setOnClickListener {
                val before = currentInputConnection
                    ?.getTextBeforeCursor(30, 0)
                    ?.toString().orEmpty()

                val previous = before.trimEnd().lastOrNull()

                val sentenceStart =
                    before.isBlank() ||
                    previous == null ||
                    previous in listOf('.', '?', '!', '؟', '\n')

                val output = when {
                    shifted && spec.shifted.isNotEmpty() ->
                        spec.shifted

                    language == 2 &&
                        (caps || shifted || sentenceStart) ->
                        spec.normal.uppercase()

                    else -> spec.normal
                }

                currentInputConnection?.commitText(output, 1)

                if (shifted && !caps) {
                    shifted = false
                    refreshKeyboard()
                }

                updateSuggestions()
            }

            frame.setOnLongClickListener {
                val alternate = when {
                    spec.longPress.isNotEmpty() ->
                        spec.longPress

                    spec.shifted.isNotEmpty() ->
                        spec.shifted

                    language == 2 ->
                        spec.normal.uppercase()

                    else -> ""
                }

                if (alternate.isNotEmpty()) {
                    val preview = TextView(this).apply {
                        text = alternate
                        textSize = 25f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = roundedBackground(
                            theme.special,
                            dp(10)
                        )
                    }

                    val popup = PopupWindow(
                        preview,
                        dp(60),
                        dp(52),
                        true
                    )

                    preview.setOnClickListener {
                        currentInputConnection?.commitText(
                            alternate,
                            1
                        )
                        updateSuggestions()
                        popup.dismiss()
                    }

                    popup.showAsDropDown(
                        frame,
                        0,
                        -frame.height - dp(52)
                    )

                    preview.postDelayed({
                        if (popup.isShowing) {
                            popup.dismiss()
                        }
                    }, 3000)
                }

                true
            }

            val params = LinearLayout.LayoutParams(
                0,
                dp(actualHeight),
                1f
            )

            params.setMargins(
                dp(1),
                dp(2),
                dp(1),
                dp(2)
            )

            row.addView(frame, params)
        }

        parent.addView(row, rowParams(actualHeight + 3))
    }

    private fun addSpecial(
        parent: LinearLayout,
        label: String,
        weight: Float,
        height: Int,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = roundedBackground(
                theme.special,
                dp(8)
            )
        }

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
            button.setOnClickListener {
                action()
            }
        }

        val params = LinearLayout.LayoutParams(
            0,
            dp(height),
            weight
        )

        params.setMargins(
            dp(2),
            dp(2),
            dp(2),
            dp(2)
        )

        parent.addView(button, params)
    }

    private fun isWordCharacter(char: Char): Boolean {
        val type = Character.getType(char)

        return char.isLetterOrDigit() ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun getCurrentWord(text: String): String {
        return text.takeLastWhile {
            isWordCharacter(it)
        }
    }

    private fun getOfflineSuggestions(): List<String> {
        val before = currentInputConnection
            ?.getTextBeforeCursor(60, 0)
            ?.toString().orEmpty()

        val currentWord = getCurrentWord(before)

        if (currentWord.isEmpty()) {
            return emptyList()
        }

        val personalWords = getSharedPreferences(
            "SharafzaiSettings",
            MODE_PRIVATE
        ).getStringSet("personalWords_$language", emptySet()).orEmpty()

        return (offlineWords[language].orEmpty() + personalWords)
            .distinct()
            .filter {
                it.startsWith(
                    currentWord,
                    ignoreCase = true
                ) &&
                !it.equals(
                    currentWord,
                    ignoreCase = true
                )
            }
            .take(3)
    }

    private fun addCurrentWordToDictionary() {
        val before = currentInputConnection
            ?.getTextBeforeCursor(80, 0)
            ?.toString().orEmpty()
        val word = getCurrentWord(before).trim()

        if (word.length < 2) {
            Toast.makeText(this, "لومړی یوه کلمه ولیکئ", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val dictionaryKey = "personalWords_$language"
        val saved = prefs.getStringSet(dictionaryKey, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        saved.add(word)
        prefs.edit().putStringSet(dictionaryKey, saved).apply()
        Toast.makeText(this, "کلمه شخصي قاموس ته اضافه شوه", Toast.LENGTH_SHORT).show()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val container = suggestionContainer ?: return

        container.removeAllViews()

        val suggestions = getOfflineSuggestions()

        suggestions.forEach { word ->

            val suggestion = TextView(this).apply {
                text = word
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(
                    dp(10),
                    dp(3),
                    dp(10),
                    dp(3)
                )

                setOnClickListener {
                    val before = currentInputConnection
                        ?.getTextBeforeCursor(60, 0)
                        ?.toString().orEmpty()

                    val currentWord = getCurrentWord(before)

                    if (currentWord.isNotEmpty()) {
                        currentInputConnection
                            ?.deleteSurroundingText(
                                currentWord.length,
                                0
                            )
                    }

                    currentInputConnection?.commitText(
                        word,
                        1
                    )

                    updateSuggestions()
                }
            }

            val params = LinearLayout.LayoutParams(
                0,
                dp(30),
                1f
            )

            container.addView(suggestion, params)
        }
    }

    private fun deletePreviousCharacter() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        if (before.isEmpty()) return

        fun previousCodePointStart(index: Int): Int =
            Character.offsetByCodePoints(before, index, -1)

        fun isGraphemeExtend(codePoint: Int): Boolean {
            val type = Character.getType(codePoint)
            return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                codePoint in 0xFE00..0xFE0F ||
                codePoint in 0xE0100..0xE01EF ||
                codePoint in 0x1F3FB..0x1F3FF ||
                codePoint == 0x20E3
        }

        var start = previousCodePointStart(before.length)
        var codePoint = Character.codePointAt(before, start)

        // Include combining marks, variation selectors and skin-tone modifiers.
        while (isGraphemeExtend(codePoint) && start > 0) {
            start = previousCodePointStart(start)
            codePoint = Character.codePointAt(before, start)
        }

        // Delete a complete emoji joined with ZWJ, such as a family emoji.
        while (start > 0) {
            val possibleJoinerStart = previousCodePointStart(start)
            if (Character.codePointAt(before, possibleJoinerStart) != 0x200D) break
            start = possibleJoinerStart
            if (start == 0) break
            start = previousCodePointStart(start)
            codePoint = Character.codePointAt(before, start)
            while (isGraphemeExtend(codePoint) && start > 0) {
                start = previousCodePointStart(start)
                codePoint = Character.codePointAt(before, start)
            }
        }

        // Regional-indicator flags are made from pairs of code points.
        if (codePoint in 0x1F1E6..0x1F1FF && start > 0) {
            val previousStart = previousCodePointStart(start)
            val previousCodePoint = Character.codePointAt(before, previousStart)
            if (previousCodePoint in 0x1F1E6..0x1F1FF) start = previousStart
        }

        connection.deleteSurroundingText(before.length - start, 0)
    }

    private fun performEnterAction() {
        val connection = currentInputConnection ?: return
        val info = currentInputEditorInfo

        if (info == null) {
            sendKey(KeyEvent.KEYCODE_ENTER)
            return
        }

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction =
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        val supportsEditorAction =
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_NEXT ||
            action == EditorInfo.IME_ACTION_DONE ||
            action == EditorInfo.IME_ACTION_PREVIOUS

        if (!noEnterAction && supportsEditorAction) {
            val handled = connection.performEditorAction(action)

            if (!handled) {
                sendKey(KeyEvent.KEYCODE_ENTER)
            }
        } else {
            sendKey(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), theme.border)
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

    private fun rowParams(
        height: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(height)
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun saveSettings() {
        getSharedPreferences(
            "SharafzaiSettings",
            MODE_PRIVATE
        ).edit()
            .putInt("language", language)
            .putInt("themeIndex", themeIndex)
            .putInt("keySizeLevel", keySizeLevel)
            .apply()
    }
}

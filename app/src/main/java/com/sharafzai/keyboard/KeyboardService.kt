from pathlib import Path

code = r'''package com.sharafzai.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import java.util.Locale

class KeyboardService : InputMethodService() {

    private data class Theme(
        val name: String,
        val background: Int,
        val key: Int,
        val special: Int,
        val text: Int,
        val accent: Int
    )

    private data class KeySpec(
        val label: String,
        val output: String = label,
        val alternates: String = "",
        val action: String = ""
    )

    private var language = 0
    private var shifted = false
    private var caps = false
    private var themeIndex = 0

    private var keyboardRoot: LinearLayout? = null
    private var suggestionContainer: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false

    private val themes = listOf(
        Theme("سمندري", Color.rgb(17, 35, 61), Color.rgb(39, 66, 99),
            Color.rgb(23, 119, 173), Color.WHITE, Color.rgb(91, 190, 245)),
        Theme("ارغواني", Color.rgb(39, 27, 55), Color.rgb(72, 51, 91),
            Color.rgb(139, 82, 174), Color.WHITE, Color.rgb(205, 151, 244)),
        Theme("شنه", Color.rgb(21, 43, 37), Color.rgb(43, 76, 64),
            Color.rgb(39, 133, 91), Color.WHITE, Color.rgb(113, 213, 158)),
        Theme("خړ", Color.rgb(35, 38, 43), Color.rgb(65, 69, 76),
            Color.rgb(95, 105, 117), Color.WHITE, Color.rgb(190, 201, 214))
    )

    private val dictionaries = mapOf(
        0 to listOf(
            "زه", "ته", "هغه", "موږ", "تاسې", "دوی", "دا", "هغه", "چې", "څه",
            "ولې", "څنګه", "کوم", "کومه", "کور", "کتاب", "ښوونځی", "پوهنتون",
            "افغانستان", "پښتو", "ژبه", "ملګری", "ورور", "خور", "مور", "پلار",
            "الله", "رحمت", "سلام", "وعلیکم", "مننه", "مهرباني", "ښه", "ډېر",
            "کار", "وخت", "نن", "سبا", "پرون", "لیک", "لیکنه", "علم", "درس"
        ),
        1 to listOf(
            "أنا", "أنت", "هو", "هي", "نحن", "أنتم", "هذا", "هذه", "ذلك",
            "الذي", "التي", "كيف", "ماذا", "لماذا", "كتاب", "مدرسة", "جامعة",
            "العربية", "أفغانستان", "السلام", "عليكم", "شكرا", "مرحبا", "الله",
            "رحمة", "علم", "درس", "عمل", "وقت", "اليوم", "غدا"
        ),
        2 to listOf(
            "the", "this", "that", "there", "their", "they", "you", "your",
            "we", "what", "when", "where", "why", "which", "with", "from",
            "have", "hello", "thanks", "please", "keyboard", "language",
            "English", "Afghanistan", "good", "great", "work", "world",
            "today", "tomorrow", "book", "school", "university"
        )
    )

    private val deleteRunnable = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            deleteOne()
            handler.postDelayed(this, 55L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("sharafzai_keyboard", MODE_PRIVATE)
        language = prefs.getInt("language", 0).coerceIn(0, 2)
        themeIndex = prefs.getInt("theme", 0).coerceIn(0, themes.lastIndex)
    }

    override fun onCreateInputView(): View {
        return buildKeyboard()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shifted = false
        caps = false
        updateSuggestions()
    }

    private fun buildKeyboard(): View {
        val theme = currentTheme()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
            setBackgroundColor(theme.background)
        }
        keyboardRoot = root

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SHARAFZAI"
            setTextColor(theme.accent)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        header.addView(makeHeaderButton(languageName()) {
            language = (language + 1) % 3
            shifted = false
            saveSettings()
            refreshKeyboard()
        }, LinearLayout.LayoutParams(dp(94), dp(36)))
        header.addView(makeHeaderButton("🎨 ${theme.name}") {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }, LinearLayout.LayoutParams(dp(108), dp(36)))
        root.addView(header)

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        suggestionContainer = suggestions
        root.addView(suggestions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(39)
        ))

        addKeyRow(root, listOf(
            KeySpec("1"), KeySpec("2"), KeySpec("3"), KeySpec("4"), KeySpec("5"),
            KeySpec("6"), KeySpec("7"), KeySpec("8"), KeySpec("9"), KeySpec("0")
        ))

        when (language) {
            0 -> buildPashtoRows(root)
            1 -> buildArabicRows(root)
            else -> buildEnglishRows(root)
        }

        addKeyRow(root, listOf(
            KeySpec("←", action = "left"),
            KeySpec("→", action = "right"),
            KeySpec(",", alternates = "، ؛"),
            KeySpec("؟", alternates = "? !"),
            KeySpec(".", alternates = "… :"),
            KeySpec("⌫", action = "delete")
        ))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(makeKey(KeySpec("⇧", action = "shift"), theme),
            weightedParams(0.95f))
        bottom.addView(makeKey(KeySpec("🌐", action = "language"), theme),
            weightedParams(0.85f))
        bottom.addView(makeKey(KeySpec("،", alternates = ", ;"), theme),
            weightedParams(0.75f))
        bottom.addView(makeKey(KeySpec("Space", action = "space"), theme),
            weightedParams(3.6f))
        bottom.addView(makeKey(KeySpec("↵", action = "enter"), theme),
            weightedParams(0.95f))
        root.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ))

        return root
    }

    private fun buildPashtoRows(root: LinearLayout) {
        addKeyRow(root, listOf(
            KeySpec("ض", alternates = "ْ ّ"), KeySpec("ص", alternates = "ً ٌ"),
            KeySpec("ث", alternates = "ٍ ْ"), KeySpec("ق", alternates = "ٰ"),
            KeySpec("ف", alternates = "ڤ"), KeySpec("غ", alternates = "ـ"),
            KeySpec("ع", alternates = "ء"), KeySpec("ه", alternates = "ۀ"),
            KeySpec("خ"), KeySpec("ح"), KeySpec("ج"), KeySpec("چ")
        ))
        addKeyRow(root, listOf(
            KeySpec("ش"), KeySpec("س"), KeySpec("ی", alternates = "ي ى"),
            KeySpec("ب"), KeySpec("ل"), KeySpec("ا", alternates = "آ أ إ"),
            KeySpec("ت"), KeySpec("ن"), KeySpec("م"), KeySpec("ک", alternates = "ك"),
            KeySpec("ګ", alternates = "گ")
        ))
        addKeyRow(root, listOf(
            KeySpec("ظ"), KeySpec("ط"), KeySpec("ز"), KeySpec("ر"),
            KeySpec("ذ"), KeySpec("د"), KeySpec("ړ"), KeySpec("و"),
            KeySpec("ۍ", alternates = "ي"), KeySpec("ؤ", alternates = "ئ")
        ))
        addKeyRow(root, listOf(
            KeySpec("پ"), KeySpec("ژ"), KeySpec("ږ"), KeySpec("ښ"),
            KeySpec("ډ"), KeySpec("ټ"), KeySpec("ڼ"), KeySpec("څ"),
            KeySpec("ځ"), KeySpec("ئ", alternates = "ء")
        ))
    }

    private fun buildArabicRows(root: LinearLayout) {
        addKeyRow(root, listOf(
            KeySpec("ض"), KeySpec("ص"), KeySpec("ث"), KeySpec("ق"),
            KeySpec("ف"), KeySpec("غ"), KeySpec("ع"), KeySpec("ه"),
            KeySpec("خ"), KeySpec("ح"), KeySpec("ج"), KeySpec("د")
        ))
        addKeyRow(root, listOf(
            KeySpec("ش"), KeySpec("س"), KeySpec("ي"), KeySpec("ب"),
            KeySpec("ل"), KeySpec("ا"), KeySpec("ت"), KeySpec("ن"),
            KeySpec("م"), KeySpec("ك"), KeySpec("ط")
        ))
        addKeyRow(root, listOf(
            KeySpec("ئ"), KeySpec("ء"), KeySpec("ؤ"), KeySpec("ر"),
            KeySpec("لا"), KeySpec("ى"), KeySpec("ة"), KeySpec("و"),
            KeySpec("ز"), KeySpec("ظ")
        ))
        addKeyRow(root, listOf(
            KeySpec("ذ"), KeySpec("د"), KeySpec("أ"), KeySpec("إ"),
            KeySpec("آ"), KeySpec("ة"), KeySpec("ٰ"), KeySpec("پ"),
            KeySpec("چ"), KeySpec("ژ")
        ))
    }

    private fun buildEnglishRows(root: LinearLayout) {
        addKeyRow(root, "qwertyuiop".map { KeySpec(it.toString(),
            alternates = when (it) {
                'a' -> "á à â ä æ"
                'e' -> "é è ê ë"
                'i' -> "í ì î ï"
                'o' -> "ó ò ô ö"
                'u' -> "ú ù û ü"
                'n' -> "ñ"
                'c' -> "ç"
                else -> ""
            }) })
        addKeyRow(root, "asdfghjkl".map { KeySpec(it.toString()) })
        addKeyRow(root, listOf(KeySpec("⇧", action = "shift")) +
            "zxcvbnm".map { KeySpec(it.toString(),
                alternates = if (it == 'n') "ñ" else "") } +
            listOf(KeySpec("⌫", action = "delete")))
        addKeyRow(root, listOf(
            KeySpec("'", alternates = "\" `"),
            KeySpec("-", alternates = "_ –"),
            KeySpec("@", alternates = "# $"),
            KeySpec(":", alternates = ";"),
            KeySpec("/", alternates = "\\"),
            KeySpec("(", alternates = "[ {"),
            KeySpec(")", alternates = "] }")
        ))
    }

    private fun addKeyRow(root: LinearLayout, keys: List<KeySpec>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(55)
        ).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        }
        keys.forEach { spec ->
            val weight = when (spec.action) {
                "shift", "delete" -> 1.35f
                else -> 1f
            }
            row.addView(makeKey(spec, currentTheme()), weightedParams(weight))
        }
        root.addView(row, params)
    }

    private fun makeKey(spec: KeySpec, theme: Theme): View {
        val special = spec.action.isNotEmpty()
        val frame = FrameLayout(this)
        val button = Button(this).apply {
            text = displayLabel(spec)
            textSize = when {
                spec.label.length > 2 -> 13f
                else -> 21f
            }
            setTextColor(theme.text)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            stateListAnimator = null
            setPadding(dp(1), 0, dp(1), 0)
            background = roundedDrawable(
                if (special) theme.special else theme.key,
                dp(8)
            )
        }
        frame.addView(button, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (spec.alternates.isNotBlank()) {
            val hint = TextView(this).apply {
                text = "اوږد"
                textSize = 8f
                setTextColor(theme.accent)
                gravity = Gravity.END or Gravity.BOTTOM
                setPadding(0, 0, dp(4), dp(2))
                isClickable = false
            }
            frame.addView(hint, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ))
        }

        button.setOnClickListener { handleKey(spec) }
        if (spec.alternates.isNotBlank()) {
            button.setOnLongClickListener {
                showAlternates(frame, spec.alternates)
                true
            }
        }
        if (spec.action == "delete") {
            button.setOnLongClickListener {
                startDeleteRepeat()
                true
            }
            button.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP ||
                    event.action == MotionEvent.ACTION_CANCEL) {
                    stopDeleteRepeat()
                }
                false
            }
        }
        return frame
    }

    private fun handleKey(spec: KeySpec) {
        when (spec.action) {
            "shift" -> {
                if (language == 2) {
                    if (shifted) caps = !caps
                    else shifted = true
                } else {
                    shifted = !shifted
                }
                refreshKeyboard()
            }
            "delete" -> deleteOne()
            "space" -> commitText(" ")
            "enter" -> sendEnter()
            "left" -> sendArrow(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT)
            "language" -> {
                language = (language + 1) % 3
                shifted = false
                saveSettings()
                refreshKeyboard()
            }
            else -> {
                var output = spec.output
                if (language == 2 && (shifted || caps)) {
                    output = output.uppercase(Locale.ROOT)
                }
                commitText(output)
                if (shifted && !caps) {
                    shifted = false
                    refreshKeyboard()
                }
            }
        }
    }

    private fun displayLabel(spec: KeySpec): String {
        if (spec.action.isNotEmpty()) return spec.label
        if (language == 2 && (shifted || caps)) {
            return spec.label.uppercase(Locale.ROOT)
        }
        return spec.label
    }

    private fun showAlternates(anchor: View, choices: String) {
        val theme = currentTheme()
        val popupContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = roundedDrawable(theme.background, dp(10))
        }
        choices.split(" ").filter { it.isNotBlank() }.forEach { value ->
            val option = Button(this).apply {
                text = value
                textSize = 20f
                setTextColor(theme.text)
                isAllCaps = false
                minWidth = dp(42)
                minimumWidth = dp(42)
                background = roundedDrawable(theme.special, dp(7))
                setOnClickListener {
                    commitText(value)
                    (popupContent.parent as? PopupWindow)?.dismiss()
                }
            }
            popupContent.addView(option, LinearLayout.LayoutParams(
                dp(46), dp(48)
            ).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        val popup = PopupWindow(
            popupContent,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(roundedDrawable(theme.background, dp(10)))
        popup.elevation = dp(8).toFloat()
        popup.isOutsideTouchable = true
        popup.showAsDropDown(anchor, 0, -dp(62))
        // Keep a reference on the popup content so its options can close it.
        popupContent.tag = popup
        for (index in 0 until popupContent.childCount) {
            val option = popupContent.getChildAt(index) as Button
            option.setOnClickListener {
                commitText(option.text.toString())
                popup.dismiss()
            }
        }
    }

    private fun commitText(value: String) {
        val connection = currentInputConnection ?: return
        var text = value
        if (language == 2 && value.length == 1 && value[0].isLetter()) {
            val before = connection.getTextBeforeCursor(3, 0)?.toString().orEmpty()
            if (before.isEmpty() || before.lastOrNull() in listOf('.', '!', '?', '\n')) {
                text = value.uppercase(Locale.ROOT)
            }
        }
        connection.commitText(text, 1)
        updateSuggestions()
    }

    private fun deleteOne() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    private fun startDeleteRepeat() {
        if (deleteRepeating) return
        deleteRepeating = true
        handler.removeCallbacks(deleteRunnable)
        handler.postDelayed(deleteRunnable, 420L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeating = false
        handler.removeCallbacks(deleteRunnable)
    }

    private fun sendEnter() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            action != EditorInfo.IME_ACTION，上) {
            connection.performEditorAction(action)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun sendArrow(keyCode: Int) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    private fun updateSuggestions() {
        val container = suggestionContainer ?: return
        val theme = currentTheme()
        container.removeAllViews()
        val before = currentInputConnection?.getTextBeforeCursor(48, 0)
            ?.toString().orEmpty()
        val currentWord = Regex("[\\p{L}]+$").find(before)?.value.orEmpty()
        if (currentWord.isBlank()) {
            val hint = TextView(this).apply {
                text = "Sharafzai • ${languageName()}"
                textSize = 12f
                setTextColor(theme.accent)
                gravity = Gravity.CENTER
            }
            container.addView(hint, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            return
        }

        val matches = dictionaries[language].orEmpty()
            .filter { it.startsWith(currentWord, ignoreCase = true) }
            .distinct()
            .take(4)

        if (matches.isEmpty()) {
            val current = TextView(this).apply {
                text = currentWord
                textSize = 13f
                setTextColor(theme.text)
                gravity = Gravity.CENTER
            }
            container.addView(current, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ))
        } else {
            matches.forEach { word ->
                val suggestion = TextView(this).apply {
                    text = word
                    textSize = 14f
                    setTextColor(theme.text)
                    gravity = Gravity.CENTER
                    setPadding(dp(7), 0, dp(7), 0)
                    isClickable = true
                    setOnClickListener {
                        val length = currentWord.length
                        currentInputConnection?.deleteSurroundingText(length, 0)
                        currentInputConnection?.commitText("$word ", 1)
                        updateSuggestions()
                    }
                }
                container.addView(suggestion, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
                ))
            }
        }
    }

    private fun makeHeaderButton(label: String, action: () -> Unit): TextView {
        val theme = currentTheme()
        return TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.text)
            gravity = Gravity.CENTER
            background = roundedDrawable(theme.special, dp(8))
            setOnClickListener { action() }
        }
    }

    private fun weightedParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun currentTheme(): Theme = themes[themeIndex]

    private fun languageName(): String {
        return when (language) {
            0 -> "پښتو"
            1 -> "عربي"
            else -> "English"
        }
    }

    private fun saveSettings() {
        getSharedPreferences("sharafzai_keyboard", MODE_PRIVATE)
            .edit()
            .putInt("language", language)
            .putInt("theme", themeIndex)
            .apply()
    }

    private fun refreshKeyboard() {
        val newView = buildKeyboard()
        setInputView(newView)
        updateSuggestions()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopDeleteRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopDeleteRepeat()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
'''
# Correct a deliberately guarded typo before writing the final source.
code = code.replace('EditorInfo.IME_ACTION,上', 'EditorInfo.IME_ACTION_GO')
path = Path("/mnt/data/KeyboardService.kt")
path.write_text(code, encoding="utf-8")
print(f"Created {path} ({len(code.splitlines())} lines).")

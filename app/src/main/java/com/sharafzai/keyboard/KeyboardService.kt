package com.sharafzai.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class KeyboardService : InputMethodService() {

    private var language = 0
    private var shifted = false

    private val languages = listOf(
        listOf(
            listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"),
            listOf("ش","س","ی","ب","ل","ا","ت","ن","م","ک","ګ"),
            listOf("ټ","څ","ځ","ډ","ړ","ږ","ښ","ڼ","پ","و","ۍ")
        ),
        listOf(
            listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"),
            listOf("ش","س","ي","ب","ل","ا","ت","ن","م","ك","ة"),
            listOf("ئ","ء","ؤ","ر","ذ","د","و","ى","ة")
        ),
        listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l"),
            listOf("z","x","c","v","b","n","m")
        )
    )

    override fun onCreateInputView(): View {
        return buildKeyboard()
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(3, 5, 3, 5)
        root.setBackgroundColor(Color.rgb(35, 39, 48))

        val title = Button(this)
        title.text = when (language) {
            0 -> "Sharafzai • پښتو"
            1 -> "Sharafzai • العربية"
            else -> "Sharafzai • English"
        }
        title.setTextColor(Color.WHITE)
        title.setBackgroundColor(Color.rgb(48, 55, 70))
        root.addView(title, rowParams(42))

        for (row in languages[language]) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER

            for (key in row) {
                addKey(line, key)
            }

            root.addView(line, rowParams(49))
        }

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL

        addSpecial(controls, "🌐", 1f) {
            language = (language + 1) % 3
            setInputView(buildKeyboard())
        }

        addSpecial(controls, "⇧", 1f) {
            shifted = !shifted
            setInputView(buildKeyboard())
        }

        addSpecial(controls, "?123", 1.3f) {
            currentInputConnection?.commitText("؟", 1)
        }

        addSpecial(controls, "⌫", 1.2f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        root.addView(controls, rowParams(48))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL

        addSpecial(bottom, "😊", 1f) {
            currentInputConnection?.commitText("😊", 1)
        }

        addSpecial(bottom, ",", 0.7f) {
            currentInputConnection?.commitText(",", 1)
        }

        addSpecial(bottom, "English/پښتو", 4f) {
            currentInputConnection?.commitText(" ", 1)
        }

        addSpecial(bottom, ".", 0.7f) {
            currentInputConnection?.commitText(".", 1)
        }

        addSpecial(bottom, "↵", 1f) {
            currentInputConnection?.commitText("\n", 1)
        }

        root.addView(bottom, rowParams(49))

        return root
    }

    private fun addKey(parent: LinearLayout, key: String) {
        val button = Button(this)
        button.text = if (language == 2 && shifted) key.uppercase() else key
        button.textSize = 19f
        button.typeface = Typeface.DEFAULT
        button.setTextColor(Color.WHITE)
        button.isAllCaps = false
        button.setPadding(0, 0, 0, 0)
        button.setBackgroundColor(Color.rgb(65, 73, 89))

        button.setOnClickListener {
            val value = if (language == 2 && shifted) {
                key.uppercase()
            } else key

            currentInputConnection?.commitText(value, 1)

            if (language == 2 && shifted) {
                shifted = false
                setInputView(buildKeyboard())
            }
        }

        val params = LinearLayout.LayoutParams(0, dp(46), 1f)
        params.setMargins(2, 2, 2, 2)
        parent.addView(button, params)
    }

    private fun addSpecial(
        parent: LinearLayout,
        label: String,
        weight: Float,
        action: () -> Unit
    ) {
        val button = Button(this)
        button.text = label
        button.textSize = 15f
        button.isAllCaps = false
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.rgb(43, 111, 150))
        button.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(0, dp(45), weight)
        params.setMargins(2, 2, 2, 2)
        parent.addView(button, params)
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

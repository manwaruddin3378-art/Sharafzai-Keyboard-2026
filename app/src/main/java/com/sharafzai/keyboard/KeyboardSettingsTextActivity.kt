package com.sharafzai.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

/** Normal Activity host for settings forms that must accept input from the SUNDUS IME. */
class KeyboardSettingsTextActivity : Activity() {
    companion object {
        const val EXTRA_MODE = "settings_text_mode"
        const val EXTRA_LANGUAGE = "settings_text_language"
        const val EXTRA_TERM = "settings_text_term"
        const val EXTRA_MEANING = "settings_text_meaning"
        const val MODE_EMAIL = "email"
        const val MODE_DICTIONARY_MANAGER = "dictionary_manager"
        const val MODE_DICTIONARY_EDITOR = "dictionary_editor"
    }

    private val prefs by lazy { getSharedPreferences("SharafzaiSettings", MODE_PRIVATE) }
    private val language by lazy { intent.getIntExtra(EXTRA_LANGUAGE, 0).coerceIn(0, 3) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_EMAIL -> emailEditor()
            MODE_DICTIONARY_EDITOR -> dictionaryEditor(
                intent.getStringExtra(EXTRA_TERM).orEmpty(),
                intent.getStringExtra(EXTRA_MEANING).orEmpty()
            )
            else -> dictionaryManager()
        }
    }

    private fun root(title: String): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.rgb(245, 248, 250))
        }
        layout.addView(TextView(this).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 48, 77))
            setPadding(0, dp(8), 0, dp(12))
        })
        setContentView(layout)
        return layout
    }

    private fun editField(hintText: String, initial: String = "", multiline: Boolean = false, email: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            setText(initial)
            textSize = 17f
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            if (email) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                setSingleLine(true)
            } else if (multiline) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                setSingleLine(false)
            } else {
                setSingleLine(true)
            }
        }

    private fun action(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun showKeyboard(field: EditText) {
        field.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun emailEditor() {
        val page = root("ستاسو ایمیل")
        page.addView(TextView(this).apply {
            text = "ایمیل یوازې په همدې موبایل کې ساتل کېږي او د ایمیل په خانه کې به په یوه ټک ښکاره شي."
            textSize = 14f
            setTextColor(Color.DKGRAY)
        })
        val email = editField(
            "name@example.com",
            prefs.getString("preferredEmail", "").orEmpty(),
            email = true
        )
        page.addView(email, LinearLayout.LayoutParams(-1, dp(58)))
        page.addView(action("ساتل") {
            val value = email.text.toString().trim()
            if (value.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                Toast.makeText(this, "د ایمیل بڼه سمه نه ده", Toast.LENGTH_LONG).show()
            } else if (prefs.edit().putString("preferredEmail", value).commit()) {
                Toast.makeText(this, "ایمیل خوندي شو", Toast.LENGTH_SHORT).show()
                finish()
            } else Toast.makeText(this, "ایمیل خوندي نه شو؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(-1, dp(50)))
        page.addView(action("بندول") { finish() }, LinearLayout.LayoutParams(-1, dp(46)))
        showKeyboard(email)
    }

    private fun personalWords(): MutableSet<String> =
        prefs.getStringSet("personalWords_$language", emptySet()).orEmpty().toMutableSet()

    private fun personalEntries(): MutableSet<String> =
        prefs.getStringSet("dictionaryEntries_$language", emptySet()).orEmpty().toMutableSet()

    private fun dictionaryManager(query: String = "") {
        val page = root("شخصي قاموس")
        page.addView(TextView(this).apply {
            text = "خپلې کلمې او ماناوې دلته ثبت، ولټوئ او اصلاح کړئ. معلومات یوازې په همدې موبایل کې ساتل کېږي."
            textSize = 14f
            setTextColor(Color.DKGRAY)
        })
        val search = editField("د کلمې یا مانا لټون", query)
        page.addView(search, LinearLayout.LayoutParams(-1, dp(54)))
        page.addView(action("＋ نوې کلمه او مانا") { dictionaryEditor("", "") }, LinearLayout.LayoutParams(-1, dp(48)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { isFillViewport = true; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(action("بندول") { finish() }, LinearLayout.LayoutParams(-1, dp(46)))

        fun refreshList(text: String) {
            list.removeAllViews()
            val hidden = prefs.getStringSet("dictionaryHidden_$language", emptySet()).orEmpty()
            val builtIn = try {
                assets.open("dictionary_seed.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.mapNotNull { row ->
                        val parts = row.split("|", limit = 5)
                        if (parts.size == 5 && parts[language].isNotBlank()) parts[language].trim() to parts[4].trim() else null
                    }.filterNot { it.first in hidden }.toList()
                }
            } catch (_: Exception) { emptyList() }
            val personal = personalEntries().mapNotNull { item ->
                val p = item.split("|||", limit = 2)
                if (p.size == 2 && p[0].isNotBlank()) p[0] to p[1] else null
            }
            val words = personalWords().map { it to (personal.firstOrNull { entry -> entry.first == it }?.second.orEmpty()) }
            val entries = (builtIn + personal + words).associateBy { it.first }.values
                .filter { text.isBlank() || it.first.contains(text, true) || it.second.contains(text, true) }
                .sortedBy { it.first.lowercase() }
            if (entries.isEmpty()) {
                list.addView(TextView(this).apply {
                    this.text = "د لټون لپاره کومه کلمه ونه موندل شوه."
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(24), 0, dp(24))
                })
            }
            entries.forEach { (term, meaning) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setBackgroundColor(Color.WHITE)
                }
                row.addView(TextView(this).apply {
                    this.text = if (meaning.isBlank()) term else "$term — $meaning"
                    textSize = 16f
                    setTextColor(Color.BLACK)
                })
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                controls.addView(action("سمون") { dictionaryEditor(term, meaning) }, LinearLayout.LayoutParams(0, dp(42), 1f))
                controls.addView(action("حذف") {
                    AlertDialog.Builder(this@KeyboardSettingsTextActivity)
                        .setTitle("کلمه حذف کړئ؟")
                        .setMessage("«$term» له قاموس څخه حذف شي؟")
                        .setNegativeButton("نه", null)
                        .setPositiveButton("هو، حذف") { _, _ ->
                            val wordsNow = personalWords().apply { remove(term) }
                            val entriesNow = personalEntries().apply { removeAll { it.substringBefore("|||") == term } }
                            val hiddenNow = prefs.getStringSet("dictionaryHidden_$language", emptySet()).orEmpty().toMutableSet().apply { add(term) }
                            prefs.edit()
                                .putStringSet("personalWords_$language", HashSet(wordsNow))
                                .putStringSet("dictionaryEntries_$language", HashSet(entriesNow))
                                .putStringSet("dictionaryHidden_$language", HashSet(hiddenNow))
                                .commit()
                            dictionaryManager(search.text.toString())
                        }.show()
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
                row.addView(controls)
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }
        refreshList(query)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshList(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        showKeyboard(search)
    }

    private fun dictionaryEditor(existingTerm: String, existingMeaning: String) {
        val page = root(if (existingTerm.isBlank()) "قاموس ته نوې کلمه اضافه کړئ" else "د قاموس کلمه اصلاح کړئ")
        page.addView(TextView(this).apply {
            text = "کلمه او مانا یې ولیکئ؛ معلومات په همدې موبایل کې ساتل کېږي."
            textSize = 14f
            setTextColor(Color.DKGRAY)
        })
        val term = editField("کلمه یا اصطلاح", existingTerm)
        val meaning = editField("د کلمې مانا او تشریح", existingMeaning, multiline = true)
        page.addView(term, LinearLayout.LayoutParams(-1, dp(56)))
        page.addView(meaning, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(action("ذخیره") {
            val word = term.text.toString().trim()
            val definition = meaning.text.toString().trim()
            if (word.isBlank() || definition.isBlank()) {
                Toast.makeText(this, "کلمه او مانا دواړه ولیکئ", Toast.LENGTH_SHORT).show()
                return@action
            }
            val words = personalWords().apply { if (existingTerm.isNotBlank()) remove(existingTerm); add(word) }
            val entries = personalEntries().apply {
                if (existingTerm.isNotBlank()) removeAll { it.substringBefore("|||") == existingTerm }
                removeAll { it.substringBefore("|||") == word }
                add("$word|||$definition")
            }
            val hidden = prefs.getStringSet("dictionaryHidden_$language", emptySet()).orEmpty().toMutableSet().apply {
                if (existingTerm.isNotBlank() && existingTerm != word) add(existingTerm)
                remove(word)
            }
            val saved = prefs.edit()
                .putStringSet("personalWords_$language", HashSet(words))
                .putStringSet("dictionaryEntries_$language", HashSet(entries))
                .putStringSet("dictionaryHidden_$language", HashSet(hidden))
                .commit()
            if (saved) {
                Toast.makeText(this, "کلمه او مانا خوندي شول", Toast.LENGTH_SHORT).show()
                if (existingTerm.isBlank() && intent.getStringExtra(EXTRA_MODE) == MODE_DICTIONARY_MANAGER) dictionaryManager()
                else finish()
            } else Toast.makeText(this, "ذخیره ناکامه شوه؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(-1, dp(50)))
        page.addView(action("بېرته") { dictionaryManager() }, LinearLayout.LayoutParams(-1, dp(46)))
        showKeyboard(term)
    }
}

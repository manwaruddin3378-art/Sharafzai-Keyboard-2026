
package com.sharafzai.keyboard

import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import android.app.AlertDialog
import android.widget.Toast
import android.widget.PopupWindow
import android.os.Handler
import android.os.Looper

class KeyboardService : InputMethodService() {

    private var language = 0
    private var shifted = false
    private var caps = false
    private var lastShiftTapAt = 0L
    private var themeIndex = 0
    private var keySizeLevel = 1
    private var suggestionContainer: LinearLayout? = null
    private var emojiPanelVisible = false
    private var diacriticsPanelVisible = false
    private var symbolsPanelVisible = false
    private var settingsPanelVisible = false
    private var phrasePanelVisible = false
    private var phraseCategory = 0
    private var phraseIndex = 0
    private var clipboardPanelVisible = false
    private var emojiCategory = 0
    private var fontStyleIndex = 0
    private var numberRowEnabled = true
    private var nativeDigitsEnabled = true
    private var autoCorrectEnabled = true
    private var hapticEnabled = true
    private var soundEnabled = false
    private var pendingVoiceText = ""
    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            pendingVoiceText = intent?.getStringExtra(VoiceInputActivity.EXTRA_RECOGNIZED_TEXT).orEmpty()
        }
    }
    private data class GlideKey(val view: View, val value: String)
    private val glideKeys = mutableListOf<GlideKey>()

    private data class KeyboardTheme(
        val background: Int,
        val keyColors: List<Int>,
        val special: Int,
        val suggestion: Int,
        val accent: Int,
        val border: Int
    )

    // Forty-eight lightweight, fully offline color themes with high-contrast text.
    private val themes = (listOf(
        // SUNDUS signature theme: deep navy, turquoise glow, pearl keys and gold-ready accents.
        KeyboardTheme(Color.rgb(3, 19, 46),
            listOf(Color.rgb(242, 246, 255), Color.rgb(225, 236, 252), Color.rgb(250, 249, 255)),
            Color.rgb(7, 45, 103), Color.rgb(4, 31, 70),
            Color.rgb(34, 218, 235), Color.rgb(38, 190, 229)),
        KeyboardTheme(Color.rgb(20, 25, 35),
            listOf(Color.rgb(48, 57, 72), Color.rgb(41, 50, 65), Color.rgb(34, 42, 56)),
            Color.rgb(57, 68, 86), Color.rgb(29, 36, 49),
            Color.rgb(90, 165, 240), Color.rgb(73, 86, 108)),
        KeyboardTheme(Color.rgb(224, 240, 248),
            listOf(Color.rgb(255, 255, 255), Color.rgb(246, 251, 255), Color.rgb(229, 242, 250)),
            Color.rgb(194, 221, 239), Color.rgb(244, 250, 254),
            Color.rgb(20, 116, 157), Color.rgb(181, 211, 228)),
        KeyboardTheme(Color.rgb(231, 245, 238),
            listOf(Color.rgb(255, 255, 255), Color.rgb(247, 253, 249), Color.rgb(231, 243, 236)),
            Color.rgb(208, 232, 218), Color.rgb(248, 253, 249),
            Color.rgb(39, 128, 95), Color.rgb(188, 218, 199)),
        KeyboardTheme(Color.rgb(242, 238, 250),
            listOf(Color.rgb(255, 255, 255), Color.rgb(250, 247, 255), Color.rgb(238, 232, 247)),
            Color.rgb(225, 217, 241), Color.rgb(251, 249, 255),
            Color.rgb(115, 84, 160), Color.rgb(211, 199, 230)),
        KeyboardTheme(Color.rgb(250, 239, 242),
            listOf(Color.rgb(255, 255, 255), Color.rgb(255, 249, 251), Color.rgb(246, 230, 236)),
            Color.rgb(241, 220, 228), Color.rgb(255, 249, 251),
            Color.rgb(173, 75, 105), Color.rgb(230, 199, 211))
    ) + List(42) { index ->
        val hue = (index * 29f + 18f) % 360f
        val colors = FloatArray(3)
        android.graphics.Color.colorToHSV(Color.HSVToColor(floatArrayOf(hue, 0.22f, 0.96f)), colors)
        val base = Color.HSVToColor(floatArrayOf(hue, 0.22f, 0.96f))
        val key = Color.HSVToColor(floatArrayOf(hue, 0.12f, 1.0f))
        val special = Color.HSVToColor(floatArrayOf(hue, 0.25f, 0.88f))
        val suggestion = Color.HSVToColor(floatArrayOf(hue, 0.10f, 0.99f))
        val accent = Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.58f))
        val border = Color.HSVToColor(floatArrayOf(hue, 0.18f, 0.82f))
        KeyboardTheme(base, listOf(key, Color.WHITE, special), special, suggestion, accent, border)
    })

    private val theme: KeyboardTheme
        get() = themes[themeIndex]

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(
            "SharafzaiSettings",
            MODE_PRIVATE
        )

        // Remove legacy clipboard history from older builds; ordinary copied text is never archived.
        prefs.edit().apply {
            (0 until 8).forEach { remove("clipboardHistory_$it") }
        }.apply()

        language = prefs.getInt("language", 0).coerceIn(0, 3)
        themeIndex = prefs.getInt("themeIndex", 0)
            .coerceIn(0, themes.size - 1)
        keySizeLevel = prefs.getInt("keySizeLevel", 2).coerceIn(0, 4)
        fontStyleIndex = prefs.getInt("fontStyleIndex", 0).coerceIn(0, 2)
        numberRowEnabled = prefs.getBoolean("numberRowEnabled", false)
        nativeDigitsEnabled = prefs.getBoolean("nativeDigitsEnabled", true)
        autoCorrectEnabled = false
        hapticEnabled = prefs.getBoolean("hapticEnabled", true)
        soundEnabled = prefs.getBoolean("soundEnabled", false)
        val voiceFilter = IntentFilter(VoiceInputActivity.ACTION_VOICE_TEXT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(voiceReceiver, voiceFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(voiceReceiver, voiceFilter)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(voiceReceiver) }
        super.onDestroy()
    }

    private data class KeySpec(
        val normal: String,
        val shifted: String = "",
        val longPress: String = "",
        val display: String = ""
    )

    private fun k(
        a: String,
        b: String = "",
        c: String = "",
        display: String = ""
    ) = KeySpec(a, b, c, display)

    // Small bundled starter vocabulary. Personal words are stored locally per language.
    private val offlineWords = mapOf(
        0 to listOf(
            "الله","محمد","اسلام","قرآن","کتاب","ښوونځی","پوهنتون","پښتو","ژبه","علم","استاد","ملګری",
            "ورور","خور","کور","کار","ژوند","ښه","مننه","سلام","ورځ","شپه","زه","ته","تاسو","هغه",
            "هغوی","موږ","زما","زموږ","ستاسو","څنګه","ولې","کله","چېرته","څه","څوک","ډېر","ډېره",
            "کوم","کومه","دا","دغه","هلته","دلته","نن","سبا","پرون","سهار","ماښام","اونۍ","میاشت",
            "کال","وخت","موبایل","کورنۍ","ماشوم","ښوونکی","زده","مدرسه","پوهه","لیک","پیغام","خبر",
            "پیسې","بازار","روغ","بښنه","مینه","خوښ","خوشحاله","بریا","بریالی","ستړی","مهرباني"
        ),
        1 to listOf(
            "الله","محمد","الإسلام","القرآن","كتاب","مدرسة","جامعة","لغة","العربية","علم","أستاذ","طالب",
            "صديق","بيت","عمل","حياة","شكرا","مرحبا","السلام","أنا","أنت","أنتم","نحن","هو","هي","هم",
            "هذا","هذه","ذلك","كيف","ماذا","متى","أين","لماذا","نعم","لا","من","فضلك","عفوا","صباح",
            "مساء","يوم","أسبوع","شهر","سنة","وقت","رسالة","هاتف","عائلة","مسجد","قلم","جميل","جيد",
            "ممتاز","صحيح","ممكن","إن","شاء","ما","الحمد","لله","بارك","جزاك","خيرا","مع","السلام"
        ),
        2 to listOf(
            "the","this","that","there","their","they","these","those","hello","help","have","here","how",
            "good","great","go","get","keyboard","language","computer","school","student","thank","thanks",
            "you","your","yours","we","our","they","them","he","she","his","her","what","when","where",
            "why","who","which","yes","no","please","sorry","welcome","morning","evening","night","day",
            "week","month","year","time","today","tomorrow","yesterday","message","phone","friend",
            "family","book","write","read","work","home","love","happy","beautiful","wonderful","Allah",
            "Islam","Quran","peace","kind","kindness","correct","right","wrong","world","people","name"
        ),
        3 to listOf(
            "الله","محمد","اسلام","قرآن","کتاب","مکتب","پوهنتون","دري","زبان","علم","استاد","شاگرد","دوست",
            "برادر","خواهر","خانه","کار","زندگی","خوب","تشکر","سلام","روز","شب","من","تو","شما","او","ما",
            "آنها","این","آن","چی","چه","کجا","چرا","چگونه","بلی","نخیر","لطفا","معذرت","خدا","صبح","شام",
            "هفته","ماه","سال","وقت","پیام","موبایل","خانواده","دانشگاه","مسجد","قلم","زیبا","موفق",
            "امروز","فردا","دیروز","دوست","مهربانی","محبت","سلامت","درست","اشتباه","آسان","سخت","بسیار",
            "خوش","خوشحال","انشاءالله","الحمدلله"
        ),
    )

    private fun offlineWordsFor(languageId: Int): List<String> =
        (offlineWords[languageId].orEmpty() + extendedOfflineWords[languageId].orEmpty()).distinct()

    // Microsoft Windows Pashto (Afghanistan), KLID 00000463.
    // Rows follow the physical QWERTY positions; shifted characters match Windows.
    private val pashtoRows = listOf(
        listOf(
            k("ض", "ْ"), k("ص", "ٌ"), k("ث", "ٍ"),
            k("ق", "ً"), k("ف", "ُ"), k("غ", "ِ"),
            k("ع", "َ"), k("ه", "ّ"), k("خ", "ځ"),
            k("ح", "څ"), k("ج", "]"), k("چ", "["),
            k("\\", "*")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ۍ"), k("ی", "ي"),
            k("ب", "پ"), k("ل"), k("ا", "آ"),
            k("ت", "ټ"), k("ن", "ڼ"), k("م", "ة"),
            k("ک", "أ"), k("ګ", "إ")
        ),
        listOf(
            k("ظ", "ئ"), k("ط", "ې"), k("ز", "ژ"),
            k("ر", "ء"), k("ذ", "‌"), k("د", "ډ"),
            k("ړ"), k("و", "ږ"), k("/", "؟")
        )
    )

    // Arabic 101-style QWERTY layout; diacritics remain available on Shift and in the tashkeel panel.
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
            k("ك", "آ"), k("ط", "ئ")
        ),
        listOf(
            k("ئ", "ً"), k("ء", "ٍ"), k("ؤ", "ُ"),
            k("ر", "ِ"), k("لا", "َ"), k("ى", "ّ"),
            k("ة", "ْ"), k("و", "ٌ"), k("ز", "َ"),
            k("ظ", "ّ")
        )
    )

    // Dari/Persian QWERTY layout with Afghanistan-specific letters.
    private val dariRows = listOf(
        listOf(
            k("ض", "ْ"), k("ص", "ٌ"), k("ث", "ٍ"),
            k("ق", "ً"), k("ف", "ُ"), k("غ", "ِ"),
            k("ع", "َ"), k("ه", "ّ"), k("خ", "أ"),
            k("ح", "آ"), k("ج", "ئ"), k("چ", "ء")
        ),
        listOf(
            k("ش", "ښ"), k("س", "ث"), k("ی", "ي"),
            k("ب", "پ"), k("ل"), k("ا", "آ"),
            k("ت", "ټ"), k("ن", "ڼ"), k("م", "ة"),
            k("ک", "ك"), k("گ", "ګ")
        ),
        listOf(
            k("ظ"), k("ط"), k("ز"), k("ر"),
            k("ذ"), k("د"), k("پ"), k("و"), k("ژ")
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
        k("\u200D", "ٔ", "€", "ـ"),
        k("۱", "!", "ٱ"), k("۲", "٬", "ى"),
        k("۳", "٫", "\u200E"), k("۴", "؋", "\u200F"),
        k("۵", "٪", "ٓ"), k("۶", "×", "ٙ"),
        k("۷", "»", "ٰ"), k("۸", "«", "'"),
        k("۹", ")", "\""), k("۰", "(", "}"),
        k("-", "ـ", "{"), k("=", "+", "|")
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
        if (pendingVoiceText.isNotBlank()) {
            val connection = currentInputConnection
            if (connection != null) {
                connection.commitText(pendingVoiceText, 1)
                pendingVoiceText = ""
            }
        }
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

    private val adhkar = listOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللَّهُ", "أَسْتَغْفِرُ اللَّهَ", "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ")
    private var dhikrIndex = 0
    private val dhikrTicker = object : Runnable {
        override fun run() {
            dhikrIndex = (dhikrIndex + 1) % adhkar.size
            dhikrView?.text = adhkar[dhikrIndex]
            handler.postDelayed(this, 3500)
        }
    }
    private var dhikrView: TextView? = null

    private fun buildKeyboard(): View {
        glideKeys.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
            background = FloralPatternDrawable(theme.background)
        }

        val dhikrStrip = TextView(this).apply {\n            text = adhkar[dhikrIndex]\n            textSize = 15f\n            gravity = Gravity.CENTER\n            setTextColor(Color.WHITE)\n            typeface = Typeface.DEFAULT_BOLD\n            setPadding(0, dp(3), 0, dp(3))\n            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(7, 75, 82), Color.rgb(20, 48, 92))).apply { cornerRadius = dp(9).toFloat() }\n        }\n        dhikrView = dhikrStrip\n        root.addView(dhikrStrip, rowParams(27))\n        handler.removeCallbacks(dhikrTicker)\n        handler.postDelayed(dhikrTicker, 3500)\n\n        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(3, 74, 91), Color.rgb(8, 35, 75))
            ).apply { cornerRadius = dp(11).toFloat() }
        }

        val brandLogo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "SUNDUS سندس"
        }
        header.addView(brandLogo, LinearLayout.LayoutParams(dp(76), dp(38)))

        val title = TextView(this).apply {
            text = when (language) {
                0 -> "🌙 SUNDUS  |  پښتو"
                1 -> "🌙 SUNDUS  |  العربية"
                2 -> "🌙 SUNDUS  |  ENGLISH"
                3 -> "🌙 SUNDUS  |  دری"
                else -> "🌙 SUNDUS  |  دری"
            }
            textSize = 14f
            letterSpacing = 0.03f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(29), 1f)
        )

        addSpecial(header, "🌐", 0.75f, 29) {
            val languageNames = arrayOf("پښتو", "دري", "عربي", "English")
            val ids = intArrayOf(0, 3, 1, 2)
            val selected = ids.indexOf(language).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("د کیبورډ ژبه وټاکئ")
                .setSingleChoiceItems(languageNames, selected) { dialog, which ->
                    language = ids[which]
                    shifted = false
                    caps = false
                    lastShiftTapAt = 0L
                    saveSettings()
                    dialog.dismiss()
                    refreshKeyboard()
                }
                .setNegativeButton("بند", null)
                .show()
        }
        addSpecial(header, "😊", 0.75f, 29) {
            emojiPanelVisible = !emojiPanelVisible
            phrasePanelVisible = false
            settingsPanelVisible = false
            clipboardPanelVisible = false
            refreshKeyboard()
        }
        addSpecial(header, "☪", 0.75f, 29) {
            phrasePanelVisible = true
            phraseCategory = 0
            phraseIndex = 0
            emojiPanelVisible = false
            settingsPanelVisible = false
            clipboardPanelVisible = false
            refreshKeyboard()
        }
        addSpecial(header, "🎨", 0.75f, 29) {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(header, "⚙", 0.75f, 29) {
            settingsPanelVisible = !settingsPanelVisible
            if (settingsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                clipboardPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }
        root.addView(header, rowParams(44))

        val languageStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(0 to "پښتو", 3 to "دري", 1 to "عربي", 2 to "English").forEach { (id, label) ->
            val tab = Button(this).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = roundedBackground(
                    if (language == id) Color.rgb(0, 178, 174) else Color.rgb(5, 37, 70),
                    dp(12)
                )
                setOnClickListener {
                    language = id
                    shifted = false
                    caps = false
                    saveSettings()
                    refreshKeyboard()
                }
            }
            languageStrip.addView(tab, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        root.addView(languageStrip, rowParams(36))

        val topTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addSpecial(topTools, "📋\nکاپي", 1f, 38) {
            clipboardPanelVisible = !clipboardPanelVisible
            if (clipboardPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                settingsPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }
        addSpecial(topTools, "⭐\nAutoText", 1f, 38) { showAutoTextEditor() }
        addSpecial(topTools, "🌐\nژباړه", 1f, 38) {
            AlertDialog.Builder(this)
                .setTitle("ژباړه")
                .setMessage("سندس اوس مهال په بشپړ ډول آفلاین دی؛ د جملو ژباړلو لپاره د ژباړې آفلاین ژبنۍ بسته لا نه ده ورزیاته شوې. د ژبو د بدلولو لپاره پورته ژبنیز انتخاب وکاروئ.")
                .setPositiveButton("سمه ده", null)
                .show()
        }
        addSpecial(topTools, "📖\nقاموس", 1f, 38) { showPersonalDictionaryEditor() }
        addSpecial(topTools, "🎙\nغږ", 1f, 38) {
            startActivity(Intent(this, VoiceInputActivity::class.java)
                .putExtra("language", language)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addSpecial(topTools, "🎨\nرنګ", 1f, 38) {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }


        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(theme.suggestion)
        }

        root.addView(suggestionContainer, rowParams(32))

        if (!emojiPanelVisible && !clipboardPanelVisible && !symbolsPanelVisible && !phrasePanelVisible && !settingsPanelVisible) {
            val nativeNumberRow = when (language) {
                0 -> pashtoNumbers
                1 -> arabicNumbers
                2 -> englishNumbers
                3 -> dariNumbers
                else -> dariNumbers
            }
            val westernDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            val numberRow = if (nativeDigitsEnabled || language == 2) nativeNumberRow else {
                nativeNumberRow.mapIndexed { index, spec ->
                    if (index < 10) spec.copy(normal = westernDigits[index]) else spec
                }
            }

            if (numberRowEnabled) addKeyRow(root, numberRow, 37)

            val rows = when (language) {
                0 -> pashtoRows
                1 -> arabicRows
                2 -> englishRows
                3 -> dariRows
                else -> dariRows
            }

            rows.forEach { row ->
                addKeyRow(root, row, 42)
            }
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addSpecial(controlRow, "😊", 0.9f, 39) {
            emojiPanelVisible = !emojiPanelVisible
            if (emojiPanelVisible) {
                clipboardPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                
                
                settingsPanelVisible = false
            }
            refreshKeyboard()
        }

        if (true) {
            addSpecial(controlRow, "◌َ", 0.9f, 39) {
                diacriticsPanelVisible = !diacriticsPanelVisible
                if (diacriticsPanelVisible) {
                    emojiPanelVisible = false
                    symbolsPanelVisible = false
                    
                    
                    settingsPanelVisible = false
                    clipboardPanelVisible = false
                }
                refreshKeyboard()
            }
        }

        addSpecial(controlRow, "#+=", 0.9f, 39) {
            symbolsPanelVisible = !symbolsPanelVisible
            if (symbolsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                
                
                settingsPanelVisible = false
                clipboardPanelVisible = false
            }
            refreshKeyboard()
        }

        addSpecial(controlRow, if (caps) "⇪" else "⇧", 1f, 39) {
            val now = System.currentTimeMillis()
            if (language == 2 && shifted && !caps && now - lastShiftTapAt <= 450L) {
                // Double-tap Shift enables English Caps Lock.
                caps = true
                shifted = false
                lastShiftTapAt = 0L
            } else if (caps) {
                // A further tap releases Caps Lock.
                caps = false
                shifted = false
                lastShiftTapAt = 0L
            } else {
                shifted = !shifted
                lastShiftTapAt = if (shifted) now else 0L
            }
            refreshKeyboard()
        }

        addSpecial(controlRow, "⌫", 1.2f, 39) {
            deletePreviousCharacter()
            updateSuggestions()
        }

        root.addView(controlRow, rowParams(41))

        if (diacriticsPanelVisible) {
            val diacriticKeys = listOf(
                k("َ"), k("ً"), k("ُ"), k("ٌ"),
                k("ِ"), k("ٍ"), k("ْ"), k("ّ"),
                k("ٰ"), k("ٓ"), k("ٔ"), k("ٖ")
            )
            addKeyRow(root, diacriticKeys, 34)
        }

        if (symbolsPanelVisible) {
            val symbolKeys = listOf(
                k("@"), k("#"), k("$"), k("%"), k("&"), k("*"), k("+"), k("="),
                k("/"), k("\\"), k("("), k(")"), k("["), k("]"), k("{"), k("}"),
                k("<"), k(">"), k("!"), k("?"), k(":"), k(";"), k("~"), k("_"),
                k("-"), k("—"), k("…"), k("•"), k("©"), k("®"), k("™"), k("٪")
            )
            symbolKeys.chunked(8).forEach { row -> addKeyRow(root, row, 32) }
        }

        if (emojiPanelVisible) {
            addEmojiPanel(root)
        }

        if (clipboardPanelVisible) {
            addClipboardPanel(root)
        }

        if (phrasePanelVisible) {
            addPhrasePanel(root)
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

        if (language == 0 || language == 3) {
            addSpecial(bottom, "نیم‌فاصله", 1.0f, 42) {
                currentInputConnection?.commitText("\u200C", 1)
            }
        }

        addSpecial(bottom, "،", 0.7f, 42) {
            val value = if (language == 2) "," else "،"
            currentInputConnection?.commitText(value, 1)
            updateSuggestions()
        }

        addSpecial(bottom, "✦ SUNDUS | سندس ✦", 3.5f, 42) { expandAutoTextAndSpace() }

        addSpecial(bottom, ".", 0.7f, 42) {
            currentInputConnection?.commitText(".", 1)
            updateSuggestions()
        }

        addSpecial(bottom, "↵", 1f, 42) {
            performEnterAction()
        }

        root.addView(bottom, rowParams(44))
        root.addView(topTools, rowParams(40))

        return root
    }

    private fun addSettingsPanel(parent: LinearLayout) {
        val title = TextView(this).apply {
            text = "د سندس کیبورډ تنظیمات"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (themeIndex == 0 || themeIndex == 1) Color.WHITE else Color.rgb(38, 45, 55))
            typeface = Typeface.DEFAULT_BOLD
        }
        parent.addView(title, rowParams(30))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addSpecial(row, "اندازه: ${listOf("ډېره وړه", "وړه", "منځنۍ", "لویه", "ډېره لویه")[keySizeLevel]}", 1.4f, 40) {
            keySizeLevel = (keySizeLevel + 1) % 5
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(row, "ژبه: ${listOf("پښتو", "عربي", "English", "دري")[language.coerceIn(0, 3)]}", 1.5f, 40) {
            language = (language + 1) % 4
            shifted = false
            caps = false
            lastShiftTapAt = 0L
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(row, "بند", 0.7f, 40) {
            settingsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(row, rowParams(44))

        val fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val fontNames = listOf("معیاري", "نسخي", "مونو")
        addSpecial(fontRow, "فونټ: ${fontNames[fontStyleIndex]}", 1.8f, 38) {
            fontStyleIndex = (fontStyleIndex + 1) % fontNames.size
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(fontRow, "بندول", 0.8f, 38) {
            settingsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(fontRow, rowParams(41))

        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val themeNames = listOf("سندس اصلي", "شپه", "سمندري", "زرغون", "بنفش", "ګلابي", "طلایي", "یاقوتي", "فیروزه", "لاجوردي", "زمردي", "سپوږمۍ", "لمر", "آسماني", "باراني", "ځنګل", "ګلستان", "مرجاني", "ارغواني", "نقره", "برونزي", "شفق", "یخ", "اور", "شګه", "کاکاو", "نعنا", "لیمو", "انګور", "ګلاب", "مرجان", "دود", "شاهي", "مخمل", "کریستال", "سپین زر", "تور الماس", "زمرد شپه", "طلایي شفق", "سندس ځانګړی", "لاجوردي شپه", "شنه زمرد", "ګلابي سپوږمۍ", "شاهي ارغواني", "د صحرا لمر", "یخي آبي", "مروارید", "تور شفق")
        addSpecial(themeRow, "رنګ: ${themeNames[themeIndex]}", 1.8f, 38) {
            themeIndex = (themeIndex + 1) % themes.size
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(themeRow, "بندول", 0.8f, 38) {
            settingsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(themeRow, rowParams(41))

        val typingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(typingRow, if (numberRowEnabled) "شمېرې: ښکاره" else "شمېرې: پټې", 1.2f, 38) {
            numberRowEnabled = !numberRowEnabled
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(typingRow, "اتومات بدلون: بند", 1.25f, 38) {
            Toast.makeText(this, "سندس ستاسو لیکل نه بدلوي؛ یوازې د لغتونو وړاندیز کوي.", Toast.LENGTH_LONG).show()
        }
        addSpecial(typingRow, if (nativeDigitsEnabled) "ملي شمېرې" else "انګلیسي شمېرې", 1.15f, 38) {
            nativeDigitsEnabled = !nativeDigitsEnabled
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(typingRow, if (hapticEnabled) "رپ: فعال" else "رپ: بند", 1.0f, 38) {
            hapticEnabled = !hapticEnabled
            saveSettings()
            refreshKeyboard()
        }
        addSpecial(typingRow, "AutoText تنظیم", 1.2f, 38) { showAutoTextEditor() }
        parent.addView(typingRow, rowParams(41))
        val soundRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(soundRow, if (soundEnabled) "د تڼیو غږ: فعال" else "د تڼیو غږ: بند", 1f, 38) {
            soundEnabled = !soundEnabled
            saveSettings()
            refreshKeyboard()
        }
        parent.addView(soundRow, rowParams(41))

        val dictionaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(dictionaryRow, "شخصي قاموس", 1.2f, 38) {
            showPersonalDictionaryEditor()
        }
        addSpecial(dictionaryRow, "ایمیل تنظیم", 1.0f, 38) { showEmailSetup() }
        addSpecial(dictionaryRow, "کلمه اضافه", 1.0f, 38) {
            addCurrentWordToDictionary()
        }
        parent.addView(dictionaryRow, rowParams(41))
    }

    private fun showPersonalDictionaryEditor() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val dictionaryKey = "personalWords_$language"
        val words = prefs.getStringSet(dictionaryKey, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        val languageName = listOf("پښتو", "عربي", "English", "دري")[language.coerceIn(0, 3)]

        fun showList() {
            val current = prefs.getStringSet(dictionaryKey, emptySet())
                ?.toMutableSet() ?: mutableSetOf()
            val sorted = current.sorted()
            val message = if (sorted.isEmpty()) {
                "تر اوسه په شخصي قاموس کې کلمه نشته."
            } else {
                "د حذف لپاره پر یوې کلمې کلیک وکړئ."
            }
            AlertDialog.Builder(this)
                .setTitle("شخصي قاموس – $languageName")
                .setMessage(message)
                .setItems(sorted.toTypedArray()) { _, which ->
                    val selected = sorted[which]
                    val updated = prefs.getStringSet(dictionaryKey, emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    updated.remove(selected)
                    prefs.edit().putStringSet(dictionaryKey, updated).apply()
                    Toast.makeText(this, "«$selected» له قاموس څخه حذف شوه", Toast.LENGTH_SHORT).show()
                    updateSuggestions()
                    showList()
                }
                .setPositiveButton("نوې کلمه") { _, _ ->
                    val input = EditText(this).apply {
                        hint = "کلمه ولیکئ"
                        setSingleLine(true)
                        setTextColor(Color.BLACK)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("قاموس ته کلمه اضافه کړئ")
                        .setView(input)
                        .setNegativeButton("بېرته", null)
                        .setPositiveButton("ذخیره") { _, _ ->
                            val word = input.text.toString().trim()
                            if (word.length >= 2) {
                                val updated = prefs.getStringSet(dictionaryKey, emptySet())
                                    ?.toMutableSet() ?: mutableSetOf()
                                updated.add(word)
                                prefs.edit().putStringSet(dictionaryKey, updated).apply()
                                Toast.makeText(this, "کلمه شخصي قاموس ته اضافه شوه", Toast.LENGTH_SHORT).show()
                                updateSuggestions()
                            } else {
                                Toast.makeText(this, "لږ تر لږه دوه توري ولیکئ", Toast.LENGTH_SHORT).show()
                            }
                            showList()
                        }
                        .show()
                }
                .setNegativeButton("بند", null)
                .show()
        }
        showList()
    }

    private fun showAutoTextEditor() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val key = "autoText_$language"
        val rows = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull {
            val parts = it.split("|||", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.sortedBy { it.first }
        val display = if (rows.isEmpty()) "تر اوسه شخصي لنډیز نشته." else rows.joinToString("\n") { "${it.first} → ${it.second}" }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        content.addView(TextView(this).apply { text = display; textSize = 14f })
        val shortcut = EditText(this).apply { hint = "لنډ کوډ، لکه سلام" }
        val expansion = EditText(this).apply { hint = "بشپړه جمله یا متن" }
        content.addView(shortcut); content.addView(expansion)
        AlertDialog.Builder(this)
            .setTitle("شخصي AutoText لنډیزونه")
            .setMessage("لنډ کوډ ولیکئ؛ د Space په وهلو سره به په بشپړ متن بدل شي. دا معلومات یوازې په موبایل کې ساتل کېږي.")
            .setView(content)
            .setPositiveButton("ساتل") { _, _ ->
                val code = shortcut.text.toString().trim()
                val value = expansion.text.toString().trim()
                if (code.isNotEmpty() && value.isNotEmpty()) {
                    val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                    updated.removeAll { it.substringBefore("|||") == code }
                    updated.add("$code|||$value")
                    prefs.edit().putStringSet(key, updated).apply()
                    Toast.makeText(this, "لنډیز خوندي شو", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("پاکول") { _, _ ->
                val code = shortcut.text.toString().trim()
                if (code.isNotEmpty()) {
                    val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                    updated.removeAll { it.substringBefore("|||") == code }
                    prefs.edit().putStringSet(key, updated).apply()
                }
            }
            .setNegativeButton("بند", null)
            .show()
    }

    private fun expandAutoTextAndSpace() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val word = getCurrentWord(before)
        val entries = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getStringSet("autoText_$language", emptySet()).orEmpty()
        val expansion = entries.mapNotNull { entry ->
            val parts = entry.split("|||", limit = 2)
            if (parts.size == 2 && parts[0].equals(word, ignoreCase = true)) parts[1] else null
        }.firstOrNull()
        if (expansion != null && word.isNotEmpty()) {
            connection.deleteSurroundingText(word.length, 0)
            connection.commitText("$expansion ", 1)
        } else connection.commitText(" ", 1)
        updateSuggestions()
    }

    private fun addEmojiPanel(parent: LinearLayout) {
        val groups = listOf(
            "مخونه" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😍","🥰","😘","😗","😙","😚","😋","😛","😜","🤪","😎","🤓","🧐","🤔","🤗","🤭","🤫","😴","😭"),
            "اسلامي" to listOf("🤲","🙏","🕌","🕋","📿","☪️","🌙","⭐","✨","❤️","💚","🤍","🕊️","🌹","🌸","📖","📚","🧎","🧕","🧔","☝️","🤝","💐","🌿","🪷","🫶"),
            "زړه" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🩹","💕","💞","💓","💗","💖","💘","💝","💟","❣️","💌","💋","💐","🌹"),
            "لاسونه" to listOf("👍","👎","👏","🙌","👐","🤝","🙏","🤲","✍️","💪","👋","🤚","✋","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","☝️","👇"),
            "طبیعت" to listOf("🌞","🌙","⭐","🌟","⚡","🔥","🌈","☁️","🌧️","❄️","🌊","🌳","🌴","🌵","🌷","🌸","🌺","🌻","🍀","🍃","🌿","🦋","🐦","🕊️"),
            "نښې" to listOf("✅","❌","❗","❓","⚠️","💯","♻️","🔔","🔕","📌","📍","🎉","🎊","🎁","🏆","⭐","🔒","🔑","📱","💬","📩","➡️","⬅️","🔝")
        )
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        groups.forEachIndexed { index, _ ->
            addSpecial(tabs, listOf("😊","🤲","❤️","👍","🌿","🔣")[index], 1f, 34) {
                emojiCategory = index
                refreshKeyboard()
            }
        }
        parent.addView(tabs, rowParams(37))
        groups[emojiCategory.coerceIn(0, groups.lastIndex)].second.take(24).chunked(8).forEach { row ->
            addKeyRow(parent, row.map { k(it) }, 31)
        }
        val close = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(close, "د ایموجي بندول", 1f, 32) {
            emojiPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(close, rowParams(35))
    }

    private fun addClipboardPanel(parent: LinearLayout) {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val favorites = prefs.getStringSet("clipboardFavorites", emptySet()).orEmpty()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(row, "کاپي", 1f, 38) {
            val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
            if (selected.isEmpty()) Toast.makeText(this, "لومړی متن انتخاب کړئ", Toast.LENGTH_SHORT).show()
            else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Sundus", selected))
                Toast.makeText(this, "متن کاپي شو؛ په سندس کې نه ساتل کېږي", Toast.LENGTH_SHORT).show()
            }
        }
        addSpecial(row, "کټ", 1f, 38) {
            val connection = currentInputConnection
            val selected = connection?.getSelectedText(0)?.toString().orEmpty()
            if (selected.isEmpty()) Toast.makeText(this, "لومړی متن انتخاب کړئ", Toast.LENGTH_SHORT).show()
            else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Sundus", selected))
                connection?.commitText("", 1)
            }
        }
        addSpecial(row, "پېسټ", 1f, 38) {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            if (clip != null && clip.itemCount > 0) currentInputConnection?.commitText(clip.getItemAt(0).coerceToText(this), 1)
            else Toast.makeText(this, "کلیپ‌بورډ تش دی", Toast.LENGTH_SHORT).show()
        }
        addSpecial(row, "ټول انتخاب", 1.2f, 38) {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        }
        addSpecial(row, "جمله ساتل", 1.3f, 38) { saveCurrentTextAsPhrase() }
        parent.addView(row, rowParams(42))

        val saveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(saveRow, "انتخاب شوی متن خوندي کول", 1.7f, 36) {
            val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty().trim()
            if (selected.isEmpty()) {
                Toast.makeText(this, "لومړی متن انتخاب کړئ", Toast.LENGTH_SHORT).show()
            } else {
                val updated = prefs.getStringSet("clipboardFavorites", emptySet())
                    ?.toMutableSet() ?: mutableSetOf()
                updated.add(selected)
                prefs.edit().putStringSet("clipboardFavorites", updated).apply()
                Toast.makeText(this, "یوازې ستاسو په غوښتنه متن خوندي شو", Toast.LENGTH_SHORT).show()
                refreshKeyboard()
            }
        }
        addSpecial(saveRow, "خوندي شوي: ${favorites.size}", 1.1f, 36) {
            if (favorites.isEmpty()) Toast.makeText(this, "تر اوسه مو کوم متن په خپله خوښه نه دی خوندي کړی.", Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(this).setTitle("ستاسو خوندي شوي متنونه")
                .setItems(favorites.toTypedArray()) { _, which ->
                    currentInputConnection?.commitText(favorites.toList()[which], 1)
                    updateSuggestions()
                }.show()
        }
        parent.addView(saveRow, rowParams(39))

        favorites.toList().chunked(2).forEach { group ->
            val favoriteRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { item ->
                addSpecial(favoriteRow, "📌 " + item.replace("\n", " ").take(18), 1f, 36) {
                    currentInputConnection?.commitText(item, 1)
                    updateSuggestions()
                }
                favoriteRow.getChildAt(favoriteRow.childCount - 1)?.setOnLongClickListener {
                    val updated = prefs.getStringSet("clipboardFavorites", emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    updated.remove(item)
                    prefs.edit().putStringSet("clipboardFavorites", updated).apply()
                    Toast.makeText(this, "له خوندي شويو متنونو حذف شو", Toast.LENGTH_SHORT).show()
                    refreshKeyboard()
                    true
                }
            }
            repeat(2 - group.size) { addSpecial(favoriteRow, " ", 1f, 36) { } }
            parent.addView(favoriteRow, rowParams(39))
        }

        val close = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(close, "خوندي شوي متنونه پاکول", 1.3f, 32) {
            prefs.edit().remove("clipboardFavorites").apply()
            refreshKeyboard()
        }
        addSpecial(close, "بندول", 0.8f, 32) {
            clipboardPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(close, rowParams(35))
    }

    private fun saveCurrentTextAsPhrase() {
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipboardText = if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0) {
            clipboard.primaryClip!!.getItemAt(0).coerceToText(this).toString()
        } else ""
        val phrase = selected.ifBlank { clipboardText }.trim()
        if (phrase.isBlank()) {
            Toast.makeText(this, "لومړی متن انتخاب یا کاپي کړئ", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val saved = prefs.getStringSet("savedPhrases", emptySet())?.toMutableSet() ?: mutableSetOf()
        saved.add(phrase)
        prefs.edit().putStringSet("savedPhrases", saved).apply()
        Toast.makeText(this, "جمله په شخصي جملو کې وساتل شوه", Toast.LENGTH_SHORT).show()
    }

    private fun addPhrasePanel(parent: LinearLayout) {
        val categories = listOf("اسلامي", "پښتو", "English", "دري", "زما")
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categories.forEachIndexed { index, label ->
            addSpecial(categoryRow, label, 1f, 32) {
                phraseCategory = index
                phraseIndex = 0
                refreshKeyboard()
            }
        }
        parent.addView(categoryRow, rowParams(35))

        val phraseGroups = listOf(
            listOf(
                "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ",
                "وَعَلَيْكُمُ السَّلَامُ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ",
                "جَزَاكَ اللَّهُ خَيْرًا وَبَارَكَ اللَّهُ فِيكَ",
                "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
                "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
                "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ الْعَلِيِّ الْعَظِيمِ",
                "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
                "رَبِّ زِدْنِي عِلْمًا وَارْزُقْنِي فَهْمًا",
                "اللَّهُمَّ اغْفِرْ لِي وَلِوَالِدَيَّ وَلِلْمُؤْمِنِينَ",
                "اللَّهُمَّ اهْدِنَا وَوَفِّقْنَا لِمَا تُحِبُّ وَتَرْضَى",
                "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ",
                "إِنَّ مَعَ الْعُسْرِ يُسْرًا",
                "مَا شَاءَ اللَّهُ، لَا قُوَّةَ إِلَّا بِاللَّهِ",
                "اللَّهُمَّ ارْزُقْنَا عِلْمًا نَافِعًا وَعَمَلًا صَالِحًا",
                "رَبَّنَا تَقَبَّلْ مِنَّا إِنَّكَ أَنْتَ السَّمِيعُ الْعَلِيمُ",
                "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ"
            ),
            listOf(
                "السلام علیکم، څنګه یاست؟ هیله ده روغ او جوړ به یاست.",
                "وعلیکم السلام ورحمة الله، الحمدلله ښه یم، ستاسو مهرباني ده.",
                "ستاسو له پیغام او نېکو هیلو څخه ډېره مننه.",
                "الله تعالی دې ستاسو په علم، عمر او روزۍ کې برکت واچوي.",
                "هیله ده دا ورځ مو له خوښیو او بریاوو ډکه تېره شي.",
                "زه به ستاسو غوښتنه په غور وڅېړم او ژر به ځواب درکړم.",
                "ستاسو له ارزښتناکې همکارۍ او مرستې څخه د زړه له کومې مننه.",
                "که مهرباني وکړئ، د موضوع په اړه نور معلومات هم راولېږئ.",
                "الله دې تل روغ، خوشحاله او په خپلو چارو کې بریالی لره.",
                "ستاسو خبرې مې ترلاسه کړې؛ په مناسب وخت کې به یې ځواب کړم.",
                "په درنښت او نېکو هیلو، ستاسو د بریا په هیله.",
                "بښنه غواړم چې ځواب مې لږ وځنډېد؛ له زغم څخه مو مننه.",
                "ان شاء الله، هڅه به وکړو چې دا کار په ښه او منظم ډول بشپړ کړو.",
                "ستاسو د وخت، پاملرنې او همکارۍ قدر کوم.",
                "که کومه بله پوښتنه لرئ، په خوښۍ سره یې ځوابوم.",
                "الله مو مل شه، په امان او د بریاوو په هیله."
            ),
            listOf(
                "Hello! I hope you are doing well and staying healthy.",
                "Thank you very much for your message and kind wishes.",
                "May Allah bless you with success, good health, and happiness.",
                "I have received your message and will reply as soon as possible.",
                "Please let me know if you need any further information.",
                "I sincerely appreciate your time, support, and cooperation.",
                "I hope everything is going well for you and your family.",
                "May your day be filled with peace, kindness, and success.",
                "I will review the matter carefully and get back to you soon.",
                "Thank you for your patience and understanding.",
                "Please accept my sincere apologies for the delayed response.",
                "I wish you continued success in your studies and professional work.",
                "It was a pleasure communicating with you. Best wishes!",
                "If you have any questions, please feel free to contact me.",
                "May Allah guide us to what is right and beneficial.",
                "Kind regards, and wishing you all the best."
            ),
            listOf(
                "سلام، امیدوارم صحت‌مند و خوشحال باشید.",
                "از پیام و مهربانی شما بسیار سپاسگزارم.",
                "خداوند به علم، عمر و روزی شما برکت نصیب کند.",
                "پیام شما را دریافت کردم و به زودی پاسخ خواهم داد.",
                "لطفاً اگر معلومات بیشتری لازم است، برایم ارسال کنید.",
                "از همکاری و پشتیبانی ارزشمند شما صمیمانه تشکر می‌کنم.",
                "امیدوارم روز خوبی داشته باشید و کارهایتان به خوبی پیش برود.",
                "خداوند شما و خانواده‌تان را همیشه سلامت و کامیاب نگهدارد.",
                "موضوع را با دقت بررسی می‌کنم و نتیجه را با شما شریک می‌سازم.",
                "از صبر، حوصله و درک شما بسیار ممنونم.",
                "اگر پرسش دیگری دارید، با خوشحالی پاسخ می‌دهم.",
                "با احترام و آرزوی موفقیت‌های بیشتر برای شما.",
                "ببخشید که پاسخ من کمی به تأخیر افتاد.",
                "امیدوارم همکاری ما سودمند و نتیجه‌بخش باشد.",
                "در پناه خداوند باشید و روزگار خوشی داشته باشید.",
                "برای شما سلامتی، آرامش و موفقیت آرزو می‌کنم."
            )
        )
        val phrases = when (phraseCategory) {
            in 0..3 -> phraseGroups[phraseCategory]
            else -> getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                .getStringSet("savedPhrases", emptySet()).orEmpty().toList()
        }

        if (phrases.isEmpty()) {
            val empty = TextView(this).apply {
                text = "تر اوسه شخصي جمله نه ده ساتل شوې؛ د کلیپ‌بورډ له برخې یې خوندي کړئ."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            parent.addView(empty, rowParams(42))
        } else {
            phraseIndex = phraseIndex.coerceIn(0, phrases.lastIndex)
            val navigation = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addSpecial(navigation, "❮", 0.65f, 42) {
                phraseIndex = if (phraseIndex <= 0) phrases.lastIndex else phraseIndex - 1
                refreshKeyboard()
            }
            val phraseText = TextView(this).apply {
                text = phrases[phraseIndex]
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 3
                setPadding(dp(5), dp(3), dp(5), dp(3))
                setTextColor(Color.WHITE)
                background = roundedBackground(theme.suggestion, dp(8))
                setOnClickListener {
                    currentInputConnection?.commitText(phrases[phraseIndex] + " ", 1)
                    updateSuggestions()
                }
            }
            navigation.addView(phraseText, LinearLayout.LayoutParams(0, dp(48), 1f))
            addSpecial(navigation, "❯", 0.65f, 42) {
                phraseIndex = (phraseIndex + 1) % phrases.size
                refreshKeyboard()
            }
            parent.addView(navigation, rowParams(50))

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            addSpecial(actionRow, "جمله داخلول", 1.4f, 32) {
                currentInputConnection?.commitText(phrases[phraseIndex] + " ", 1)
                updateSuggestions()
            }
            addSpecial(actionRow, "${phraseIndex + 1} / ${phrases.size}", 0.8f, 32) { }
            addSpecial(actionRow, "بندول", 0.8f, 32) {
                phrasePanelVisible = false
                refreshKeyboard()
            }
            parent.addView(actionRow, rowParams(36))
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun commitKeyTap(spec: KeySpec, frame: View) {
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString().orEmpty()
        val previous = before.trimEnd().lastOrNull()
        val sentenceStart = before.isBlank() || previous == null ||
            previous in listOf('.', '?', '!', '؟', '\n')
        val inputType = currentInputEditorInfo?.inputType
            ?: android.text.InputType.TYPE_CLASS_TEXT
        val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val isSensitiveOrAddressField = inputClass == android.text.InputType.TYPE_CLASS_TEXT &&
            variation in setOf(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            )
        val shouldAutoCapitalize = language == 2 &&
            inputClass == android.text.InputType.TYPE_CLASS_TEXT &&
            !isSensitiveOrAddressField
        val output = when {
            shifted && spec.shifted.isNotEmpty() -> spec.shifted
            shouldAutoCapitalize && (caps || shifted || sentenceStart) -> spec.normal.uppercase()
            else -> spec.normal
        }
        currentInputConnection?.commitText(output, 1)
        if (hapticEnabled) frame.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) {
            (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                .playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        }
        if (shifted && !caps) {
            shifted = false
            lastShiftTapAt = 0L
            refreshKeyboard()
        }
        updateSuggestions()
    }

    private fun showAlternatePopup(anchor: View, alternate: String) {
        val preview = TextView(this).apply {
            text = alternate
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(if (themeIndex == 0 || themeIndex == 1) Color.WHITE else Color.rgb(35, 42, 52))
            background = roundedBackground(theme.special, dp(12))
            elevation = dp(8).toFloat()
        }
        val popup = PopupWindow(preview, dp(42), dp(42), false).apply {
            isOutsideTouchable = false
            elevation = dp(8).toFloat()
        }
        popup.showAsDropDown(anchor, 0, -anchor.height - dp(44))
        anchor.postDelayed({ if (popup.isShowing) popup.dismiss() }, 650)
    }

    private fun addGlidePath(path: MutableList<String>, value: String) {
        if (path.lastOrNull() != value) path.add(value)
    }

    private fun findGlideKey(rawX: Float, rawY: Float): GlideKey? {
        val location = IntArray(2)
        var nearest: GlideKey? = null
        var nearestDistance = Double.MAX_VALUE
        glideKeys.forEach { key ->
            if (key.view.width <= 0 || key.view.height <= 0) return@forEach
            key.view.getLocationOnScreen(location)
            val centerX = location[0] + key.view.width / 2f
            val centerY = location[1] + key.view.height / 2f
            val distance = kotlin.math.hypot(
                (rawX - centerX).toDouble(),
                (rawY - centerY).toDouble()
            )
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = key
            }
        }
        return nearest?.takeIf { nearestDistance <= dp(48) }
    }

    private fun predictGlideWord(path: List<String>): String? {
        val typedPath = path.joinToString("").lowercase()
        if (typedPath.length < 3) return null
        val personalWords = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
            .getStringSet("personalWords_$language", emptySet()).orEmpty()
        val candidates = (offlineWordsFor(language) + personalWords)
            .distinct()
            .filter { it.length >= 3 }
        val ranked = candidates.map { word ->
            word to glideEditDistance(typedPath, word.lowercase())
        }.sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.length - typedPath.length) })
        val best = ranked.firstOrNull() ?: return null
        val maxDistance = maxOf(1, typedPath.length / 5)
        if (best.second > maxDistance) return null
        if (ranked.size > 1 && ranked[1].second == best.second &&
            kotlin.math.abs(ranked[1].first.length - typedPath.length) ==
            kotlin.math.abs(best.first.length - typedPath.length)) return null
        return best.first
    }

    private fun glideEditDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun addKeyRow(
        parent: LinearLayout,
        keys: List<KeySpec>,
        height: Int
    ) {
        val actualHeight = (height * listOf(0.78f, 0.90f, 1.0f, 1.10f, 1.22f)[keySizeLevel]).toInt()
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
                text = if (value == spec.normal && spec.display.isNotEmpty()) spec.display else value
                textSize = if (keys.size >= 12) 15f else 18f
                typeface = when (fontStyleIndex) {
                    1 -> Typeface.create("serif", Typeface.BOLD)
                    2 -> Typeface.create("monospace", Typeface.BOLD)
                    else -> Typeface.DEFAULT_BOLD
                }
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(35, 42, 52))
                gravity = Gravity.CENTER
            }

            frame.addView(
                mainText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            val longPressOutput = when {
                spec.longPress.isNotEmpty() -> spec.longPress
                spec.shifted.isNotEmpty() -> spec.shifted
                language == 2 -> spec.normal.uppercase()
                else -> ""
            }

            if (longPressOutput.isNotEmpty()) {
                val hint = TextView(this).apply {
                    text = longPressOutput
                    textSize = 10f
                    setTextColor(theme.accent)
                    gravity = Gravity.BOTTOM or Gravity.END
                    setPadding(0, 0, dp(4), dp(3))
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
                dp(13)
            )
            frame.elevation = dp(3).toFloat()

            if (spec.normal.length == 1 && spec.normal[0].isLetter()) {
                glideKeys.add(GlideKey(frame, spec.normal))
                var downRawX = 0f
                var downRawY = 0f
                var glideStarted = false
                var longPressHandled = false
                val path = mutableListOf<String>()
                val longPressTask = Runnable {
                    if (!glideStarted && longPressOutput.isNotEmpty()) {
                        currentInputConnection?.commitText(longPressOutput, 1)
                        showAlternatePopup(frame, longPressOutput)
                        longPressHandled = true
                        updateSuggestions()
                    }
                }

                frame.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            glideStarted = false
                            longPressHandled = false
                            path.clear()
                            path.add(spec.normal)
                            handler.postDelayed(longPressTask, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val moved = kotlin.math.hypot(
                                (event.rawX - downRawX).toDouble(),
                                (event.rawY - downRawY).toDouble()
                            )
                            if (moved > dp(10)) handler.removeCallbacks(longPressTask)
                            if (moved > dp(17)) glideStarted = true
                            if (glideStarted && !longPressHandled) {
                                findGlideKey(event.rawX, event.rawY)?.let { addGlidePath(path, it.value) }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(longPressTask)
                            if (longPressHandled) {
                                true
                            } else if (glideStarted && path.size >= 3) {
                                findGlideKey(event.rawX, event.rawY)?.let { addGlidePath(path, it.value) }
                                val candidate = predictGlideWord(path)
                                val output = candidate ?: path.joinToString("")
                                currentInputConnection?.commitText(output, 1)
                                updateSuggestions()
                                true
                            } else {
                                commitKeyTap(spec, frame)
                                true
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacks(longPressTask)
                            true
                        }
                        else -> true
                    }
                }
            } else {
                frame.setOnClickListener {
                    commitKeyTap(spec, frame)
                }

                frame.setOnLongClickListener {
                    val alternate = longPressOutput
                    if (alternate.isEmpty()) {
                        false
                    } else {
                        currentInputConnection?.commitText(alternate, 1)
                        showAlternatePopup(frame, alternate)
                        updateSuggestions()
                        true
                    }
                }
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
            setTextColor(if (themeIndex == 0 || themeIndex == 1) Color.WHITE else Color.rgb(45, 52, 62))
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = if (label.contains("SUNDUS")) {
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(3, 47, 91), Color.rgb(2, 101, 132), Color.rgb(3, 47, 91))
                ).apply {
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(2), Color.rgb(226, 181, 67))
                }
            } else roundedBackground(theme.special, dp(12))
            if (label.contains("SUNDUS")) {
                setTextColor(Color.rgb(255, 218, 116))
                textSize = 15f
                typeface = Typeface.create("serif", Typeface.BOLD)
            }
            elevation = dp(4).toFloat()
            letterSpacing = 0.01f
        }

        if (label == "Space" || label.contains("SUNDUS")) {
            var downX = 0f
            button.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; true }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.x - downX
                        if (kotlin.math.abs(delta) > dp(18)) {
                            val steps = (kotlin.math.abs(delta) / dp(12)).toInt().coerceAtLeast(1)
                            repeat(steps) { sendKey(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT) }
                        } else action()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        } else if (label == "⌫") {
            val handler = Handler(Looper.getMainLooper())
            var repeatTask: Runnable? = null
            var wordTask: Runnable? = null
            var wordDeleteMode = false
            var swipeDeleteTriggered = false
            var touchStartX = 0f

            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        swipeDeleteTriggered = false
                        wordDeleteMode = false
                        action()
                        val characterRepeat = object : Runnable {
                            override fun run() {
                                if (!wordDeleteMode) {
                                    action()
                                    handler.postDelayed(this, 65)
                                }
                            }
                        }
                        repeatTask = characterRepeat
                        handler.postDelayed(characterRepeat, 400)
                        val switchToWords = Runnable {
                            wordDeleteMode = true
                            repeatTask?.let { handler.removeCallbacks(it) }
                            deletePreviousWord()
                            val wordRepeat = object : Runnable {
                                override fun run() {
                                    if (wordDeleteMode) {
                                        deletePreviousWord()
                                        handler.postDelayed(this, 420)
                                    }
                                }
                            }
                            repeatTask = wordRepeat
                            handler.postDelayed(wordRepeat, 420)
                        }
                        wordTask = switchToWords
                        handler.postDelayed(switchToWords, 650)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!swipeDeleteTriggered && touchStartX - event.x > dp(32)) {
                            swipeDeleteTriggered = true
                            wordDeleteMode = true
                            wordTask?.let { handler.removeCallbacks(it) }
                            repeatTask?.let { handler.removeCallbacks(it) }
                            wordTask = null
                            repeatTask = null
                            // SwiftKey-style left swipe on Backspace removes the rest of the current word.
                            deletePreviousWord()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        wordTask?.let { handler.removeCallbacks(it) }
                        repeatTask?.let { handler.removeCallbacks(it) }
                        wordTask = null
                        repeatTask = null
                        wordDeleteMode = false
                        swipeDeleteTriggered = false
                        true
                    }
                    else -> true
                }
            }
        } else {
            button.setOnClickListener {
                if (hapticEnabled) it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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

        val candidates = (offlineWordsFor(language) + personalWords).distinct()
        val prefixMatches = candidates.filter {
            it.startsWith(currentWord, ignoreCase = true) &&
                !it.equals(currentWord, ignoreCase = true)
        }.sortedWith(
            compareBy<String> { kotlin.math.abs(it.length - currentWord.length) }
                .thenBy { it.length }
        )
        if (prefixMatches.isNotEmpty()) return prefixMatches.take(3)

        if (currentWord.length < 4) return emptyList()
        return candidates
            .map { word ->
                Triple(
                    word,
                    editDistanceAtMostOne(currentWord.lowercase(), word.lowercase()),
                    word.commonPrefixWith(currentWord, ignoreCase = true).length
                )
            }
            .filter { it.second == 1 }
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { kotlin.math.abs(it.first.length - currentWord.length) }
                    .thenByDescending { it.third }
                    .thenBy { it.first.length }
            )
            .map { it.first }
            .take(3)
    }

    private fun editDistanceAtMostOne(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                // Treat a neighboring-letter transposition as one common typing error.
                if (edits == 0 && a.length == b.length &&
                    i + 1 < a.length && j + 1 < b.length &&
                    a[i] == b[j + 1] && a[i + 1] == b[j]
                ) {
                    edits++
                    i += 2
                    j += 2
                    continue
                }
                edits++
                if (edits > 1) return edits
                if (a.length > b.length) i++
                else if (b.length > a.length) j++
                else { i++; j++ }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits
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
        val editorType = currentInputEditorInfo?.inputType ?: 0
        val inputClass = editorType and android.text.InputType.TYPE_MASK_CLASS
        val variation = editorType and android.text.InputType.TYPE_MASK_VARIATION
        val isTextPassword = inputClass == android.text.InputType.TYPE_CLASS_TEXT &&
            variation in setOf(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        val isNumericPassword = inputClass == android.text.InputType.TYPE_CLASS_NUMBER &&
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val suppressSuggestions = (editorType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        // Never expose predictions, saved email, or clipboard content in password fields.
        if (isTextPassword || isNumericPassword || suppressSuggestions) return

        val savedEmail = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getString("preferredEmail", "").orEmpty()
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
            if (savedEmail.isBlank()) return
            val emailChip = TextView(this).apply {
                text = "✉ $savedEmail"; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38,45,55))
                setPadding(dp(10), dp(3), dp(10), dp(3)); background = roundedBackground(theme.keyColors[0], dp(8))
                setOnClickListener { currentInputConnection?.commitText(savedEmail, 1); updateSuggestions() }
            }
            container.addView(emailChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))
            return
        }
        // Clipboard content is accessed only after the user opens the clipboard panel.
        val suggestions = getOfflineSuggestions()

        suggestions.forEach { word ->

            val suggestion = TextView(this).apply {
                text = word
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38, 45, 55))
                setPadding(
                    dp(10),
                    dp(3),
                    dp(10),
                    dp(3)
                )
                background = roundedBackground(theme.keyColors[0], dp(8))
                elevation = dp(1).toFloat()

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

    private fun deletePreviousWord() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val word = getCurrentWord(trimmed)
        val count = before.length - (trimmed.length - word.length)
        if (count > 0) connection.deleteSurroundingText(count, 0) else deletePreviousCharacter()
        updateSuggestions()
    }

    private fun deletePreviousCharacter() {
        val connection = currentInputConnection ?: return
        // Backspace must remove the entire selected range before deleting a character.
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            // Replace the selected range first; send DEL as a compatibility fallback
            // for editors whose InputConnection does not implement commitText reliably.
            val replaced = runCatching { connection.commitText("", 1) }.getOrDefault(false)
            if (!replaced) sendKey(KeyEvent.KEYCODE_DEL)
            updateSuggestions()
            return
        }
        val extracted = runCatching {
            connection.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        }.getOrNull()
        if (extracted != null && extracted.selectionStart >= 0 &&
            extracted.selectionEnd >= 0 && extracted.selectionStart != extracted.selectionEnd) {
            sendKey(KeyEvent.KEYCODE_DEL)
            updateSuggestions()
            return
        }
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
        fun shade(factor: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
            return Color.HSVToColor(Color.alpha(color), hsv)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(shade(1.10f), color, shade(0.90f))
        ).apply {
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
        val density = resources.displayMetrics.density
        val screenHeightDp = resources.displayMetrics.heightPixels / density
        val compactScale = (screenHeightDp / 780f).coerceIn(0.82f, 1f)
        return (value * density * compactScale).toInt()
    }

    private fun showEmailSetup() {
        val input = EditText(this).apply {
            hint = "name@example.com"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getString("preferredEmail", ""))
        }
        AlertDialog.Builder(this).setTitle("ستاسو ایمیل").setMessage("ایمیل یوازې په دې موبایل کې ساتل کېږي او د ایمیل په خانه کې به په یوه ټک ښکاره شي.")
            .setView(input).setPositiveButton("ساتل") { _, _ ->
                getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).edit().putString("preferredEmail", input.text.toString().trim()).apply()
                refreshKeyboard()
            }.setNegativeButton("بند", null).show()
    }

    private fun saveSettings() {
        getSharedPreferences(
            "SharafzaiSettings",
            MODE_PRIVATE
        ).edit()
            .putInt("language", language)
            .putInt("themeIndex", themeIndex)
            .putInt("keySizeLevel", keySizeLevel)
            .putInt("fontStyleIndex", fontStyleIndex)
            .putBoolean("numberRowEnabled", numberRowEnabled)
            .putBoolean("nativeDigitsEnabled", nativeDigitsEnabled)
            .putBoolean("autoCorrectEnabled", autoCorrectEnabled)
            .putBoolean("hapticEnabled", hapticEnabled)
            .putBoolean("soundEnabled", soundEnabled)
            .apply()
    }
}


/** A lightweight, offline floral wallpaper drawn behind the keyboard keys. */
private class FloralPatternDrawable(private val baseColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(baseColor, Color.rgb(3, 42, 72), Color.rgb(1, 17, 43)),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        fun flower(cx: Float, cy: Float, radius: Float, tint: Int) {
            paint.color = tint
            paint.alpha = 92
            for (i in 0 until 8) {
                val angle = Math.PI * i / 4.0
                val px = cx + kotlin.math.cos(angle).toFloat() * radius * 0.52f
                val py = cy + kotlin.math.sin(angle).toFloat() * radius * 0.52f
                canvas.drawCircle(px, py, radius * 0.31f, paint)
            }
            paint.color = Color.argb(175, 255, 214, 105)
            canvas.drawCircle(cx, cy, radius * 0.20f, paint)
        }

        fun leaf(cx: Float, cy: Float, width: Float, height: Float, tint: Int, angle: Float) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            paint.color = tint
            paint.alpha = 66
            canvas.drawOval(
                cx - width / 2f, cy - height / 2f,
                cx + width / 2f, cy + height / 2f, paint
            )
            canvas.restore()
        }

        val radius = (h * 0.075f).coerceIn(24f, 48f)
        flower(w * 0.025f, h * 0.10f, radius, Color.rgb(255, 104, 170))
        flower(w * 0.975f, h * 0.17f, radius * 1.05f, Color.rgb(255, 183, 77))
        flower(w * 0.035f, h * 0.91f, radius * 0.92f, Color.rgb(169, 133, 255))
        flower(w * 0.965f, h * 0.88f, radius, Color.rgb(255, 112, 153))
        flower(w * 0.50f, h * 0.04f, radius * 0.48f, Color.rgb(95, 220, 205))
        leaf(w * 0.13f, h * 0.20f, radius * 1.3f, radius * 0.42f, Color.rgb(61, 177, 128), -28f)
        leaf(w * 0.86f, h * 0.29f, radius * 1.35f, radius * 0.43f, Color.rgb(61, 177, 128), 32f)
        leaf(w * 0.15f, h * 0.80f, radius * 1.25f, radius * 0.40f, Color.rgb(61, 177, 128), 25f)
        leaf(w * 0.85f, h * 0.78f, radius * 1.25f, radius * 0.40f, Color.rgb(61, 177, 128), -30f)

        paint.color = Color.argb(58, 20, 221, 245)
        paint.strokeWidth = (h * 0.003f).coerceAtLeast(1f)
        canvas.drawLine(0f, h * 0.19f, w, h * 0.19f, paint)
        canvas.drawLine(0f, h * 0.985f, w, h * 0.985f, paint)
        canvas.restoreToCount(save)
    }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated by Android")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

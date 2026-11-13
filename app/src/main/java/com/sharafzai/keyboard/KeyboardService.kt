
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
import android.widget.EditText
import android.app.AlertDialog
import android.widget.Toast
import android.widget.PopupWindow
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock

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
    private var calculatorPanelVisible = false
    private var toolsPanelVisible = false
    private var settingsPanelVisible = false
    private var phrasePanelVisible = false
    private var phraseCategory = 0
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
    private var adhkarIndex = 0
    private var adhkarTextView: TextView? = null
    private val adhkarHandler = Handler(Looper.getMainLooper())
    private val adhkarList = listOf(
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
        "لَا إِلٰهَ إِلَّا اللَّهُ",
        "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَاللَّهُ أَكْبَرُ",
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
        "سُبْحَانَ اللَّهِ الْعَظِيمِ",
        "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
        "اللَّهُ أَكْبَرُ كَبِيرًا",
        "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ",
        "رَبِّ اغْفِرْ لِي وَارْحَمْنِي",
        "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
        "لَا إِلٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ",
        "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً",
        "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ",
        "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ",
        "بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ",
        "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ",
        "اللَّهُمَّ بَارِكْ لَنَا فِيمَا رَزَقْتَنَا",
        "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا"
    )
    private val adhkarRotation = object : Runnable {
        override fun run() {
            adhkarIndex = (adhkarIndex + 1) % adhkarList.size
            adhkarTextView?.apply {
                text = "۞ ذکر ۞  " + adhkarList[adhkarIndex]
                val colors = listOf(Color.rgb(0, 190, 190), Color.rgb(190, 70, 180), Color.rgb(225, 155, 20), Color.rgb(55, 130, 225))
                val color = colors[adhkarIndex % colors.size]
                setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, Color.rgb(8, 35, 75))).apply { cornerRadius = dp(12).toFloat() }
            }
            adhkarHandler.postDelayed(this, 7_000)
        }
    }
    private var calculatorExpression = ""

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
        KeyboardTheme(Color.rgb(241, 244, 248),
            listOf(Color.rgb(255, 255, 255), Color.rgb(250, 252, 255), Color.rgb(235, 240, 247)),
            Color.rgb(224, 232, 242), Color.rgb(251, 252, 254),
            Color.rgb(39, 104, 160), Color.rgb(207, 217, 230)),
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

        language = prefs.getInt("language", 0).coerceIn(0, 4)
        themeIndex = prefs.getInt("themeIndex", 0)
            .coerceIn(0, themes.size - 1)
        keySizeLevel = prefs.getInt("keySizeLevel", 2).coerceIn(0, 4)
        fontStyleIndex = prefs.getInt("fontStyleIndex", 0).coerceIn(0, 2)
        numberRowEnabled = prefs.getBoolean("numberRowEnabled", true)
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
        adhkarHandler.removeCallbacks(adhkarRotation)
        adhkarTextView = null
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
        4 to listOf("اللہ","محمد","اسلام","قرآن","کتاب","مدرسہ","اردو","زبان","علم","استاد","طالب","دوست","گھر","کام","زندگی","شکریہ","سلام","صبح","شام","دن","رات","ہفتہ","مہینہ","سال","وقت","پیغام","موبائل","خاندان","مسجد","قلم","خوبصورت","آج","کل","کیوں","کہاں","کیا","کون","کیسے","جی","نہیں","براہ","کرم","مہربانی","انشاءاللہ","الحمدللہ","میں","آپ","تم","ہم","وہ","یہ","میرا","آپکا","اچھا","بہت","ضرور","براہِ","معاف","کیجیے","مدد","پانی","کھانا","کتابیں","تعلیم","طالبعلم","خوشی","محبت","پاکستان","افغانستان","پشاور","کابل","جلال آباد","اللہ تعالیٰ","رسول","صلی","وسلم")
    )

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
            k("ت", "ټ"), k("ن", "ڼ"), k("م"),
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


    // Urdu phonetic layout with distinct Urdu-specific letters.
    // Urdu phonetic QWERTY layout: each base letter appears only once.
    private val urduRows = listOf(
        listOf(
            k("ق", "ؒ"), k("و", "ؤ"), k("ع", "ء"), k("ر", "ڑ"), k("ت", "ٹ"),
            k("ے", "ۓ"), k("ء", "ئ"), k("ی", "ئ"), k("ہ", "ھ"), k("پ", "ﷲ")
        ),
        listOf(
            k("ا", "آ"), k("س", "ص"), k("د", "ڈ"), k("ف", "ِ"),
            k("گ", "گھ"), k("ح", "حّ"), k("ج", "ض"), k("ک", "کھ"), k("ل", "لٰ")
        ),
        listOf(
            k("ز", "ذ"), k("ش", "ث"), k("چ", "چھ"), k("ط", "ظ"), k("ب", "بھ"),
            k("ن", "ں"), k("م", "مّ"), k("،", ";"), k("۔", ":"), k("؟", "!")
        )
    )

    private val urduNumbers = listOf(
        k("۱","!","1"), k("۲","@","2"), k("۳","#","3"), k("۴","$","4"),
        k("۵","%","5"), k("۶","^","6"), k("۷","&","7"), k("۸","*","8"),
        k("۹","(","9"), k("۰",")","0"), k("-","_"), k("=","+")
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
        startAdhkarRotation()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        adhkarHandler.removeCallbacks(adhkarRotation)
        adhkarTextView = null
        super.onFinishInputView(finishingInput)
    }

    private fun startAdhkarRotation() {
        adhkarHandler.removeCallbacks(adhkarRotation)
        adhkarIndex = 0
        adhkarTextView?.apply {
            text = "۞ ذکر ۞  " + adhkarList[adhkarIndex]
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(0, 155, 180), Color.rgb(8, 35, 75))).apply { cornerRadius = dp(12).toFloat() }
        }
        adhkarHandler.postDelayed(adhkarRotation, 7_000)
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
        glideKeys.clear()
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
                0 -> "✦ سندس  |  پښتو"
                1 -> "✦ سندس  |  العربية"
                2 -> "✦ سندس  |  ENGLISH"
                3 -> "✦ سندس  |  دری"
                else -> "✦ سندس  |  اردو"
            }
            textSize = 13f
            letterSpacing = 0.03f
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38, 45, 55))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(29), 1f)
        )

        addSpecial(header, "🎙", 0.8f, 29) {
            startActivity(Intent(this, VoiceInputActivity::class.java)
                .putExtra("language", language)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        addSpecial(header, "🌐", 0.8f, 29) {
            language = (language + 1) % 5
            shifted = false
            caps = false
            lastShiftTapAt = 0L
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
            keySizeLevel = (keySizeLevel + 1) % 5
            saveSettings()
            refreshKeyboard()
            val sizeLabel = listOf("ډېره وړه", "وړه", "منځنۍ", "لویه", "ډېره لویه")[keySizeLevel]
            Toast.makeText(this, "د تڼیو اندازه: $sizeLabel", Toast.LENGTH_SHORT).show()
        }

        addSpecial(header, "⚙", 0.8f, 29) {
            settingsPanelVisible = !settingsPanelVisible
            if (settingsPanelVisible) {
                emojiPanelVisible = false
                diacriticsPanelVisible = false
                symbolsPanelVisible = false
                calculatorPanelVisible = false
                toolsPanelVisible = false
                clipboardPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }

        root.addView(header, rowParams(31))

        val topTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(topTools, "📋", 0.8f, 35) {
            clipboardPanelVisible = !clipboardPanelVisible
            if (clipboardPanelVisible) {
                emojiPanelVisible = false; diacriticsPanelVisible = false
                symbolsPanelVisible = false; calculatorPanelVisible = false
                toolsPanelVisible = false; settingsPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }
        addSpecial(topTools, "وسایل", 1.0f, 35) {
            toolsPanelVisible = !toolsPanelVisible
            phrasePanelVisible = false
            if (toolsPanelVisible) {
                emojiPanelVisible = false; diacriticsPanelVisible = false
                symbolsPanelVisible = false; calculatorPanelVisible = false
                settingsPanelVisible = false; clipboardPanelVisible = false
            }
            refreshKeyboard()
        }
        addSpecial(topTools, "Caps", 0.9f, 35) { caps = !caps; shifted = false; lastShiftTapAt = 0L; refreshKeyboard() }
        addSpecial(topTools, "AutoText", 1.0f, 35) { showAutoTextEditor() }
        addSpecial(topTools, "جملې", 1.0f, 35) {
            phrasePanelVisible = !phrasePanelVisible
            toolsPanelVisible = false
            if (phrasePanelVisible) {
                emojiPanelVisible = false; diacriticsPanelVisible = false
                symbolsPanelVisible = false; calculatorPanelVisible = false
                settingsPanelVisible = false; clipboardPanelVisible = false
            }
            refreshKeyboard()
        }
        adhkarTextView = TextView(this).apply {
            text = "۞ ذکر ۞  " + adhkarList[adhkarIndex]
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(0, 190, 190), Color.rgb(8, 35, 75))).apply { cornerRadius = dp(12).toFloat() }
            elevation = dp(5).toFloat()
        }
        root.addView(adhkarTextView, rowParams(43))
        root.addView(topTools, rowParams(37))

        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(theme.suggestion)
        }

        root.addView(suggestionContainer, rowParams(32))

        if (!emojiPanelVisible && !clipboardPanelVisible && !symbolsPanelVisible) {
            val nativeNumberRow = when (language) {
                0 -> pashtoNumbers
                1 -> arabicNumbers
                2 -> englishNumbers
                3 -> dariNumbers
                else -> urduNumbers
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
                else -> urduRows
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
                calculatorPanelVisible = false
                toolsPanelVisible = false
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
                    calculatorPanelVisible = false
                    toolsPanelVisible = false
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
                calculatorPanelVisible = false
                toolsPanelVisible = false
                settingsPanelVisible = false
                clipboardPanelVisible = false
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

        // Calculator and utility screens are provided by ToolsActivity, not inside the typing keyboard.

        if (phrasePanelVisible) {
            addPhrasePanel(root)
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

        addSpecial(bottom, "Space", 3.5f, 42) { expandAutoTextAndSpace() }

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
            text = "د سندس کیبورډ تنظیمات"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38, 45, 55))
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
        addSpecial(row, "ژبه: ${listOf("پښتو", "عربي", "English", "دري", "اردو")[language]}", 1.5f, 40) {
            language = (language + 1) % 5
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
        val themeNames = listOf("مرغلرین", "شپه", "سمندري", "زرغون", "بنفش", "ګلابي", "طلایي", "یاقوتي", "فیروزه", "لاجوردي", "زمردي", "سپوږمۍ", "لمر", "آسماني", "باراني", "ځنګل", "ګلستان", "مرجاني", "ارغواني", "نقره", "برونزي", "شفق", "یخ", "اور", "شګه", "کاکاو", "نعنا", "لیمو", "انګور", "ګلاب", "مرجان", "دود", "شاهي", "مخمل", "کریستال", "سپین زر", "تور الماس", "زمرد شپه", "طلایي شفق", "سندس ځانګړی", "لاجوردي شپه", "شنه زمرد", "ګلابي سپوږمۍ", "شاهي ارغواني", "د صحرا لمر", "یخي آبي", "مروارید", "تور شفق")
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
        val languageName = listOf("پښتو", "عربي", "English", "دري", "اردو")[language]

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

    private fun addToolsPanel(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addSpecial(row, "📅 زما کلنډر", 1.5f, 40) {
            openSharafzaiTools("calendar")
        }
        addSpecial(row, "⏰ زما الارم", 1.5f, 40) {
            openSharafzaiTools("alarms")
        }
        addSpecial(row, "Close", 1f, 40) {
            toolsPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(row, rowParams(43))
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
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sundus", selected))
                rememberClipboardText(selected)
                Toast.makeText(this, "متن کاپي شو", Toast.LENGTH_SHORT).show()
            }
        }
        addSpecial(row, "کټ", 1f, 38) {
            val connection = currentInputConnection
            val selected = connection?.getSelectedText(0)?.toString().orEmpty()
            if (selected.isEmpty()) Toast.makeText(this, "لومړی متن انتخاب کړئ", Toast.LENGTH_SHORT).show()
            else {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sundus", selected))
                rememberClipboardText(selected)
                connection?.commitText("", 1)
            }
        }
        addSpecial(row, "پېسټ", 1f, 38) {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            if (clip != null && clip.itemCount > 0) currentInputConnection?.commitText(clip.getItemAt(0).coerceToText(this), 1)
            else Toast.makeText(this, "کلیپ‌بورډ تش دی", Toast.LENGTH_SHORT).show()
        }
        addSpecial(row, "ټول انتخاب", 1.2f, 38) { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
        addSpecial(row, "جمله ساتل", 1.4f, 38) { saveCurrentTextAsPhrase() }
        parent.addView(row, rowParams(42))

        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(searchRow, "🔍 د کلیپ‌بورډ لټون", 1.5f, 36) {
            val searchBox = EditText(this).apply { hint = "د متن یوه برخه ولیکئ" }
            AlertDialog.Builder(this).setTitle("کلیپ‌بورډ لټون").setView(searchBox)
                .setPositiveButton("لټون") { _, _ ->
                    val query = searchBox.text.toString().trim()
                    val all = (favorites + (0 until 8).mapNotNull { prefs.getString("clipboardHistory_$it", null) }).distinct()
                    val matches = if (query.isBlank()) all else all.filter { it.contains(query, ignoreCase = true) }
                    if (matches.isEmpty()) Toast.makeText(this, "پایله ونه موندل شوه", Toast.LENGTH_SHORT).show()
                    else AlertDialog.Builder(this).setTitle("پایلې")
                        .setItems(matches.toTypedArray()) { _, which -> currentInputConnection?.commitText(matches[which], 1); updateSuggestions() }.show()
                }.setNegativeButton("بند", null).show()
        }
        addSpecial(searchRow, "📌 خوښ شوي: ${favorites.size}", 1f, 36) {
            if (favorites.isEmpty()) Toast.makeText(this, "تر اوسه خوښ شوی متن نشته؛ د متن پر تڼۍ اوږد ټچ وکړئ.", Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(this).setTitle("قفل شوي او خوښ شوي متنونه")
                .setItems(favorites.toTypedArray()) { _, which -> currentInputConnection?.commitText(favorites.toList()[which], 1); updateSuggestions() }.show()
        }
        parent.addView(searchRow, rowParams(39))

        val historyTitle = TextView(this).apply {
            text = "وروستي کاپي شوي متنونه — پر متن اوږد ټچ = قفل/خلاص"
            textSize = 11f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(45, 52, 62))
        }
        parent.addView(historyTitle, rowParams(27))
        val history = (favorites.toList() + (0 until 8).mapNotNull { index ->
            prefs.getString("clipboardHistory_$index", null)?.takeIf { it.isNotBlank() }
        }).distinct()
        history.chunked(2).forEach { group ->
            val historyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { item ->
                val pinned = item in favorites
                val label = (if (pinned) "📌 " else "") + item.replace("\n", " ").take(16)
                addSpecial(historyRow, label, 1f, 36) {
                    currentInputConnection?.commitText(item, 1)
                    updateSuggestions()
                }
                historyRow.getChildAt(historyRow.childCount - 1)?.setOnLongClickListener {
                    val updated = favorites.toMutableSet()
                    if (pinned) updated.remove(item) else updated.add(item)
                    prefs.edit().putStringSet("clipboardFavorites", updated).apply()
                    Toast.makeText(this, if (pinned) "له خوښ شويو خلاص شو" else "متن قفل او خوښ شو", Toast.LENGTH_SHORT).show()
                    refreshKeyboard()
                    true
                }
            }
            repeat(2 - group.size) { addSpecial(historyRow, " ", 1f, 36) { } }
            parent.addView(historyRow, rowParams(39))
        }
        if (history.isEmpty()) {
            val empty = TextView(this).apply {
                text = "د کاپي تاریخچه لا تشه ده."; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.GRAY)
            }
            parent.addView(empty, rowParams(28))
        }
        val close = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(close, "تاریخچه پاکول", 1.2f, 32) {
            prefs.edit().apply { (0 until 8).forEach { remove("clipboardHistory_$it") } }.apply()
            refreshKeyboard()
        }
        addSpecial(close, "بندول", 0.8f, 32) { clipboardPanelVisible = false; refreshKeyboard() }
        parent.addView(close, rowParams(35))
    }

    private fun rememberClipboardText(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val previous = (0 until 8).mapNotNull { index ->
            prefs.getString("clipboardHistory_$index", null)
        }
        val updated = (listOf(clean) + previous.filterNot { it == clean }).take(8)
        prefs.edit().apply {
            (0 until 8).forEach { index ->
                if (index < updated.size) putString("clipboardHistory_$index", updated[index])
                else remove("clipboardHistory_$index")
            }
        }.apply()
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
        val categories = listOf("اسلامي", "پښتو", "English", "دري", "اردو", "ډیزاین", "۞", "زما")
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categories.forEachIndexed { index, label ->
            addSpecial(categoryRow, label, 1f, 36) { phraseCategory = index; refreshKeyboard() }
        }
        parent.addView(categoryRow, rowParams(39))
        val phrases = when (phraseCategory) {
            0 -> listOf("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ","الْحَمْدُ لِلَّهِ","مَا شَاءَ اللَّهُ","إِنْ شَاءَ اللَّهُ","سُبْحَانَ اللَّهِ","أَسْتَغْفِرُ اللَّهَ","جَزَاكَ اللَّهُ خَيْرًا","بَارَكَ اللَّهُ فِيكَ","السَّلَامُ عَلَيْكُمْ","وَعَلَيْكُمُ السَّلَامُ","صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ","رَضِيَ اللَّهُ عَنْهُ","سُبْحَانَ اللَّهِ وَبِحَمْدِهِ","لَا إِلٰهَ إِلَّا اللَّهُ","اللَّهُ أَكْبَرُ","لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ","حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ","رَبِّ اغْفِرْ لِي","اللَّهُمَّ ارْحَمْنِي","أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ","رَبَّنَا تَقَبَّلْ مِنَّا","اللَّهُمَّ بَارِكْ لَنَا","اللَّهُمَّ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ")
            1 -> listOf("السلام علیکم، څنګه یاست؟","وعلیکم السلام، الحمدلله ښه یم","ډېره مننه","هیڅ خبره نه ده","مهرباني وکړئ","الله دې مل شه","په خیر راغلې","ستړی مه شې","بیا به سره ګورو","ستاسو له مرستې مننه","بښنه غواړم","ښه ورځ ولرئ","الله دې مل شه او بریالی اوسې","ستاسې له مهربانۍ مننه","هیله ده روغ او جوړ به یاست","زه به ژر درته ځواب درکړم","په امان الله","الله دې ساتنه وکړي","ستاسې ورځ دې نېکمرغه وي","د بریا په هیله","ډېر ښه، مننه","ان شاء الله، همداسې به وکړو")
            2 -> listOf("Hello!","How are you?","I hope you are well.","Thank you very much.","You're welcome.","Please let me know.","Have a nice day!","Good morning!","Good evening!","See you soon.","May Allah bless you.","Best wishes.")
            3 -> listOf("سلام، حال شما چطور است؟","تشکر بسیار","خواهش می‌کنم","روز خوش داشته باشید","خداوند نگهدارتان باشد","از همکاری شما سپاسگزارم","به امید دیدار","صبح بخیر","شب بخیر","موفق باشید","ببخشید","لطفاً")
            4 -> listOf("السلام علیکم","وعلیکم السلام","جزاک اللہ خیراً","اللہ آپ کو خوش رکھے","بہت شکریہ","براہِ کرم","خدا حافظ","ان شاء اللہ","الحمد للہ","سبحان اللہ","صلی اللہ علیہ وسلم","آپ کیسے ہیں؟","میں خیریت سے ہوں","اللہ آپ کو کامیاب کرے","معاف کیجیے","آپ کا بہت شکریہ","خوش آمدید","اللہ حافظ","ماشاء اللہ","الحمدللہ رب العالمین")
            5 -> listOf("❖ مننه ❖","✦ ښه راغلاست ✦","『 السلام علیکم 』","《 الحمد لله 》","༺ مننه ༻","✧ په خیر اوسئ ✧","◈ بریالی اوسئ ◈","❀ خوښ او خوشحاله اوسئ ❀","★ الله مو مل شه ★")
            6 -> listOf("۞ بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۞","۞ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۞","﷽","ﷺ","۞ سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ۞","۞ جَزَاكَ اللَّهُ خَيْرًا ۞","﴿ وَمَا تَوْفِيقِي إِلَّا بِاللَّهِ ﴾","﴿ إِنَّ مَعَ الْعُسْرِ يُسْرًا ﴾")
            else -> getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                .getStringSet("savedPhrases", emptySet()).orEmpty().toList()
        }
        if (phrases.isEmpty()) {
            val empty = TextView(this).apply {
                text = "تر اوسه شخصي جمله نه ده ساتل شوې. د کلیپ‌بورډ له برخې څخه جمله وساتئ."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(45, 52, 62))
            }
            parent.addView(empty, rowParams(38))
        }
        phrases.chunked(3).forEach { group ->
            val phraseRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { phrase ->
                addSpecial(phraseRow, phrase, 1f, 42) {
                    currentInputConnection?.commitText("$phrase ", 1)
                    updateSuggestions()
                }
            }
            repeat(3 - group.size) { addSpecial(phraseRow, " ", 1f, 42) { } }
            parent.addView(phraseRow, rowParams(45))
        }
        val closeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(closeRow, "بندول", 1f, 34) { phrasePanelVisible = false; refreshKeyboard() }
        parent.addView(closeRow, rowParams(37))
    }

    private fun openSharafzaiTools(page: String) {
        startActivity(Intent(this, ToolsActivity::class.java)
            .putExtra("open_page", page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun addCalculatorPanel(parent: LinearLayout) {
        val display = TextView(this).apply {
            text = calculatorExpression.ifEmpty { "0" }
            textSize = 23f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38, 45, 55))
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

    private val handler = Handler(Looper.getMainLooper())

    private fun commitKeyTap(spec: KeySpec, frame: View) {
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString().orEmpty()
        val previous = before.trimEnd().lastOrNull()
        val sentenceStart = before.isBlank() || previous == null ||
            previous in listOf('.', '?', '!', '؟', '\n')
        val output = when {
            shifted && spec.shifted.isNotEmpty() -> spec.shifted
            language == 2 && (caps || shifted || sentenceStart) -> spec.normal.uppercase()
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
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(35, 42, 52))
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
        val candidates = (offlineWords[language].orEmpty() + personalWords)
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
                    gravity = Gravity.TOP or Gravity.END
                    setPadding(0, dp(1), dp(3), 0)
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
                dp(10)
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
            setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(45, 52, 62))
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = roundedBackground(
                theme.special,
                dp(10)
            )
            elevation = dp(4).toFloat()
            letterSpacing = 0.01f
        }

        if (label == "Space") {
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

        val candidates = (offlineWords[language].orEmpty() + personalWords).distinct()
        val prefixMatches = candidates.filter {
            it.startsWith(currentWord, ignoreCase = true) &&
                !it.equals(currentWord, ignoreCase = true)
        }
        if (prefixMatches.isNotEmpty()) return prefixMatches.take(3)

        if (currentWord.length < 4) return emptyList()
        return candidates
            .map { word -> word to editDistanceAtMostOne(currentWord.lowercase(), word.lowercase()) }
            .filter { it.second == 1 }
            .sortedBy { it.second }
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
        val variation = editorType and android.text.InputType.TYPE_MASK_VARIATION
        val savedEmail = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getString("preferredEmail", "").orEmpty()
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && savedEmail.isNotBlank()) {
            val emailChip = TextView(this).apply {
                text = "✉ $savedEmail"; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38,45,55))
                setPadding(dp(10), dp(3), dp(10), dp(3)); background = roundedBackground(theme.keyColors[0], dp(8))
                setOnClickListener { currentInputConnection?.commitText(savedEmail, 1); updateSuggestions() }
            }
            container.addView(emailChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))
            return
        }
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val clipText = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        val otp = Regex("(?<!\\d)\\d{4,8}(?!\\d)").find(clipText)?.value
        if (otp != null && (editorType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER) {
            val otpChip = TextView(this).apply {
                text = "OTP: $otp"; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (themeIndex == 1) Color.WHITE else Color.rgb(38,45,55))
                setOnClickListener { currentInputConnection?.commitText(otp, 1); updateSuggestions() }
            }
            container.addView(otpChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))
            return
        }
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
            connection.commitText("", 1)
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

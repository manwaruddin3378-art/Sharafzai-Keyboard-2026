
package com.sharafzai.keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
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
import android.util.Log

class KeyboardService : InputMethodService() {

    private var language = 0
    private var shifted = false
    private var caps = false
    private var lastShiftTapAt = 0L
    private var themeIndex = 0
    private var keySizeLevel = 1
    private var suggestionContainer: LinearLayout? = null
    private var emojiPanelVisible = false
    private var symbolsPanelVisible = false
    private var settingsPanelVisible = false
    private var phrasePanelVisible = false
    private var phraseDialog: AlertDialog? = null
    private var phraseCategory = 0
    private var phraseIndex = 0
    private var clipboardPanelVisible = false
    private var emojiCategory = 0
    private var emojiPage = 0
    private var symbolsCategory = 0
    private var fontStyleIndex = 0
    private var numberRowEnabled = true
    private var nativeDigitsEnabled = true
    private var autoCorrectEnabled = true
    private var hapticEnabled = true
    private var soundEnabled = false
    private var pendingVoiceText = ""
    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val recognizedText = intent
                ?.getStringExtra(VoiceInputActivity.EXTRA_RECOGNIZED_TEXT)
                .orEmpty()
            if (recognizedText.isBlank() || isRestrictedEditorField()) return

            // The recognizer activity returns while the IME connection is usually still active.
            // Commit immediately when possible; retain the text only as a lifecycle fallback.
            val connection = currentInputConnection
            val committed = connection != null &&
                runCatching { connection.commitText(recognizedText, 1) }.getOrDefault(false)
            if (committed) {
                pendingVoiceText = ""
                updateSuggestions()
            } else {
                pendingVoiceText = recognizedText
            }
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        Log.e("SUNDUS_IME_LIFECYCLE", "KeyboardService onCreate started")
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
        if (!prefs.getBoolean("compactLayoutApplied", false)) {
            prefs.edit()
                .putBoolean("numberRowEnabled", false)
                .putBoolean("compactLayoutApplied", true)
                .apply()
        }
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
        Log.e("SUNDUS_IME_LIFECYCLE", "KeyboardService onCreate completed")
    }

    override fun onDestroy() {
        Log.e("SUNDUS_IME_LIFECYCLE", "KeyboardService onDestroy")
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

    // Expanded offline vocabulary for the four supported languages.
    // All entries are bundled locally; no network access or typing-history collection is used.
    private val extendedOfflineWords = mapOf(
        0 to listOf(
            "افغانستان","کابل","جلال","ننګرهار","لغمان","کونړ","کندهار","هرات","بلخ","پکتیا",
            "پکتیکا","خوست","فراه","بادغیس","بامیان","دایکندی","غزني","وردګ","لوګر","پنجشېر",
            "اسلامي","فقه","حدیث","تفسیر","عقیده","شریعت","حلال","حرام","عبادت","لمونځ",
            "روژه","زکات","حج","عمره","مسجد","مدرسه","علما","عالمان","طالب","زده‌کوونکی",
            "ښوونه","روزنه","تعلیم","تحصیل","څېړنه","مقاله","رساله","کتابتون","لیکوال","ژباړه",
            "اقتصاد","بانک","معامله","تجارت","بازرګاني","پانګه","پانګونه","ګټه","تاوان","قرارداد",
            "اجاره","مرابحه","مشارکت","مضاربت","استصناع","سلم","وکالت","کفالت","ضمانت","ملکیت",
            "قانون","حقوق","عدالت","محکمه","قاضي","فتوا","مفتي","فقهي","حنفي","مذهب",
            "پوهه","فکر","کلتور","ټولنه","کورنۍ","ملګرتیا","درناوی","باور","رښتیا","مسؤولیت",
            "پرمختګ","بریالیتوب","هڅه","زحمت","خدمت","مرسته","همکاري","اتحاد","سوله","امنیت",
            "روغتیا","درملنه","ډاکټر","روغتون","ښوونځی","پوهنتون","ټکنالوژي","کمپیوټر","انټرنېټ","معلومات"
        ),
        1 to listOf(
            "أفغانستان","كابل","جلال آباد","قندهار","هرات","مزار","ننګرهار","إسلام","فقه","حديث",
            "تفسير","عقيدة","شريعة","حلال","حرام","عبادة","صلاة","صوم","زكاة","حج",
            "عمرة","مسجد","مدرسة","جامعة","علماء","طالب","تعليم","تعلم","بحث","دراسة",
            "رسالة","مقالة","مكتبة","كاتب","ترجمة","اقتصاد","مصرف","بنك","معاملة","تجارة",
            "استثمار","رأس","مال","ربح","خسارة","عقد","إجارة","مرابحة","مشاركة","مضاربة",
            "استصناع","سلم","وكالة","كفالة","ضمان","ملكية","قانون","حقوق","عدالة","محكمة",
            "قاضي","فتوى","مفتي","حنفي","مذهب","لغة","كلمة","جملة","كتابة","قراءة",
            "مدير","موظف","عمل","وظيفة","خبرة","مهارة","مشروع","إدارة","تنمية","تطوير",
            "صحة","طبيب","مستشفى","عائلة","أسرة","أب","أم","أخ","أخت","صباح",
            "مساء","اليوم","غدا","أمس","الآن","دائما","أحيانا","مساعدة","تعاون","احترام",
            "مسؤولية","نجاح","توفيق","سلام","أمن","وطن","شعب","مجتمع","ثقافة","تقنية"
        ),
        2 to listOf(
            "about","above","across","action","activity","actually","address","advice","afghanistan","after",
            "again","against","agreement","already","always","answer","application","area","around","article",
            "available","beautiful","because","before","between","business","calendar","change","children","clear",
            "college","community","complete","computer","continue","correct","country","create","culture","customer",
            "database","decision","different","dictionary","development","education","email","employee","english",
            "enough","environment","especially","example","excellent","experience","family","finance","financial",
            "follow","friendship","future","general","government","graduate","greatest","guidance","history","however",
            "important","improve","information","international","Islamic","keyboard","knowledge","language","learning",
            "library","management","meaning","message","mobile","modern","money","morning","necessary","network",
            "office","opportunity","organization","original","people","personal","phone","please","practice","prepare",
            "problem","professional","project","quality","question","research","respect","responsibility","right",
            "school","science","security","sentence","service","simple","solution","special","student","success",
            "support","system","technology","thanks","together","training","translation","university","useful","voice",
            "welcome","wonderful","working","writing","yesterday","tomorrow"
        ),
        3 to listOf(
            "افغانستان","کابل","جلال‌آباد","قندهار","هرات","مزارشریف","ننگرهار","اسلام","فقه","حدیث",
            "تفسیر","عقیده","شریعت","حلال","حرام","عبادت","نماز","روزه","زکات","حج",
            "عمره","مسجد","مدرسه","دانشگاه","عالمان","طالب","آموزش","تعلیم","یادگیری","پژوهش",
            "تحقیق","رساله","مقاله","کتابخانه","نویسنده","ترجمه","اقتصاد","بانک","معامله","تجارت",
            "سرمایه","سرمایه‌گذاری","سود","زیان","قرارداد","اجاره","مرابحه","مشارکت","مضاربت","استصناع",
            "سلم","وکالت","کفالت","ضمانت","مالکیت","قانون","حقوق","عدالت","محکمه","قاضی",
            "فتوا","مفتی","حنفی","مذهب","زبان","واژه","جمله","نوشتن","خواندن","مدیر",
            "کارمند","کار","وظیفه","تجربه","مهارت","پروژه","مدیریت","توسعه","پیشرفت","سلامتی",
            "پزشک","بیمارستان","خانواده","پدر","مادر","برادر","خواهر","صبح","شام","امروز",
            "فردا","دیروز","اکنون","همیشه","گاهی","کمک","همکاری","احترام","مسؤولیت","موفقیت",
            "آرامش","صلح","امنیت","وطن","مردم","جامعه","فرهنگ","فناوری","کمپیوتر","معلومات"
        )
    )

    private fun offlineWordsFor(languageId: Int): List<String> =
        (offlineWords[languageId].orEmpty() + extendedOfflineWords[languageId].orEmpty())
            .distinct()

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
        k("\u200C", "ٔ", "€", "ـ"),
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
        Log.e("SUNDUS_IME_LIFECYCLE", "onCreateInputView started")
        val view = buildKeyboard()
        Log.e("SUNDUS_IME_LIFECYCLE", "onCreateInputView completed")
        return view
    }

    // Keep the IME docked above the app content, including landscape mode.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(
        info: EditorInfo,
        restarting: Boolean
    ) {
        Log.e("SUNDUS_IME_LIFECYCLE", "onStartInputView restarting=$restarting inputType=${info.inputType}")
        super.onStartInputView(info, restarting)
        if (pendingVoiceText.isNotBlank()) {
            if (isRestrictedEditorField()) {
                pendingVoiceText = ""
            } else {
                val connection = currentInputConnection
                if (connection != null &&
                    runCatching { connection.commitText(pendingVoiceText, 1) }.getOrDefault(false)
                ) {
                    pendingVoiceText = ""
                }
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

    private val adhkar = listOf(
        "سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللَّهُ",
        "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "سُبْحَانَ اللَّهِ الْعَظِيمِ",
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "حَسْبِيَ اللَّهُ وَنِعْمَ الْوَكِيلُ",
        "لَا إِلَٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ",
        "رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ إِنَّكَ أَنْتَ التَّوَّابُ الرَّحِيمُ",
        "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ",
        "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
        "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا",
        "اللَّهُمَّ اغْفِرْ لِي وَارْحَمْنِي وَاهْدِنِي وَعَافِنِي وَارْزُقْنِي",
        "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ",
        "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ",
        "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ",
        "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْجَنَّةَ وَأَعُوذُ بِكَ مِنَ النَّارِ",
        "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ",
        "سُبْحَانَ اللَّهِ وَالْحَمْدُ لِلَّهِ وَلَا إِلَٰهَ إِلَّا اللَّهُ وَاللَّهُ أَكْبَرُ",
        "اللَّهُمَّ اهْدِنِي وَسَدِّدْنِي", "رَبِّ زِدْنِي عِلْمًا",
        "اللَّهُمَّ بَارِكْ لَنَا فِيمَا رَزَقْتَنَا",
        "اللَّهُمَّ تَقَبَّلْ مِنَّا إِنَّكَ أَنْتَ السَّمِيعُ الْعَلِيمُ",
        "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ",
        "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ",
        "اللَّهُمَّ اكْفِنِي بِحَلَالِكَ عَنْ حَرَامِكَ",
        "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلَى دِينِكَ",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا وَرِزْقًا طَيِّبًا",
        "الْحَمْدُ لِلَّهِ عَلَى كُلِّ حَالٍ",
        "اللَّهُمَّ أَجِرْنِي مِنَ النَّارِ",
        "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ الَّذِي لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ"
    )
    private val dhikrColors = listOf(
        Color.rgb(255, 226, 125), Color.rgb(142, 232, 255),
        Color.rgb(177, 255, 190), Color.rgb(255, 174, 210),
        Color.rgb(211, 190, 255), Color.rgb(255, 195, 139)
    )
    private var dhikrIndex = 0
    private var dhikrEnterFromRight = true
    private val dhikrTicker = object : Runnable {
        override fun run() {
            val view = dhikrView
            if (view == null || !view.isAttachedToWindow) {
                handler.postDelayed(this, 3500)
                return
            }
            // Slide the current remembrance away, then bring the next one in
            // from the opposite side with its own contrasting text/background colors.
            val width = view.width.toFloat().coerceAtLeast(dp(80).toFloat())
            val outgoing = if (dhikrEnterFromRight) -width else width
            val incoming = if (dhikrEnterFromRight) width else -width
            view.animate().cancel()
            view.animate()
                .alpha(0f)
                .translationX(outgoing)
                .setDuration(260L)
                .withEndAction {
                    dhikrIndex = (dhikrIndex + 1) % adhkar.size
                    view.text = adhkar[dhikrIndex]
                    view.setTextColor(dhikrColors[dhikrIndex % dhikrColors.size])
                    val base = dhikrColors[dhikrIndex % dhikrColors.size]
                    val darker = Color.rgb(
                        (Color.red(base) * 0.30f).toInt(),
                        (Color.green(base) * 0.30f).toInt(),
                        (Color.blue(base) * 0.30f).toInt()
                    )
                    view.background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(darker, Color.rgb(7, 35, 62), darker)
                    ).apply { cornerRadius = dp(9).toFloat() }
                    view.translationX = incoming
                    view.alpha = 0f
                    view.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(420L)
                        .start()
                    dhikrEnterFromRight = !dhikrEnterFromRight
                }
                .start()
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

        val dhikrStrip = TextView(this).apply {
            text = adhkar[dhikrIndex]
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(dhikrColors[dhikrIndex % dhikrColors.size])
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(3))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(5, 43, 59), Color.rgb(20, 48, 92), Color.rgb(5, 43, 59))
            ).apply { cornerRadius = dp(9).toFloat() }
        }
        dhikrView = dhikrStrip
        root.addView(dhikrStrip, rowParams(27))
        handler.removeCallbacks(dhikrTicker)
        handler.postDelayed(dhikrTicker, 3500)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(3, 74, 91), Color.rgb(8, 35, 75))
            ).apply { cornerRadius = dp(11).toFloat() }
        }

        val brandLogo = TextView(this).apply {
            text = "☾\nسندس\nSUNDUS"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 218, 116))
            typeface = Typeface.create("serif", Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 31, 48), Color.rgb(1, 15, 31))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.rgb(226, 181, 67))
            }
            contentDescription = "SUNDUS سندس"
        }
        header.addView(brandLogo, LinearLayout.LayoutParams(dp(52), dp(50)))

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
            ImeDialogBuilder()
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
            symbolsPanelVisible = false
            phrasePanelVisible = false
            settingsPanelVisible = false
            clipboardPanelVisible = false
            refreshKeyboard()
        }
        addSpecial(header, "☪\nجملې", 0.9f, 34) {
            emojiPanelVisible = false
            symbolsPanelVisible = false
            settingsPanelVisible = false
            clipboardPanelVisible = false
            showPhraseWindow()
        }
        addSpecial(header, "🎨", 0.75f, 29) { showThemePicker() }
        addSpecial(header, "⚙", 0.75f, 29) {
            settingsPanelVisible = !settingsPanelVisible
            if (settingsPanelVisible) {
                emojiPanelVisible = false
                symbolsPanelVisible = false
                clipboardPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }
        root.addView(header, rowParams(40))

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
            languageStrip.addView(tab, LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }


        val topTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addSpecial(topTools, "📋", 1f, 32) {
            clipboardPanelVisible = !clipboardPanelVisible
            if (clipboardPanelVisible) {
                emojiPanelVisible = false
                symbolsPanelVisible = false
                settingsPanelVisible = false
                phrasePanelVisible = false
            }
            refreshKeyboard()
        }
        addSpecial(topTools, "⭐ متن", 1f, 32) { showAutoTextEditor() }
        addSpecial(topTools, "🌐", 1f, 32) { showOfflineTranslationPicker() }
        addSpecial(topTools, "📖", 1f, 32) { showDictionaryHome() }
        addSpecial(topTools, "🔎", 1f, 32) { showUniversalSearchDialog() }
        addSpecial(topTools, "🎙", 1f, 32) {
            if (isRestrictedEditorField()) {
                Toast.makeText(this, "په پټنوم او ځانګړو خانو کې غږیز لیکل بند دي.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, VoiceInputActivity::class.java)
                    .putExtra("language", language)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        addSpecial(topTools, "🎨", 1f, 32) { showThemePicker() }

        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(theme.suggestion)
        }

        root.addView(topTools, rowParams(32))
        root.addView(languageStrip, rowParams(30))

        root.addView(suggestionContainer, rowParams(26))

        if (!emojiPanelVisible && !clipboardPanelVisible && !symbolsPanelVisible && !settingsPanelVisible) {
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



        if (symbolsPanelVisible) {
            addSymbolsPanel(root)
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
            gravity = Gravity.CENTER
        }

        // Three equal visual zones keep the SUNDUS space key exactly centered.
        val leftZone = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addSpecial(leftZone, if (symbolsPanelVisible) "ABC" else "123", 1f, 44) {
            symbolsPanelVisible = !symbolsPanelVisible
            if (symbolsPanelVisible) {
                emojiPanelVisible = false
                clipboardPanelVisible = false
                settingsPanelVisible = false
                phrasePanelVisible = false
                symbolsCategory = 0
            }
            refreshKeyboard()
        }

        val centerZone = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addSpecial(centerZone, "✦ SUNDUS | سندس ✦", 1f, 44) { expandAutoTextAndSpace() }

        val rightZone = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addSpecial(rightZone, "⌄", 0.8f, 44) { requestHideSelf(0) }
        addSpecial(rightZone, if (language == 2) "," else "،", 0.55f, 44) {
            currentInputConnection?.commitText(if (language == 2) ", " else "، ", 1)
            updateSuggestions()
        }
        addSpecial(rightZone, ".", 0.55f, 44) {
            currentInputConnection?.commitText(". ", 1)
            updateSuggestions()
        }
        addSpecial(rightZone, "↵", 0.8f, 44) { performEnterAction() }

        bottom.addView(leftZone, LinearLayout.LayoutParams(0, dp(46), 1f))
        bottom.addView(centerZone, LinearLayout.LayoutParams(0, dp(46), 2.4f))
        bottom.addView(rightZone, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(bottom, rowParams(46))

        return root
    }

    // Attach every toolbar dialog to the actual IME window. Prefer the token stored
    // in the IME Window attributes; decorView.windowToken can be null during early
    // view creation on some Android builds, which previously left dialogs detached.
    private fun attachDialogToIme(dialog: AlertDialog): AlertDialog {
        val dialogWindow = dialog.window ?: return dialog
        val imeWindow = this@KeyboardService.window?.window
        val imeToken = imeWindow?.attributes?.token
            ?: imeWindow?.decorView?.windowToken
        val params = dialogWindow.attributes
        params.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        if (imeToken != null) {
            params.token = imeToken
        } else {
            android.util.Log.w("SundusIME", "IME window token is not available; dialog may not attach")
        }
        dialogWindow.attributes = params
        dialogWindow.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        return dialog
    }

    private inner class ImeDialogBuilder : AlertDialog.Builder(this@KeyboardService) {
        override fun create(): AlertDialog = attachDialogToIme(super.create())
    }

    private fun showThemePicker() {
        val names = listOf(
            "سندس اصلي", "شپه", "سمندري", "زرغون", "بنفش", "ګلابي", "طلایي", "یاقوتي",
            "فیروزه", "لاجوردي", "زمردي", "سپوږمۍ", "لمر", "آسماني", "باراني", "ځنګل",
            "ګلستان", "مرجاني", "ارغواني", "نقره", "برونزي", "شفق", "یخ", "اور",
            "شګه", "کاکاو", "نعنا", "لیمو", "انګور", "ګلاب", "مرجان", "دود",
            "شاهي", "مخمل", "کریستال", "سپین زر", "تور الماس", "زمرد شپه", "طلایي شفق",
            "سندس ځانګړی", "لاجوردي شپه", "شنه زمرد", "ګلابي سپوږمۍ", "شاهي ارغواني",
            "د صحرا لمر", "یخي آبي", "مروارید", "تور شفق"
        )
        ImeDialogBuilder()
            .setTitle("د سندس رنګونه")
            .setSingleChoiceItems(names.toTypedArray(), themeIndex) { dialog, which ->
                themeIndex = which.coerceIn(themes.indices)
                saveSettings()
                dialog.dismiss()
                refreshKeyboard()
            }
            .setNegativeButton("بند", null)
            .show()
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

    private fun builtInDictionaryEntries(languageId: Int): List<Pair<String, String>> {
        return try {
            assets.open("dictionary_seed.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { row ->
                    val parts = row.split("|", limit = 5)
                    if (parts.size == 5 && languageId in 0..3 && parts[languageId].isNotBlank()) {
                        parts[languageId].trim() to parts[4].trim()
                    } else null
                }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun dictionaryEntries(languageId: Int = language): List<Pair<String, String>> {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val hidden = prefs.getStringSet("dictionaryHidden_$languageId", emptySet()).orEmpty()
        val builtIn = builtInDictionaryEntries(languageId).filterNot { it.first in hidden }
        val personal = prefs.getStringSet("dictionaryEntries_$languageId", emptySet()).orEmpty()
            .mapNotNull { row ->
                val parts = row.split("|||", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
            }
        return (builtIn + personal).associateBy { it.first }.values
            .sortedBy { it.first.lowercase() }
    }

    private fun showDictionaryHome() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val autoLearn = prefs.getBoolean("autoLearnWords", true)
        ImeDialogBuilder()
            .setTitle("قاموس او لټون")
            .setMessage("اتومات زده کړه یوازې بشپړې کلمې په همدې موبایل کې ساتي؛ بشپړ متن نه ذخیره کوي. اوسنی حالت: ${if (autoLearn) "فعاله" else "بنده"}")
            .setItems(arrayOf("🔎 د کلمو لټون", "＋ نوې کلمه او مانا", "📖 شخصي قاموس اداره کول", if (autoLearn) "اتومات زده کړه بنده کړئ" else "اتومات زده کړه فعاله کړئ")) { _, which ->
                when (which) {
                    0 -> showDictionarySearchDialog()
                    1 -> showAddDictionaryEntryDialog()
                    2 -> showPersonalDictionaryEditor()
                    3 -> {
                        val enabled = !prefs.getBoolean("autoLearnWords", true)
                        prefs.edit().putBoolean("autoLearnWords", enabled).commit()
                        Toast.makeText(this, if (enabled) "اتومات زده کړه فعاله شوه" else "اتومات زده کړه بنده شوه", Toast.LENGTH_SHORT).show()
                        showDictionaryHome()
                    }
                }
            }
            .setNegativeButton("بند", null)
            .show()
    }

    // Search is local-only and covers vocabulary and user-saved material.
    private fun showUniversalSearchDialog() {
        val input = EditText(this).apply {
            hint = "کلمه، جمله، ژباړه یا لنډیز ولټوئ"
            setSingleLine(true)
        }
        ImeDialogBuilder()
            .setTitle("د سندس آفلاین لټون")
            .setMessage("په قاموس، شخصي جملو، ژباړو، AutoText او خوندي شوو متنونو کې لټون")
            .setView(input)
            .setNegativeButton("بند", null)
            .setPositiveButton("لټون") { _, _ ->
                val query = normalizeSuggestionText(input.text.toString().trim())
                if (query.isBlank()) return@setPositiveButton
                val results = mutableListOf<Triple<String, String, String>>()
                val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                val meanings = dictionaryEntries().toMap()
                (offlineWordsFor(language) + prefs.getStringSet("personalWords_$language", emptySet()).orEmpty() + meanings.keys)
                    .distinct().forEach { word ->
                        val meaning = meanings[word].orEmpty()
                        if (normalizeSuggestionText(word).contains(query) ||
                            normalizeSuggestionText(meaning).contains(query)) {
                            results.add(Triple("قاموس", if (meaning.isBlank()) word else "$word — $meaning", word))
                        }
                    }
                prefs.getStringSet("savedPhrases", emptySet()).orEmpty().forEach { phrase ->
                    if (normalizeSuggestionText(phrase).contains(query)) results.add(Triple("جمله", phrase, phrase))
                }
                (0..3).filter { it != language }.forEach { target ->
                    prefs.getStringSet("translationPairs_${language}_${target}", emptySet()).orEmpty().forEach { pair ->
                        val parts = pair.split("|||", limit = 2)
                        if (parts.size == 2 && (normalizeSuggestionText(parts[0]).contains(query) ||
                            normalizeSuggestionText(parts[1]).contains(query))) {
                            results.add(Triple("ژباړه", "${parts[0]} → ${parts[1]}", parts[1]))
                        }
                    }
                }
                prefs.getStringSet("autoText_$language", emptySet()).orEmpty().forEach { row ->
                    val parts = row.split("|||", limit = 2)
                    if (parts.size == 2 && (normalizeSuggestionText(parts[0]).contains(query) ||
                        normalizeSuggestionText(parts[1]).contains(query))) {
                        results.add(Triple("AutoText", "${parts[0]} → ${parts[1]}", parts[1]))
                    }
                }
                prefs.getStringSet("clipboardFavorites", emptySet()).orEmpty().forEach { saved ->
                    if (normalizeSuggestionText(saved).contains(query)) results.add(Triple("خوندي شوی متن", saved, saved))
                }
                val unique = results.distinctBy { it.second }.take(100)
                if (unique.isEmpty()) {
                    Toast.makeText(this, "د لټون پایله ونه موندل شوه.", Toast.LENGTH_LONG).show()
                } else {
                    ImeDialogBuilder()
                        .setTitle("د لټون پایلې: ${unique.size}")
                        .setItems(unique.map { "${it.first}: ${it.second}" }.toTypedArray()) { _, index ->
                            val insert = unique[index].third
                            currentInputConnection?.commitText(insert + " ", 1)
                            updateSuggestions()
                        }
                        .setNegativeButton("بند", null)
                        .show()
                }
            }
            .show()
    }

    private fun showPersonalDictionaryEditor() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val languageName = listOf("پښتو", "عربي", "English", "دري")[language.coerceIn(0, 3)]
        fun showList() {
            val entries = dictionaryEntries()
            val words = prefs.getStringSet("personalWords_$language", emptySet()).orEmpty()
            val allTerms = (entries.map { it.first } + words).distinct().sorted()
            val labels = allTerms.map { term ->
                val meaning = entries.firstOrNull { it.first == term }?.second.orEmpty()
                if (meaning.isBlank()) term else "$term  —  $meaning"
            }
            ImeDialogBuilder()
                .setTitle("شخصي قاموس – $languageName (${allTerms.size})")
                .setMessage(if (allTerms.isEmpty()) "تر اوسه کلمه نه ده ثبت شوې. نوې کلمه له مانا سره اضافه کړئ." else "د یوې کلمې پر نوم کلیک وکړئ چې مانا یې وګورئ، اصلاح یا حذف یې کړئ.")
                .setItems(labels.toTypedArray()) { _, which ->
                    val term = allTerms[which]
                    val meaning = entries.firstOrNull { it.first == term }?.second.orEmpty()
                    ImeDialogBuilder()
                        .setTitle(term)
                        .setMessage(meaning.ifBlank { "د دې کلمې مانا لا نه ده ثبت شوې." })
                        .setPositiveButton("کلمه ولیکئ") { _, _ ->
                            currentInputConnection?.commitText("$term ", 1)
                            updateSuggestions()
                        }
                        .setNeutralButton("سمون") { _, _ ->
                            showAddDictionaryEntryDialog(term, meaning)
                        }
                        .setNegativeButton("حذف") { _, _ ->
                            ImeDialogBuilder()
                                .setTitle("کلمه حذف کړئ؟")
                                .setMessage("«$term» له شخصي قاموس څخه حذف شي؟")
                                .setNegativeButton("نه", null)
                                .setPositiveButton("هو، حذف") { _, _ ->
                                    val updatedWords = prefs.getStringSet("personalWords_$language", emptySet())
                                        ?.toMutableSet() ?: mutableSetOf()
                                    updatedWords.remove(term)
                                    prefs.edit().putStringSet("personalWords_$language", HashSet(updatedWords)).commit()
                                    val updatedEntries = prefs.getStringSet("dictionaryEntries_$language", emptySet())
                                        ?.toMutableSet() ?: mutableSetOf()
                                    updatedEntries.removeAll { it.substringBefore("|||") == term }
                                    val hidden = prefs.getStringSet("dictionaryHidden_$language", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    hidden.add(term)
                                    prefs.edit()
                                        .putStringSet("dictionaryEntries_$language", HashSet(updatedEntries))
                                        .putStringSet("dictionaryHidden_$language", HashSet(hidden))
                                        .commit()
                                    updateSuggestions()
                                    showList()
                                }.show()
                        }
                        .show()
                }
                .setPositiveButton("＋ نوې کلمه") { _, _ -> showAddDictionaryEntryDialog() }
                .setNeutralButton("لټون") { _, _ -> showDictionarySearchDialog() }
                .setNegativeButton("بند", null)
                .show()
        }
        showList()
    }

    private fun showAddDictionaryEntryDialog(existingTerm: String = "", existingMeaning: String = "") {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val termInput = EditText(this).apply {
            hint = "کلمه یا اصطلاح"
            setSingleLine(true)
            setText(existingTerm)
        }
        val meaningInput = EditText(this).apply {
            hint = "د کلمې مانا او تشریح"
            minLines = 2
            setSingleLine(false)
            setText(existingMeaning)
        }
        content.addView(termInput)
        content.addView(meaningInput)
        val dialog = ImeDialogBuilder()
            .setTitle(if (existingTerm.isBlank()) "قاموس ته نوې کلمه اضافه کړئ" else "د قاموس کلمه اصلاح کړئ")
            .setMessage("کلمه او مانا یې ولیکئ؛ معلومات په همدې موبایل کې ساتل کېږي.")
            .setView(content)
            .setNegativeButton("بند", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val term = termInput.text.toString().trim()
                val meaning = meaningInput.text.toString().trim()
                if (term.isEmpty() || meaning.isEmpty()) {
                    Toast.makeText(this, "کلمه او مانا دواړه ولیکئ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                val words = prefs.getStringSet("personalWords_$language", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (existingTerm.isNotBlank()) words.remove(existingTerm)
                words.add(term)
                val entries = prefs.getStringSet("dictionaryEntries_$language", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (existingTerm.isNotBlank()) entries.removeAll { it.substringBefore("|||") == existingTerm }
                entries.removeAll { it.substringBefore("|||") == term }
                entries.add("$term|||$meaning")
                val hidden = prefs.getStringSet("dictionaryHidden_$language", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (existingTerm.isNotBlank() && existingTerm != term) hidden.add(existingTerm)
                hidden.remove(term)
                val saved = prefs.edit()
                    .putStringSet("personalWords_$language", HashSet(words))
                    .putStringSet("dictionaryEntries_$language", HashSet(entries))
                    .putStringSet("dictionaryHidden_$language", HashSet(hidden))
                    .commit()
                if (saved) {
                    dialog.dismiss()
                    Toast.makeText(this, "کلمه او مانا خوندي شول", Toast.LENGTH_SHORT).show()
                    updateSuggestions()
                } else {
                    Toast.makeText(this, "ذخیره ناکامه شوه؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
                }
            }
        }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun showDictionarySearchDialog() {
        val input = EditText(this).apply {
            hint = "کلمه یا د مانا یوه برخه ولیکئ"
            setSingleLine(true)
        }
        ImeDialogBuilder()
            .setTitle("په آفلاین شخصي قاموس کې لټون")
            .setView(input)
            .setNegativeButton("بند", null)
            .setPositiveButton("لټون") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    val personal = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                        .getStringSet("personalWords_$language", emptySet()).orEmpty()
                    val entries = dictionaryEntries()
                    // Built-in seed definitions are authored in Pashto for every vocabulary column.
                    // Label them explicitly in non-Pashto layouts so they are not mistaken for
                    // Arabic, English, or Dari definitions. User-authored entries keep their label.
                    val builtInTerms = builtInDictionaryEntries(language).mapTo(hashSetOf()) { it.first }
                    val matches = (offlineWordsFor(language) + personal + entries.map { it.first })
                        .distinct()
                        .filter { term ->
                            normalizeSuggestionText(term).contains(normalizeSuggestionText(query)) ||
                                entries.firstOrNull { it.first == term }?.second?.let {
                                    normalizeSuggestionText(it).contains(normalizeSuggestionText(query))
                                } == true
                        }
                        .take(80)
                    if (matches.isEmpty()) {
                        Toast.makeText(this, "پایله ونه موندل شوه؛ خپله کلمه او مانا قاموس ته اضافه کړئ.", Toast.LENGTH_LONG).show()
                    } else {
                        ImeDialogBuilder()
                            .setTitle("د لټون پایلې (${matches.size})")
                            .setItems(matches.map { term ->
                                val meaning = entries.firstOrNull { it.first == term }?.second.orEmpty()
                                val label = if (language != 0 && term in builtInTerms) "پښتو تشریح: " else ""
                                if (meaning.isBlank()) term else "$term — $label$meaning"
                            }.toTypedArray()) { _, which ->
                                val term = matches[which]
                                val meaning = entries.firstOrNull { it.first == term }?.second.orEmpty()
                                val label = if (language != 0 && term in builtInTerms) "پښتو تشریح: " else ""
                                ImeDialogBuilder()
                                    .setTitle(term)
                                    .setMessage(if (meaning.isBlank()) "د دې کلمې مانا په شخصي قاموس کې نه ده ثبت شوې." else label + meaning)
                                    .setPositiveButton("کلمه ولیکئ") { _, _ ->
                                        currentInputConnection?.commitText("$term ", 1)
                                        updateSuggestions()
                                    }
                                    .setNegativeButton("بند", null)
                                    .show()
                            }
                            .setNegativeButton("بند", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showOfflineTranslationPicker() {
        val names = arrayOf("پښتو", "عربي", "English", "دري")
        val ids = intArrayOf(0, 1, 2, 3)
        val targets = ids.filter { it != language }.toIntArray()
        val labels = targets.map { names[it] }.toTypedArray()
        ImeDialogBuilder()
            .setTitle("د ژباړې هدف ژبه وټاکئ")
            .setItems(labels) { _, which -> showOfflineTranslationEditor(targets[which]) }
            .setNegativeButton("بند", null)
            .show()
    }

    // Personal translation memory works fully offline; it does not pretend to be machine translation.
    private fun showOfflineTranslationEditor(targetLanguage: Int) {
        val languageNames = listOf("پښتو", "عربي", "English", "دري")
        val source = EditText(this).apply {
            hint = "متن یا جمله ولیکئ"
            minLines = 2
            setSingleLine(false)
        }
        val key = "translationPairs_${language}_$targetLanguage"
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        fun savedPairs(): MutableSet<String> =
            prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

        val dialog = ImeDialogBuilder()
            .setTitle("آفلاین شخصي ژباړن: ${languageNames[language]} ← ${languageNames[targetLanguage]}")
            .setMessage("خپلې ژباړې ثبت او وروسته یې ولټوئ. دا برخه اتومات ماشیني ژباړه نه ده.")
            .setView(source)
            .setPositiveButton("ژباړه ولټوه", null)
            .setNeutralButton("＋ نوې ژباړه", null)
            .setNegativeButton("ژباړې اداره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = source.text.toString().trim()
                if (query.isEmpty()) {
                    Toast.makeText(this, "لومړی متن ولیکئ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val match = savedPairs().mapNotNull { entry ->
                    val parts = entry.split("|||", limit = 2)
                    if (parts.size == 2 && normalizeSuggestionText(parts[0]) == normalizeSuggestionText(query)) parts[1] else null
                }.firstOrNull()
                dialog.dismiss()
                if (match != null) {
                    ImeDialogBuilder().setTitle("ژباړه").setMessage(match)
                        .setPositiveButton("سمه ده", null).show()
                } else {
                    Toast.makeText(this, "دا متن لا نه دی ثبت شوی؛ نوې ژباړه یې اضافه کړئ.", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val original = source.text.toString().trim()
                if (original.isEmpty()) {
                    Toast.makeText(this, "لومړی اصلي متن ولیکئ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                val translated = EditText(this).apply {
                    hint = "ژباړل شوی متن په ${languageNames[targetLanguage]} ولیکئ"
                    minLines = 2
                    setSingleLine(false)
                }
                val addDialog = ImeDialogBuilder()
                    .setTitle("نوې ژباړه خوندي کړئ")
                    .setView(translated)
                    .setNegativeButton("بند", null)
                    .setPositiveButton("ذخیره", null)
                    .create()
                addDialog.setOnShowListener {
                    addDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = translated.text.toString().trim()
                        if (value.isEmpty()) {
                            Toast.makeText(this, "ژباړه هم ولیکئ", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val pairs = savedPairs()
                        pairs.removeAll { it.substringBefore("|||") == original }
                        pairs.add("$original|||$value")
                        if (prefs.edit().putStringSet(key, HashSet(pairs)).commit()) {
                            addDialog.dismiss()
                            Toast.makeText(this, "ژباړه خوندي شوه", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "ژباړه خوندي نه شوه؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                attachDialogToIme(addDialog)
                addDialog.show()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                showSavedTranslationsManager(targetLanguage)
            }
        }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun showSavedTranslationsManager(targetLanguage: Int) {
        val key = "translationPairs_${language}_$targetLanguage"
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        fun readPairs(): List<Pair<String, String>> =
            prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { row ->
                val parts = row.split("|||", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.sortedBy { it.first.lowercase() }
        fun showList() {
            val pairs = readPairs()
            ImeDialogBuilder()
                .setTitle("خوندي شوې ژباړې: ${pairs.size}")
                .setItems(pairs.map { "${it.first}  →  ${it.second}" }.toTypedArray()) { _, which ->
                    val pair = pairs[which]
                    ImeDialogBuilder()
                        .setTitle(pair.first)
                        .setMessage(pair.second)
                        .setPositiveButton("ژباړه ولیکئ") { _, _ ->
                            currentInputConnection?.commitText(pair.second + " ", 1)
                            updateSuggestions()
                        }
                        .setNeutralButton("سمون") { _, _ ->
                            showEditTranslationDialog(targetLanguage, pair.first, pair.second)
                        }
                        .setNegativeButton("حذف") { _, _ ->
                            ImeDialogBuilder()
                                .setTitle("ژباړه حذف کړئ؟")
                                .setMessage("دا ثبت شوې ژباړه حذف شي؟")
                                .setNegativeButton("نه", null)
                                .setPositiveButton("هو، حذف") { _, _ ->
                                    val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                                    updated.remove("${pair.first}|||${pair.second}")
                                    prefs.edit().putStringSet(key, HashSet(updated)).commit()
                                    showList()
                                }.show()
                        }
                        .show()
                }
                .setPositiveButton("بند", null)
                .show()
        }
        showList()
    }

    private fun showEditTranslationDialog(targetLanguage: Int, oldText: String, oldTranslation: String) {
        val key = "translationPairs_${language}_$targetLanguage"
        val names = listOf("پښتو", "عربي", "English", "دري")
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)) }
        val source = EditText(this).apply { hint = "اصلي متن"; setText(oldText); minLines = 2 }
        val translated = EditText(this).apply { hint = "ژباړل شوی متن (${names[targetLanguage]})"; setText(oldTranslation); minLines = 2 }
        content.addView(source)
        content.addView(translated)
        ImeDialogBuilder()
            .setTitle("د ژباړې اصلاح")
            .setView(content)
            .setNegativeButton("بند", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val original = source.text.toString().trim()
                val result = translated.text.toString().trim()
                if (original.isNotEmpty() && result.isNotEmpty()) {
                    val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                    val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                    updated.remove("${oldText}|||${oldTranslation}")
                    updated.removeAll { it.substringBefore("|||") == original }
                    updated.add("$original|||${result}")
                    prefs.edit().putStringSet(key, HashSet(updated)).commit()
                    Toast.makeText(this, "ژباړه اصلاح او خوندي شوه", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAutoTextEditor() {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        val key = "autoText_$language"
        val rows = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull {
            val parts = it.split("|||", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.sortedBy { it.first }
        val display = if (rows.isEmpty()) "تر اوسه لنډیز نه دی ثبت شوی." else rows.joinToString("\n") { "${it.first} → ${it.second}" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        content.addView(TextView(this).apply {
            text = display
            textSize = 13f
            maxLines = 5
            setPadding(0, 0, 0, dp(6))
        })
        val shortcut = EditText(this).apply { hint = "لنډ کوډ، لکه سلام"; setSingleLine(true) }
        val expansion = EditText(this).apply { hint = "بشپړه جمله یا متن"; minLines = 2 }
        content.addView(shortcut)
        content.addView(expansion)
        val dialog = ImeDialogBuilder()
            .setTitle("شخصي AutoText لنډیزونه")
            .setMessage("لنډ کوډ د Space په وهلو سره بشپړ متن ته پراخېږي. ټول معلومات آفلاین ساتل کېږي.")
            .setView(content)
            .setPositiveButton("＋ ساتل", null)
            .setNeutralButton("ثبت شوي اداره کول", null)
            .setNegativeButton("بند", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = shortcut.text.toString().trim()
                val value = expansion.text.toString().trim()
                if (code.isEmpty() || value.isEmpty()) {
                    Toast.makeText(this, "لنډ کوډ او بشپړ متن دواړه ولیکئ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                updated.removeAll { it.substringBefore("|||") == code }
                updated.add("$code|||$value")
                if (prefs.edit().putStringSet(key, HashSet(updated)).commit()) {
                    dialog.dismiss()
                    Toast.makeText(this, "AutoText لنډیز خوندي شو", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "لنډیز خوندي نه شو؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showAutoTextManager()
            }
        }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun showAutoTextManager() {
        val key = "autoText_$language"
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        fun readRows(): List<Pair<String, String>> =
            prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { entry ->
                val parts = entry.split("|||", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.sortedBy { it.first.lowercase() }
        fun showList() {
            val rows = readRows()
            ImeDialogBuilder()
                .setTitle("ثبت شوي AutoText لنډیزونه (${rows.size})")
                .setItems(rows.map { "${it.first}  →  ${it.second}" }.toTypedArray()) { _, which ->
                    val selected = rows[which]
                    ImeDialogBuilder()
                        .setTitle(selected.first)
                        .setMessage(selected.second)
                        .setPositiveButton("سمون") { _, _ ->
                            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)) }
                            val code = EditText(this).apply { hint = "لنډ کوډ"; setText(selected.first) }
                            val expansion = EditText(this).apply { hint = "بشپړ متن"; setText(selected.second); minLines = 2 }
                            content.addView(code)
                            content.addView(expansion)
                            ImeDialogBuilder()
                                .setTitle("AutoText اصلاح کړئ")
                                .setView(content)
                                .setNegativeButton("بند", null)
                                .setPositiveButton("ذخیره") { _, _ ->
                                    val newCode = code.text.toString().trim()
                                    val newExpansion = expansion.text.toString().trim()
                                    if (newCode.isNotEmpty() && newExpansion.isNotEmpty()) {
                                        val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                                        updated.removeAll { it.substringBefore("|||") == selected.first || it.substringBefore("|||") == newCode }
                                        updated.add("$newCode|||${newExpansion}")
                                        prefs.edit().putStringSet(key, HashSet(updated)).commit()
                                        Toast.makeText(this, "AutoText اصلاح شو", Toast.LENGTH_SHORT).show()
                                    }
                                }.show()
                        }
                        .setNegativeButton("حذف") { _, _ ->
                            ImeDialogBuilder()
                                .setTitle("لنډیز حذف کړئ؟")
                                .setNegativeButton("نه", null)
                                .setPositiveButton("هو، حذف") { _, _ ->
                                    val updated = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                                    updated.remove("${selected.first}|||${selected.second}")
                                    prefs.edit().putStringSet(key, HashSet(updated)).commit()
                                    showList()
                                }.show()
                        }
                        .show()
                }
                .setPositiveButton("بند", null)
                .show()
        }
        showList()
    }

    private fun expandAutoTextAndSpace() {
        val connection = currentInputConnection ?: return
        // Never inspect words or expand shortcuts in protected/private editor fields.
        if (isRestrictedEditorField()) {
            connection.commitText(" ", 1)
            updateSuggestions()
            return
        }
        val before = connection.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val word = getCurrentWord(before)
        learnCompletedWord(word)
        val entries = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
            .getStringSet("autoText_$language", emptySet()).orEmpty()
        val expansion = entries.mapNotNull { entry ->
            val parts = entry.split("|||", limit = 2)
            if (parts.size == 2 && parts[0].equals(word, ignoreCase = true)) parts[1] else null
        }.firstOrNull()
        if (expansion != null && word.isNotEmpty()) {
            connection.deleteSurroundingText(word.length, 0)
            connection.commitText("$expansion ", 1)
        } else {
            connection.commitText(" ", 1)
        }
        updateSuggestions()
    }

    private fun addSymbolsPanel(parent: LinearLayout) {
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("نښې", "اعراب", "اسلامي").forEachIndexed { index, label ->
            addSpecial(tabs, label, 1f, 34) {
                symbolsCategory = index
                refreshKeyboard()
            }
        }
        parent.addView(tabs, rowParams(36))

        val keys = when (symbolsCategory) {
            1 -> listOf(
                "َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ْ", "ّ",
                "ٰ", "ٓ", "ٔ", "ٖ", "ٗ", "٘", "ٙ", "ٚ",
                "ٛ", "ٜ", "ٝ", "ٞ", "ٟ", "ـ", "ٕ", "ٖ"
            )
            2 -> listOf(
                "﷽", "ﷺ", "ﷻ", "☪", "۞", "۩", "ٱ", "۝",
                "ۖ", "ۗ", "ۚ", "ۙ", "ۛ", "ۜ", "﴿", "﴾",
                "٭", "۞", "۩", "☾", "☽", "۞", "۝", "﷽"
            )
            else -> listOf(
                "@", "#", "$", "%", "&", "*", "+", "=",
                "/", "\\", "(", ")", "[", "]", "{", "}",
                "<", ">", "!", "?", ":", ";", "~", "_",
                "-", "—", "…", "•", "©", "®", "™", "٪"
            )
        }
        keys.chunked(8).forEach { row -> addKeyRow(parent, row.map { k(it) }, 34) }
    }

    private fun addEmojiPanel(parent: LinearLayout) {
        val groups = listOf(
            "مخونه" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😍","🥰","😘","😗","😙","😚","😋","😛","😜","🤪","😎","🤓","🧐","🤔","🤗","🤭","🤫","😴","😭","🥹","😌","😏","😔","😢","😤","😡","🤯","🥳","🤩","😬","🫠","🫣","🫡","🤠","🥱","🥲","😶","😐","😑","🙄","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😓","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤑","😈","👿","👻","💀","☠️","👽","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾"),
            "اسلامي" to listOf("🤲","🙏","🕌","🕋","📿","☪️","🌙","⭐","✨","❤️","💚","🤍","🕊️","🌹","🌸","📖","📚","🧎","🧕","🧔","☝️","🤝","💐","🌿","🪷","🫶","🛐","📜","🕯️","🪔","🤲🏻","🙏🏻","🧎‍♂️","🧎‍♀️","🧕🏻","🧔🏻","🤲🏼","🤲🏽","🤲🏾","🤲🏿","🌛","🌜","🌝","🌚","🌟","💫","🌠","🖋️","✒️","🪶","🧭","🌅","🌄","🌃","🌾","🌴","🌳","🌺","🌼","🪻","🕊","🩵","🩷","💞","💕","💖","💗","💓","💝","🌷","🍃","☝🏻","☝🏼","☝🏽","☝🏾","☝🏿","🤝🏻","🧎🏻","🧎🏼","🧎🏽","🧎🏾","🧎🏿"),
            "زړه" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🩹","💕","💞","💓","💗","💖","💘","💝","💟","❣️","💌","💋","💐","🌹","🩷","🩵","🩶","💏","💑","💍","💒","❤️‍🔥","♥️","♡","ღ","❥"),
            "لاسونه" to listOf("👍","👎","👏","🙌","👐","🤝","🙏","🤲","✍️","💪","👋","🤚","✋","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","☝️","👇","👆","🖐️","🫱","🫲","🫳","🫴","🫶","🤜","🤛","✊","👊","🫵","🖖","👈","👉","💅","🦾","🦿"),
            "طبیعت" to listOf("🌞","🌙","⭐","🌟","⚡","🔥","🌈","☁️","🌧️","❄️","🌊","🌳","🌴","🌵","🌷","🌸","🌺","🌻","🍀","🍃","🌿","🦋","🐦","🕊️","🌍","🌎","🌏","🌅","🌄","🌠","🌌","🌪️","🌫️","☀️","🌤️","⛅","🌥️","🌦️","🌨️","⛈️","🌩️","🌬️","🌱","🪴","🌲","🌾","🍁","🍂","🍄","🌼","🪻","🪷","💮","🏵️","🥀","🎋","🎍","☘️","💧","💦","☔","☃️","⛄"),
            "نښې" to listOf("✅","❌","❗","❓","⚠️","💯","♻️","🔔","🔕","📌","📍","🎉","🎊","🎁","🏆","⭐","🔒","🔑","📱","💬","📩","➡️","⬅️","🔝","🆗","🆕","🆙","🆒","🆓","🆔","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔺","🔻","➕","➖","✖️","➗","💠","🔰","✔️","☑️","✳️","✴️","❇️","‼️","⁉️","➰","➿","🔱","⚜️","♾️","©️","®️","™️","🛑","⛔","🚫","🔞","🚭","🚯","🚱","🚷","🚸","❎","🟰"),
            "حیوانات" to listOf("🐱","🐈","🐶","🐕","🐼","🐻","🦁","🐯","🐸","🐵","🐧","🐦","🦅","🦉","🦋","🐝","🐞","🐢","🐬","🐳","🐟","🦈","🐎","🐪","🐫","🐄","🐑","🐐","🐓","🦚","🦜","🕊️","🐘","🦒","🦓","🦌","🦬","🐂","🐃","🐖","🐗","🐏","🦙","🦘","🦥","🦦","🦨","🦡","🐁","🐀","🐇","🐿️","🦔","🦇","🐻‍❄️","🐨","🐮","🐷","🐔","🐣","🐤","🐥","🦆","🦢","🦩","🦊","🦝","🐴","🦄","🪲","🦗","🪳","🕷️","🦂","🐌","🐛","🪱","🦠","🐙","🦑","🦐","🦞","🦀","🐡","🦭","🐊","🦎","🐍"),
            "خوراک" to listOf("🍎","🍏","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🥭","🍍","🥥","🥑","🍅","🥕","🌽","🥒","🥬","🥦","🍞","🥐","🧀","🥚","🍚","🍲","🍵","☕","🥛","🍯","🍈","🥝","🫒","🥔","🍠","🥜","🌰","🫘","🫛","🍄","🫑","🌶️","🥗","🫚","🧄","🧅","🥖","🥨","🧇","🥞","🧈","🥯","🥪","🌮","🌯","🫔","🥙","🧆","🍳","🥘","🍝","🍜","🍛","🍣","🍱","🥟","🍤","🍙","🍘","🍥","🥠","🥮","🍢","🍡","🍧","🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🍼","🫖","🧃","🥤","🧋","🫗","🍶"),
            "سفر" to listOf("🚗","🚕","🚌","🚎","🏎️","🚓","🚑","🚒","🚲","🛵","🏍️","✈️","🛫","🛬","🚀","🚁","🚢","⛵","🚉","🚆","🚇","🗺️","🧭","🏔️","⛰️","🏕️","🏖️","🏝️","🏙️","🏠","🏫","🏥","🚙","🚐","🚚","🚛","🚜","🛻","🚔","🚍","🚘","🚖","🚃","🚋","🚞","🚟","🚠","🚡","🚄","🚅","🚈","🚝","🚂","🚊","🛩️","🛸","🛰️","🚤","🛥️","🛳️","⛴️","⚓","⛽","🚧","🛣️","🛤️","🗽","🗼","🏰","🏯","🏟️","🎡","🎢","🎠","⛲","⛺","🏜️","🏞️","🌋","🗻","🌆","🌇","🌉","🏘️","🏡","🏢","🏬","🏣","🏤","🏦","🏨","🏪","🏛️","⛪","🕌","🛕","🕍","⛩️","🕋")
        )
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        groups.forEachIndexed { index, _ ->
            addSpecial(tabs, listOf("😊","☪️","❤️","👍","🌿","🔣","🐾","🍎","🚗")[index], 1f, 34) {
                emojiCategory = index
                emojiPage = 0
                refreshKeyboard()
            }
        }
        parent.addView(tabs, rowParams(37))
        val selected = groups[emojiCategory.coerceIn(0, groups.lastIndex)].second.distinct()
        val pageSize = 48
        val pageCount = ((selected.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        emojiPage = emojiPage.coerceIn(0, pageCount - 1)
        selected.drop(emojiPage * pageSize).take(pageSize).chunked(8).forEach { row ->
            addKeyRow(parent, row.map { k(it) }, 31)
        }
        val pageNav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addSpecial(pageNav, "❮", 0.7f, 30) {
            emojiPage = if (emojiPage <= 0) pageCount - 1 else emojiPage - 1
            refreshKeyboard()
        }
        addSpecial(pageNav, " ${emojiPage + 1} / $pageCount ", 1f, 30) {
            emojiPage = (emojiPage + 1) % pageCount
            refreshKeyboard()
        }
        addSpecial(pageNav, "❯", 0.7f, 30) {
            emojiPage = (emojiPage + 1) % pageCount
            refreshKeyboard()
        }
        addSpecial(pageNav, "د ایموجي بندول", 0.8f, 30) {
            emojiPanelVisible = false
            refreshKeyboard()
        }
        parent.addView(pageNav, rowParams(34))
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
                prefs.edit().putStringSet("clipboardFavorites", HashSet(updated)).commit()
                Toast.makeText(this, "یوازې ستاسو په غوښتنه متن خوندي شو", Toast.LENGTH_SHORT).show()
                refreshKeyboard()
            }
        }
        addSpecial(saveRow, "خوندي شوي: ${favorites.size}", 1.1f, 36) {
            if (favorites.isEmpty()) Toast.makeText(this, "تر اوسه مو کوم متن په خپله خوښه نه دی خوندي کړی.", Toast.LENGTH_LONG).show()
            else ImeDialogBuilder().setTitle("ستاسو خوندي شوي متنونه")
                .setItems(favorites.toTypedArray()) { _, which ->
                    val selected = favorites.toList()[which]
                    ImeDialogBuilder()
                        .setTitle("خوندي شوی متن")
                        .setMessage(selected)
                        .setPositiveButton("داخلول") { _, _ ->
                            currentInputConnection?.commitText(selected, 1)
                            updateSuggestions()
                        }
                        .setNegativeButton("حذف") { _, _ ->
                            val updated = prefs.getStringSet("clipboardFavorites", emptySet())?.toMutableSet() ?: mutableSetOf()
                            updated.remove(selected)
                            prefs.edit().putStringSet("clipboardFavorites", HashSet(updated)).commit()
                            refreshKeyboard()
                        }
                        .show()
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
                    prefs.edit().putStringSet("clipboardFavorites", HashSet(updated)).commit()
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
        prefs.edit().putStringSet("savedPhrases", HashSet(saved)).commit()
        Toast.makeText(this, "جمله په شخصي جملو کې وساتل شوه", Toast.LENGTH_SHORT).show()
    }

    private fun showPhraseWindow() {
        phraseDialog?.dismiss()
        phraseCategory = phraseCategory.coerceIn(0, 4)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val dialog = ImeDialogBuilder()
            .setTitle("د سندس چټکې جملې")
            .setView(content)
            .setNegativeButton("بند", null)
            .create()
        phraseDialog = dialog
        addPhrasePanel(content)
        dialog.setOnDismissListener { if (phraseDialog === dialog) phraseDialog = null }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun showAddPhraseDialog() {
        val input = EditText(this).apply {
            hint = "خپله جمله یا متن ولیکئ"
            minLines = 3
            setSingleLine(false)
            setText(currentInputConnection?.getSelectedText(0)?.toString().orEmpty())
        }
        val dialog = ImeDialogBuilder()
            .setTitle("شخصي جمله اضافه کړئ")
            .setView(input)
            .setNegativeButton("بند", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phrase = input.text.toString().trim()
                if (phrase.isEmpty()) {
                    Toast.makeText(this, "لومړی جمله ولیکئ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                val saved = prefs.getStringSet("savedPhrases", emptySet())?.toMutableSet() ?: mutableSetOf()
                saved.add(phrase)
                if (prefs.edit().putStringSet("savedPhrases", HashSet(saved)).commit()) {
                    phraseCategory = 4
                    phraseIndex = 0
                    dialog.dismiss()
                    phraseDialog?.dismiss()
                    Toast.makeText(this, "جمله په شخصي برخې کې خوندي شوه", Toast.LENGTH_SHORT).show()
                    showPhraseWindow()
                } else {
                    Toast.makeText(this, "جمله خوندي نه شوه؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
                }
            }
        }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun showEditPhraseDialog(existingPhrase: String) {
        val input = EditText(this).apply {
            hint = "خپله جمله یا متن ولیکئ"
            minLines = 3
            setSingleLine(false)
            setText(existingPhrase)
            setSelection(text.length)
        }
        val dialog = ImeDialogBuilder()
            .setTitle("شخصي جمله اصلاح کړئ")
            .setView(input)
            .setNegativeButton("بند", null)
            .setPositiveButton("بدلون خوندي کړئ", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updatedPhrase = input.text.toString().trim()
                if (updatedPhrase.isEmpty()) {
                    Toast.makeText(this, "جمله تشه نه شي پاتې کېدای", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                val saved = prefs.getStringSet("savedPhrases", emptySet())?.toMutableSet() ?: mutableSetOf()
                saved.remove(existingPhrase)
                saved.add(updatedPhrase)
                if (prefs.edit().putStringSet("savedPhrases", HashSet(saved)).commit()) {
                    phraseIndex = 0
                    phraseCategory = 4
                    dialog.dismiss()
                    phraseDialog?.dismiss()
                    Toast.makeText(this, "شخصي جمله اصلاح او خوندي شوه", Toast.LENGTH_SHORT).show()
                    showPhraseWindow()
                } else {
                    Toast.makeText(this, "بدلون خوندي نه شو؛ بیا هڅه وکړئ", Toast.LENGTH_LONG).show()
                }
            }
        }
        attachDialogToIme(dialog)
        dialog.show()
    }

    private fun addPhrasePanel(parent: LinearLayout) {
        val categories = listOf("اسلامي", "پښتو", "English", "دري", "زما")
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categories.forEachIndexed { index, label ->
            addSpecial(categoryRow, label, 1f, 28) {
                phraseCategory = index
                phraseIndex = 0
                showPhraseWindow()
            }
        }
        addSpecial(categoryRow, "＋", 0.65f, 28) { showAddPhraseDialog() }
        addSpecial(categoryRow, "×", 0.55f, 28) {
            phrasePanelVisible = false
            phraseDialog?.dismiss()
        }
        parent.addView(categoryRow, rowParams(30))

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
                "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ",
                "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى",
                "اللَّهُمَّ اغْفِرْ لِي ذَنْبِي كُلَّهُ دِقَّهُ وَجِلَّهُ",
                "رَبَّنَا ظَلَمْنَا أَنْفُسَنَا وَإِنْ لَمْ تَغْفِرْ لَنَا وَتَرْحَمْنَا لَنَكُونَنَّ مِنَ الْخَاسِرِينَ",
                "رَبِّ اشْرَحْ لِي صَدْرِي وَيَسِّرْ لِي أَمْرِي",
                "اللَّهُمَّ ارْحَمْنِي وَاهْدِنِي وَارْزُقْنِي",
                "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ شَرِّ مَا عَمِلْتُ وَمِنْ شَرِّ مَا لَمْ أَعْمَلْ",
                "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكَسَلِ وَالْهَرَمِ وَالْمَأْثَمِ وَالْمَغْرَمِ",
                "اللَّهُمَّ إِنِّي أَسْأَلُكَ رِضَاكَ وَالْجَنَّةَ",
                "اللَّهُمَّ اجْعَلْنِي مِنَ التَّوَّابِينَ وَاجْعَلْنِي مِنَ الْمُتَطَهِّرِينَ",
                "اللَّهُمَّ ثَبِّتْ قَلْبِي عَلَى دِينِكَ",
                "اللَّهُمَّ أَحْسِنْ عَاقِبَتَنَا فِي الْأُمُورِ كُلِّهَا",
                "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ زَوَالِ نِعْمَتِكَ",
                "اللَّهُمَّ اجْعَلْ خَيْرَ عُمُرِي آخِرَهُ وَخَيْرَ عَمَلِي خَوَاتِيمَهُ",
                "اللَّهُمَّ بَارِكْ لَنَا فِي أَوْقَاتِنَا وَأَعْمَالِنَا",
                "اللَّهُمَّ أَصْلِحْ لِي دِينِي وَدُنْيَايَ وَآخِرَتِي",
                "اللَّهُمَّ إِنِّي أَسْأَلُكَ قَلْبًا سَلِيمًا وَلِسَانًا صَادِقًا",
                "اللَّهُمَّ اجْعَلْنَا مِنْ أَهْلِ الْقُرْآنِ وَالْعَمَلِ بِهِ",
                "اللَّهُمَّ ارْزُقْنَا حُبَّكَ وَحُبَّ مَنْ يُحِبُّكَ",
                "اللَّهُمَّ اغْفِرْ لِلْمُسْلِمِينَ وَالْمُسْلِمَاتِ",
                "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ",
                "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا أَنْتَ، أَسْتَغْفِرُكَ وَأَتُوبُ إِلَيْكَ",
                "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى سَيِّدِنَا مُحَمَّدٍ",
                "اللَّهُمَّ اجْعَلْنَا مِنَ الشَّاكِرِينَ الذَّاكِرِينَ",
                "رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ",
                "اللَّهُمَّ أَدْخِلْنَا الْجَنَّةَ وَأَجِرْنَا مِنَ النَّارِ",
                "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْبِرَّ وَالتَّقْوَى وَمِنَ الْعَمَلِ مَا تَرْضَى",
                "رَبَّنَا لَا تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا",
                "اللَّهُمَّ اجْعَلْ هَذَا الْبَلَدَ آمِنًا مُطْمَئِنًّا",
                "اللَّهُمَّ احْفَظْ أَهْلَنَا وَأَوْطَانَنَا وَجَمِيعَ الْمُسْلِمِينَ"
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
                showPhraseWindow()
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
                    phraseDialog?.dismiss()
                }
            }
            navigation.addView(phraseText, LinearLayout.LayoutParams(0, dp(40), 1f))
            addSpecial(navigation, "❯", 0.65f, 42) {
                phraseIndex = (phraseIndex + 1) % phrases.size
                showPhraseWindow()
            }
            parent.addView(navigation, rowParams(42))
            if (phraseCategory == 4) {
                val manageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                addSpecial(manageRow, "✎ جمله اصلاح", 1f, 34) {
                    phrases.getOrNull(phraseIndex)?.let { showEditPhraseDialog(it) }
                }
                addSpecial(manageRow, "حذف جمله", 1f, 34) {
                    val selected = phrases.getOrNull(phraseIndex)
                    if (selected != null) ImeDialogBuilder()
                        .setTitle("جمله حذف کړئ؟")
                        .setMessage("ایا دا جمله له شخصي جملو حذف کول غواړئ؟")
                        .setNegativeButton("نه", null)
                        .setPositiveButton("هو، حذف") { _, _ ->
                            val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
                            val saved = prefs.getStringSet("savedPhrases", emptySet())?.toMutableSet() ?: mutableSetOf()
                            saved.remove(selected)
                            prefs.edit().putStringSet("savedPhrases", HashSet(saved)).commit()
                            phraseIndex = 0
                            showPhraseWindow()
                        }.show()
                }
                parent.addView(manageRow, rowParams(36))
            }


        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun commitKeyTap(spec: KeySpec, frame: View) {
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
            !isSensitiveOrAddressField &&
            !isRestrictedEditorField()
        // Read nearby editor text only when English sentence capitalization actually needs it.
        val sentenceStart = if (shouldAutoCapitalize && !caps && !shifted) {
            val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString().orEmpty()
            val previous = before.trimEnd().lastOrNull()
            before.isBlank() || previous == null ||
                previous in listOf('.', '?', '!', '؟', '\n')
        } else false
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

    private var alternatePopupWindow: PopupWindow? = null

    private fun dismissAlternatePopup() {
        alternatePopupWindow?.dismiss()
        alternatePopupWindow = null
    }

    private fun showAlternatePopup(anchor: View, alternate: String) {
        dismissAlternatePopup()
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
        alternatePopupWindow = popup
        popup.setOnDismissListener {
            if (alternatePopupWindow === popup) alternatePopupWindow = null
        }
        popup.showAsDropDown(anchor, 0, -anchor.height - dp(44))
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
                textSize = if (keys.size >= 12) 17f else 20f
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
                language == 2 && spec.normal.any { it.isLetter() } -> spec.normal.uppercase()
                else -> ""
            }

            if (longPressOutput.isNotEmpty()) {
                val hint = TextView(this).apply {
                    text = longPressOutput
                    textSize = 10f
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(1), dp(2), dp(1))
                    // Alternate glyph stays inside the upper-right corner of its key.
                    background = null
                }

                frame.addView(
                    hint,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    ).apply {
                        topMargin = dp(3)
                        rightMargin = dp(4)
                    }
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
                var pendingLongPressOutput = ""
                val path = mutableListOf<String>()
                val longPressTask = Runnable {
                    if (!glideStarted && longPressOutput.isNotEmpty()) {
                        // Preview the alternate while held; insert it only when the finger is released.
                        pendingLongPressOutput = longPressOutput
                        showAlternatePopup(frame, longPressOutput)
                        longPressHandled = true
                    }
                }

                frame.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            glideStarted = false
                            longPressHandled = false
                            pendingLongPressOutput = ""
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
                            dismissAlternatePopup()
                            if (longPressHandled) {
                                if (pendingLongPressOutput.isNotEmpty()) {
                                    currentInputConnection?.commitText(pendingLongPressOutput, 1)
                                    pendingLongPressOutput = ""
                                    updateSuggestions()
                                }
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
                            dismissAlternatePopup()
                            true
                        }
                        else -> true
                    }
                }
            } else {
                var alternatePending = ""
                var alternateLongPress = false
                val alternateTask = Runnable {
                    if (longPressOutput.isNotEmpty()) {
                        alternatePending = longPressOutput
                        alternateLongPress = true
                        showAlternatePopup(frame, longPressOutput)
                    }
                }
                var alternateDownX = 0f
                var alternateDownY = 0f
                frame.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            alternateDownX = event.rawX
                            alternateDownY = event.rawY
                            alternatePending = ""
                            alternateLongPress = false
                            handler.postDelayed(
                                alternateTask,
                                android.view.ViewConfiguration.getLongPressTimeout().toLong()
                            )
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val moved = kotlin.math.hypot(
                                (event.rawX - alternateDownX).toDouble(),
                                (event.rawY - alternateDownY).toDouble()
                            )
                            if (moved > dp(12)) handler.removeCallbacks(alternateTask)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(alternateTask)
                            dismissAlternatePopup()
                            if (alternateLongPress && alternatePending.isNotEmpty()) {
                                currentInputConnection?.commitText(alternatePending, 1)
                                updateSuggestions()
                            } else {
                                commitKeyTap(spec, frame)
                            }
                            alternatePending = ""
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacks(alternateTask)
                            dismissAlternatePopup()
                            alternatePending = ""
                            alternateLongPress = false
                            true
                        }
                        else -> true
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

    private fun performKeyFeedback(view: View) {
        if (hapticEnabled) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) {
            (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                .playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        }
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
                        } else {
                            performKeyFeedback(view)
                            action()
                        }
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
                        performKeyFeedback(button)
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
                performKeyFeedback(it)
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
            // Keep the Persian/Dari/Pashto zero-width non-joiner inside compound words.
            char == '\u200C' ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun getCurrentWord(text: String): String {
        return text.takeLastWhile {
            isWordCharacter(it)
        }
    }

    // Normalize Arabic-script variants for matching without changing inserted spelling.
    private fun normalizeSuggestionText(value: String): String {
        return value.lowercase()
            .filterNot {
                val type = Character.getType(it)
                type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    type == Character.ENCLOSING_MARK.toInt() ||
                    it == '\u0640'
            }
            .replace('ك', 'ک')
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
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
        val normalizedCurrent = normalizeSuggestionText(currentWord)
        val prefixMatches = candidates.filter { word ->
            val normalizedWord = normalizeSuggestionText(word)
            normalizedWord.startsWith(normalizedCurrent) && normalizedWord != normalizedCurrent
        }.sortedWith(
            compareBy<String> {
                kotlin.math.abs(normalizeSuggestionText(it).length - normalizedCurrent.length)
            }.thenBy { normalizeSuggestionText(it).length }
        )
        if (prefixMatches.isNotEmpty()) return prefixMatches.take(3)

        if (normalizedCurrent.length < 4) return emptyList()
        return candidates
            .map { word ->
                val normalizedWord = normalizeSuggestionText(word)
                Triple(
                    word,
                    editDistanceAtMostOne(normalizedCurrent, normalizedWord),
                    normalizedWord.commonPrefixWith(normalizedCurrent).length
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

    // Respect Android's explicit request not to personalize or inspect this editor.
    // Saved email chips are also hidden in fields that disable personalized learning.
    private fun isRestrictedEditorField(info: EditorInfo? = currentInputEditorInfo): Boolean {
        val editor = info ?: return true
        val editorType = editor.inputType
        val inputClass = editorType and android.text.InputType.TYPE_MASK_CLASS
        val variation = editorType and android.text.InputType.TYPE_MASK_VARIATION
        val privateImeField = Build.VERSION.SDK_INT >= 26 &&
            (editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return inputClass != android.text.InputType.TYPE_CLASS_TEXT ||
            variation in setOf(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            ) ||
            (editorType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
            privateImeField
    }

    // Learn only a completed single word after the user types a space.
    // This is local-only, user-controllable, and never runs in restricted editor fields.
    private fun learnCompletedWord(candidate: String) {
        val prefs = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE)
        if (!prefs.getBoolean("autoLearnWords", true)) return
        if (isRestrictedEditorField()) return

        val word = candidate.trim()
        if (word.length !in 2..40 || !word.any { it.isLetter() } ||
            word.any { !isWordCharacter(it) }
        ) return

        val key = "personalWords_$language"
        val words = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (word in words || offlineWordsFor(language).any {
                normalizeSuggestionText(it) == normalizeSuggestionText(word)
            }
        ) return

        // Keep the on-device personal vocabulary bounded.
        if (words.size >= 3000) return
        words.add(word)
        prefs.edit().putStringSet(key, HashSet(words)).apply()
    }

    private fun addCurrentWordToDictionary() {
        if (isRestrictedEditorField()) {
            Toast.makeText(this, "په دې ځانګړې خانه کې کلمه قاموس ته نه شي اضافه کېدای.", Toast.LENGTH_LONG).show()
            return
        }
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
        prefs.edit().putStringSet(dictionaryKey, HashSet(saved)).commit()
        Toast.makeText(this, "کلمه شخصي قاموس ته اضافه شوه؛ که وغواړئ مانا یې هم ولیکئ.", Toast.LENGTH_SHORT).show()
        updateSuggestions()
        showAddDictionaryEntryDialog(word, dictionaryEntries().firstOrNull { it.first == word }?.second.orEmpty())
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
        val isUriField = inputClass == android.text.InputType.TYPE_CLASS_TEXT &&
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI
        val suppressSuggestions = (editorType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val noPersonalizedLearning = Build.VERSION.SDK_INT >= 26 &&
            ((currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        // Never inspect surrounding text or show personalized content in protected editors.
        if (isTextPassword || isNumericPassword || isUriField || suppressSuggestions ||
            noPersonalizedLearning || inputClass != android.text.InputType.TYPE_CLASS_TEXT
        ) return

        val savedEmail = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getString("preferredEmail", "").orEmpty()
        val isEmailField = variation in setOf(
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        if (isEmailField) {
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
        ImeDialogBuilder().setTitle("ستاسو ایمیل").setMessage("ایمیل یوازې په دې موبایل کې ساتل کېږي او د ایمیل په خانه کې به په یوه ټک ښکاره شي.")
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

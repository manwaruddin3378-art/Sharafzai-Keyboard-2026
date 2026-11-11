package com.sharafzai.keyboard

import android.app.*
import android.content.*
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("SharafzaiTools", MODE_PRIVATE) }
    private var page = "home"
    private var openedPdfUri: Uri? = null
    private var pdfPageIndex = 0
    private var textToSpeech: TextToSpeech? = null
    private var audioPlayer: MediaPlayer? = null
    private var currentAudioPath: String? = null
    private var voiceTextInput: EditText? = null
    private var chosen = Calendar.getInstance()
    private lateinit var root: LinearLayout
    private var homeDhikrView: TextView? = null
    private var homeDhikrIndex = 0
    private val homeDhikrList = listOf("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "الْحَمْدُ لِلَّهِ")
    private val homeDhikrHandler = Handler(Looper.getMainLooper())
    private val homeDhikrRotation = object : Runnable {
        override fun run() {
            homeDhikrIndex = (homeDhikrIndex + 1) % homeDhikrList.size
            homeDhikrView?.text = "ذکر:  " + homeDhikrList[homeDhikrIndex]
            if (homeDhikrView != null) homeDhikrHandler.postDelayed(this, 45_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent.getStringExtra("open_page") ?: "home"
        handlePdfIntent(intent)
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel("sharafzai_alarms", "Sharafzai Alarms", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701)
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePdfIntent(intent)
        render()
    }

    private fun handlePdfIntent(source: Intent?) {
        if (source == null) return
        val uri = when (source.action) {
            Intent.ACTION_VIEW -> source.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            openedPdfUri = uri
            pdfPageIndex = 0
            page = "pdf_viewer"
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 31 || (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
            AlarmStore.read(this).filter { it.enabled }.forEach { scheduleAlarm(it) }
        }
        if (page == "home") {
            homeDhikrHandler.removeCallbacks(homeDhikrRotation)
            homeDhikrHandler.postDelayed(homeDhikrRotation, 45_000)
        }
    }

    override fun onPause() {
        homeDhikrHandler.removeCallbacks(homeDhikrRotation)
        super.onPause()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.rgb(15, 15, 15))
        }
        root.addView(TextView(this).apply {
            text = "SHARAFZAI  •  شرفزی اپلیکیشن"
            textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(4, 18, 4, 18)
        })
        if (page != "home") {
            addButton(root, "⌂  اصلي پاڼې ته ستنېدل", 1f) { page = "home"; render() }
        }
        when (page) {
            "home" -> showHome()
            "alarms" -> { line("خپلواک الارم"); showAlarms() }
            "calendar" -> { line("کلنډر او ورځني یادښتونه"); showCalendar() }
            "calculator" -> showCalculator()
            "adhkar", "morning", "evening" -> showAdhkar(page)
            "phrases" -> showPhrases()
            "library" -> showLibrary()
            "pdf_viewer" -> showPdfViewer()
            "keyboard" -> showKeyboardSetup()
            "settings" -> showAppSettings()
            else -> showHome()
        }
        if (page == "home") {
            homeDhikrHandler.removeCallbacks(homeDhikrRotation)
            homeDhikrHandler.postDelayed(homeDhikrRotation, 45_000)
        } else {
            homeDhikrHandler.removeCallbacks(homeDhikrRotation)
            homeDhikrView = null
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showHome() {
        line("ستاسو ورځني وسایل او اسلامي اسانتیاوې په یوه ځای کې")
        homeDhikrView = TextView(this).apply {
            text = "ذکر:  " + homeDhikrList[homeDhikrIndex]
            textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(8, 20, 8, 20)
            setBackgroundColor(Color.rgb(45, 45, 45))
        }
        root.addView(homeDhikrView)
        addButton(root, "کیبورډ شرفزی", 1f) { page = "keyboard"; render() }
        addButton(root, "سمارټ کلکولیټر", 1f) { page = "calculator"; render() }
        addButton(root, "الارم او یادوونکي", 1f) { page = "alarms"; render() }
        addButton(root, "کلنډر او یادښتونه", 1f) { page = "calendar"; render() }
        addButton(root, "ټول اذکار", 1f) { page = "adhkar"; render() }
        addButton(root, "اذکار سهار", 1f) { page = "morning"; render() }
        addButton(root, "اذکار ماښام", 1f) { page = "evening"; render() }
        addButton(root, "اسلامي او ورځنۍ جملې", 1f) { page = "phrases"; render() }
        addButton(root, "زما کتابتون (PDF کتابونه او لیکنې)", 1f) { page = "library"; render() }
        addButton(root, "تنظیمات", 1f) { page = "settings"; render() }
        line("د موبایل پر اصلي سکرین د تلپاتې اذکارو لپاره: د موبایل پر تش ځای ګوته لږه شېبه کېږدئ، Widgets/ویجټونه خلاص کړئ، «د شرفزی اذکار» ومومئ او سکرین ته یې راکش کړئ. پر ویجټ ټک وکړئ، د اذکارو پاڼه به پرانیستل شي.")
    }



    private fun showVoiceTools() {
        line("غږ په لیکنه بدلول او لیکل شوی متن په غږ اورېدل. د غږ پېژندنې شتون او انټرنېټي اړتیا د موبایل له خدمت سره تړلې ده.")
        val languageOptions = listOf("پښتو" to "ps-AF", "دري" to "fa-AF", "عربي" to "ar", "انګلیسي" to "en-US")
        val language = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languageOptions.map { it.first })
        }
        root.addView(language, LinearLayout.LayoutParams(-1, dp(48)))
        val textBox = EditText(this).apply {
            hint = "دلته متن ولیکئ یا د غږ له لارې یې واورئ"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            minLines = 5
            gravity = Gravity.TOP
        }
        voiceTextInput = textBox
        root.addView(textBox, LinearLayout.LayoutParams(-1, dp(150)))
        addButton(root, "🎙 غږ په لیکنه بدلول", 1f) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageOptions[language.selectedItemPosition].second)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageOptions[language.selectedItemPosition].second)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "اوس خبرې وکړئ")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            try {
                startActivityForResult(intent, VOICE_REQUEST)
            } catch (_: Exception) {
                Toast.makeText(this, "په موبایل کې د غږ پېژندنې خدمت ونه موندل شو؛ اړوند خدمت نصب یا فعال کړئ.", Toast.LENGTH_LONG).show()
            }
        }
        addButton(root, "🔊 لیکل شوی متن په غږ واورئ", 1f) {
            val spokenText = textBox.text.toString().trim()
            if (spokenText.isEmpty()) {
                Toast.makeText(this, "لومړی متن ولیکئ یا ثبت کړئ", Toast.LENGTH_SHORT).show()
            } else {
                val selectedLocale = when (languageOptions[language.selectedItemPosition].second) {
                    "ps-AF" -> Locale("ps", "AF")
                    "fa-AF" -> Locale("fa", "AF")
                    "ar" -> Locale("ar")
                    else -> Locale.US
                }
                val speakNow = {
                    val result = textToSpeech?.setLanguage(selectedLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(this, "دا ژبه د موبایل په غږیز انجن کې نه شته؛ د TTS ژبني معلومات نصب کړئ.", Toast.LENGTH_LONG).show()
                    } else {
                        textToSpeech?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "sharafzai-tts")
                    }
                }
                if (textToSpeech == null) {
                    textToSpeech = TextToSpeech(this) { status ->
                        if (status == TextToSpeech.SUCCESS) runOnUiThread { speakNow() }
                        else Toast.makeText(this, "د متن غږیز انجن فعال نه شو.", Toast.LENGTH_LONG).show()
                    }
                } else speakNow()
            }
        }
        addButton(root, "متن پاکول", 1f) { textBox.setText("") }
        line("یادونه: غږ په لیکنه بدلول د Android د غږ پېژندنې اپ/خدمت کاروي او کېدای شي انټرنېټ وغواړي. د لیکنې په غږ بدلول د موبایل د Text-to-Speech انجن او د هغې د ژبو له ملاتړ سره تړلي دي؛ پښتو غږ په هر موبایل کې تضمین نه دی.")
    }

    private fun showAudioLibrary() {
        line("د علماوو بیانونه، درسونه او ویناوې دلته ورزیاتې کړئ. آډیو فایلونه د شرفزی اپلیکیشن په خپل حافظه کې ساتل کېږي.")
        addButton(root, "＋ نوی آډیو بیان ورزیات کړئ", 1f) {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivityForResult(pick, AUDIO_PICK_REQUEST)
            } catch (_: Exception) {
                Toast.makeText(this, "د فایلونو ټاکلو اپلیکیشن ونه موندل شو", Toast.LENGTH_LONG).show()
            }
        }
        val library = readAudioLibrary()
        line("ثبت شوي بیانونه: ${library.length()}")
        if (library.length() == 0) line("تر اوسه آډیو نه ده ورزیاته شوې.")
        for (i in 0 until library.length()) {
            val item = library.optJSONObject(i) ?: continue
            val title = item.optString("title", "بې نومه بیان")
            val scholar = item.optString("scholar", "ناټاکلی عالم")
            val path = item.optString("path", "")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(Color.rgb(38, 38, 38))
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 17f
                setTextColor(Color.WHITE)
            })
            card.addView(TextView(this).apply {
                text = "ویناوال: $scholar"
                textSize = 13f
                setTextColor(Color.LTGRAY)
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            addButton(actions, "▶ اورېدل", 1f) { playAudio(path) }
            addButton(actions, "■ درول", 1f) { stopAudio() }
            addButton(actions, "حذف", 0.8f) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("آډیو حذفول؟")
                    .setMessage("«$title» به له شرفزی آډیو کتابتون او د اپلیکیشن له حافظې حذف شي.")
                    .setPositiveButton("حذف") { _, _ ->
                        stopAudio()
                        runCatching { File(path).delete() }
                        val updated = org.json.JSONArray()
                        for (j in 0 until library.length()) {
                            val old = library.optJSONObject(j) ?: continue
                            if (old.optString("path") != path) updated.put(old)
                        }
                        prefs.edit().putString("audio_library", updated.toString()).apply()
                        render()
                    }
                    .setNegativeButton("نه", null).show()
            }
            card.addView(actions)
            root.addView(card)
        }
        line("آفلاین اورېدل: یو ځل چې آډیو فایل ورزیات شي، بیا یې د انټرنېټ پرته اورېدلای شئ.")
    }

    private fun readAudioLibrary(): org.json.JSONArray =
        runCatching { org.json.JSONArray(prefs.getString("audio_library", "[]") ?: "[]") }
            .getOrDefault(org.json.JSONArray())

    private fun playAudio(path: String) {
        try {
            stopAudio()
            audioPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { player ->
                    player.start()
                    Toast.makeText(this@MainActivity, "آډیو پیل شو", Toast.LENGTH_SHORT).show()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@MainActivity, "دا آډیو فایل نه شي غږېدلی.", Toast.LENGTH_LONG).show()
                    stopAudio()
                    true
                }
                prepareAsync()
            }
            currentAudioPath = path
        } catch (_: Exception) {
            Toast.makeText(this, "آډیو پرانیستل نه شو.", Toast.LENGTH_LONG).show()
            stopAudio()
        }
    }

    private fun stopAudio() {
        runCatching { audioPlayer?.stop() }
        runCatching { audioPlayer?.release() }
        audioPlayer = null
        currentAudioPath = null
    }

    private fun saveAudioFile(uri: Uri) {
        val folder = File(filesDir, "audio_library").apply { mkdirs() }
        val destination = File(folder, "audio_${System.currentTimeMillis()}.media")
        Thread {
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("فایل لوستل کېدای نه شول")
            }
            runOnUiThread {
                if (copied.isFailure) {
                    destination.delete()
                    Toast.makeText(this, "آډیو فایل خوندي نه شو.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val title = EditText(this).apply {
                    hint = "د بیان سرلیک"
                    setTextColor(Color.WHITE)
                }
                val scholar = EditText(this).apply {
                    hint = "د عالم/ویناوال نوم"
                    setTextColor(Color.WHITE)
                }
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(8), dp(20), 0)
                    addView(title)
                    addView(scholar)
                }
                AlertDialog.Builder(this)
                    .setTitle("د آډیو معلومات")
                    .setView(panel)
                    .setPositiveButton("ساتل") { _, _ ->
                        val library = readAudioLibrary()
                        library.put(org.json.JSONObject()
                            .put("path", destination.absolutePath)
                            .put("title", title.text.toString().trim().ifBlank { "د عالم بیان" })
                            .put("scholar", scholar.text.toString().trim().ifBlank { "ناټاکلی عالم" })
                            .put("added", System.currentTimeMillis()))
                        prefs.edit().putString("audio_library", library.toString()).apply()
                        Toast.makeText(this, "آډیو بیان خوندي شو", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton("لغوه") { _, _ -> destination.delete() }
                    .show()
            }
        }.start()
    }

    override fun onDestroy() {
        stopAudio()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    private fun showLanguageTools() {
        line("آنلاین قاموس او ژباړه: د دې برخې د کارولو لپاره انټرنېټ ته اړتیا ده. نور د اپ وسایل آفلاین کار کوي.")
        val languages = listOf("پښتو" to "ps", "انګلیسي" to "en", "عربي" to "ar")
        line("د متن ژبه")
        val source = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            setSelection(0)
        }
        root.addView(source, LinearLayout.LayoutParams(-1, dp(48)))
        line("ژباړه دې کومې ژبې ته وشي؟")
        val target = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            setSelection(1)
        }
        root.addView(target, LinearLayout.LayoutParams(-1, dp(48)))
        val input = EditText(this).apply {
            hint = "کلمه یا متن دلته ولیکئ"
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            minLines = 3; gravity = Gravity.TOP
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(120)))
        val output = TextView(this).apply {
            text = "ژباړه یا د قاموس پایله به دلته ښکاره شي"
            textSize = 17f; setTextColor(Color.WHITE); setPadding(8, 16, 8, 16)
        }
        root.addView(output)
        fun runOnline(dictionary: Boolean) {
            val textValue = input.text.toString().trim()
            if (textValue.isEmpty()) {
                Toast.makeText(this, "لومړی کلمه یا متن ولیکئ", Toast.LENGTH_SHORT).show()
                return
            }
            val from = languages[source.selectedItemPosition].second
            val to = languages[target.selectedItemPosition].second
            if (!dictionary && from == to) {
                Toast.makeText(this, "د ژباړې لپاره بېله موخه ژبه وټاکئ", Toast.LENGTH_SHORT).show()
                return
            }
            output.text = "مهرباني وکړئ، لږ انتظار..."
            Thread {
                val result = runCatching {
                    if (dictionary) {
                        val entry = lookupOnlineDictionary(textValue, from, to)
                        if (entry.isNotBlank()) entry
                        else if (from != to) {
                            "په آنلاین قاموس کې بشپړ تعریف ونه موندل شو؛ لاندې یې اتومات ژباړه ده:\n" +
                                translateWithMyMemory(textValue, from, to)
                        } else "د دې کلمې قاموسي تعریف ونه موندل شو."
                    } else translateWithMyMemory(textValue, from, to)
                }.getOrElse { "اوسمهال آنلاین خدمت ته لاسرسی ونه شو. انټرنېټ وګورئ او بیا هڅه وکړئ.\n\nتفصیل: ${it.message ?: "نامعلومه ستونزه"}" }
                runOnUiThread { output.text = if (dictionary) "قاموسي پایله:\n$result" else result }
            }.start()
        }
        addButton(root, "د کلمې قاموسي مانا", 1f) { runOnline(true) }
        addButton(root, "د متن ژباړه", 1f) { runOnline(false) }
        addButton(root, "له AI څخه پوښتنه", 1f) {
            val question = input.text.toString().trim()
            if (question.isEmpty()) {
                Toast.makeText(this, "لومړی خپله پوښتنه ولیکئ", Toast.LENGTH_SHORT).show()
            } else {
                val prompt = URLEncoder.encode(question, "UTF-8")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/?q=$prompt")))
                } catch (_: Exception) {
                    Toast.makeText(this, "د انټرنېټ براوزر ونه موندل شو", Toast.LENGTH_LONG).show()
                }
            }
        }
        line("یادونه: قاموسي تعریفونه د FreeDictionaryAPI او Wiktionary له معلوماتو ترلاسه کېږي (CC BY-SA 4.0)؛ د متن اتومات ژباړه MyMemory کاروي. دواړه انټرنېټ غواړي. د مهمو علمي او شرعي متنونو ژباړه له اصلي متن سره حتماً وګورئ. د AI پوښتنه په ChatGPT کې پرانیستل کېږي.")
    }

    private fun lookupOnlineDictionary(word: String, source: String, target: String): String {
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = URL("https://freedictionaryapi.com/api/v1/entries/$source/$encoded?translations=true")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "SharafzaiApp/1.0")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode == 404) return ""
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
            val json = org.json.JSONObject(body)
            val entries = json.optJSONArray("entries") ?: return ""
            val results = mutableListOf<String>()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val languageCode = entry.optJSONObject("language")?.optString("code", "") ?: ""
                if (languageCode != source) continue
                val partOfSpeech = entry.optString("partOfSpeech", "")
                val senses = entry.optJSONArray("senses") ?: continue
                for (j in 0 until senses.length()) {
                    val sense = senses.optJSONObject(j) ?: continue
                    val definition = sense.optString("definition", "").trim()
                    if (definition.isBlank()) continue
                    val section = StringBuilder()
                    if (partOfSpeech.isNotBlank()) section.append("[$partOfSpeech] ")
                    section.append(definition)
                    val examples = sense.optJSONArray("examples")
                    if (examples != null && examples.length() > 0) {
                        val example = examples.optString(0, "")
                        if (example.isNotBlank()) section.append("\nبېلګه: ").append(example)
                    }
                    val translations = sense.optJSONArray("translations")
                    val translatedWords = mutableListOf<String>()
                    if (translations != null) {
                        for (k in 0 until translations.length()) {
                            val translation = translations.optJSONObject(k) ?: continue
                            val code = translation.optJSONObject("language")?.optString("code", "") ?: ""
                            val translated = translation.optString("word", "")
                            if (code == target && translated.isNotBlank()) translatedWords.add(translated)
                        }
                    }
                    if (translatedWords.isNotEmpty()) {
                        section.append("\nژباړه: ").append(translatedWords.distinct().take(5).joinToString("، "))
                    }
                    results.add(section.toString())
                    if (results.size >= 8) break
                }
                if (results.size >= 8) break
            }
            if (results.isEmpty()) return ""
            val sourceUrl = json.optJSONObject("source")?.optString("url", "") ?: ""
            val attribution = "\n\nسرچینه: FreeDictionaryAPI؛ د Wiktionary پر بنسټ (CC BY-SA 4.0)."
            return results.joinToString("\n\n") + attribution +
                if (sourceUrl.isNotBlank()) "\nاصلي سرچینه: $sourceUrl" else ""
        } finally {
            connection.disconnect()
        }
    }

    private fun translateWithMyMemory(text: String, source: String, target: String): String {
        val chunks = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else current.toString() + " " + word
            if (candidate.toByteArray(Charsets.UTF_8).size > 420 && current.isNotEmpty()) {
                chunks.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks.joinToString(" ") { chunk ->
            val encoded = URLEncoder.encode(chunk, "UTF-8")
            val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=$source|$target")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "SharafzaiApp/1.0")
            }
            try {
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
                val json = org.json.JSONObject(body)
                val status = json.optInt("responseStatus", 200)
                if (status != 200) throw IllegalStateException(json.optString("responseDetails", "ژباړه ترلاسه نه شوه"))
                json.optJSONObject("responseData")?.optString("translatedText")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("د ژباړې پایله تشه ده")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun showLibrary() {
        line("خپل PDF کتابونه، شخصي لیکنې او نور اسناد دلته ورزیات کړئ. فایلونه په همدې موبایل کې د کتابتون له لارې لاسرسی لري.")
        addButton(root, "＋ له موبایل څخه PDF کتاب ورزیات کړئ", 1f) {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            try {
                startActivityForResult(pick, PDF_PICK_REQUEST)
            } catch (_: Exception) {
                Toast.makeText(this, "د فایلونو ټاکلو اپلیکیشن ونه موندل شو", Toast.LENGTH_LONG).show()
            }
        }
        val books = readPdfLibrary()
        line("ستاسو کتابونه: " + books.length())
        if (books.length() == 0) {
            line("تر اوسه PDF کتاب نه دی ورزیات شوی. د پورته تڼۍ له لارې یو PDF وټاکئ.")
        } else {
            for (i in 0 until books.length()) {
                val item = books.optJSONObject(i) ?: continue
                val title = item.optString("title", "بې نومه PDF")
                val category = item.optString("category", "نور اسناد")
                val uri = item.optString("uri", "")
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setBackgroundColor(Color.rgb(38, 38, 38))
                }
                row.addView(TextView(this).apply {
                    text = title
                    textSize = 17f
                    setTextColor(Color.WHITE)
                })
                row.addView(TextView(this).apply {
                    text = category
                    textSize = 12f
                    setTextColor(Color.LTGRAY)
                })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                addButton(actions, "کتاب پرانیزئ", 1f) { openPdf(uri) }
                addButton(actions, "له کتابتونه لرې", 1f) {
                    AlertDialog.Builder(this)
                        .setTitle("له کتابتونه لرې کول؟")
                        .setMessage("«" + title + "» به یوازې له شرفزی کتابتونه لرې شي؛ اصلي فایل به نه حذف کېږي.")
                        .setPositiveButton("لرې کول") { _, _ -> removePdfFromLibrary(uri); render() }
                        .setNegativeButton("نه", null)
                        .show()
                }
                row.addView(actions)
                root.addView(row)
            }
        }
        line("یادونه: یوازې PDF فایلونه منل کېږي. د کتاب لوستلو لپاره به د موبایل PDF لوستونکی پرانیستل شي. د کتابتون څخه د لرې کولو سره اصلي فایل نه حذف کېږي.")
    }

    private fun readPdfLibrary(): org.json.JSONArray {
        val raw = prefs.getString("pdf_library", "[]") ?: "[]"
        return runCatching { org.json.JSONArray(raw) }.getOrDefault(org.json.JSONArray())
    }

    private fun getPdfDisplayName(uri: Uri): String {
        var name = "PDF کتاب"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return name.removeSuffix(".pdf").removeSuffix(".PDF")
    }

    private fun savePdfToLibrary(uri: Uri, title: String, category: String) {
        val folder = File(filesDir, "pdf_library").apply { mkdirs() }
        val destination = File(folder, "book_${System.currentTimeMillis()}.pdf")
        Toast.makeText(this, "PDF کتابتون ته کاپي کېږي؛ لږ انتظار وکړئ...", Toast.LENGTH_SHORT).show()
        Thread {
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("PDF فایل لوستل کېدای نه شول")
            }
            runOnUiThread {
                if (copied.isFailure) {
                    destination.delete()
                    Toast.makeText(this, "PDF کتابتون ته خوندي نه شو.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val books = readPdfLibrary()
                books.put(org.json.JSONObject()
                    .put("uri", Uri.fromFile(destination).toString())
                    .put("title", title.ifBlank { "PDF کتاب" })
                    .put("category", category)
                    .put("added", System.currentTimeMillis()))
                prefs.edit().putString("pdf_library", books.toString()).apply()
                runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                Toast.makeText(this, "PDF په خپل کتابتون کې خوندي شو", Toast.LENGTH_SHORT).show()
                render()
            }
        }.start()
    }

    private fun openPdf(uriText: String) {
        openedPdfUri = Uri.parse(uriText)
        pdfPageIndex = 0
        page = "pdf_viewer"
        render()
    }

    private fun showPdfViewer() {
        val uri = openedPdfUri
        if (uri == null) {
            line("PDF فایل ونه موندل شو.")
            return
        }
        line("PDF لوستونکی")
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(controls, "مخکینۍ پاڼه", 1f) {
            if (pdfPageIndex > 0) { pdfPageIndex--; render() }
        }
        addButton(controls, "بله پاڼه", 1f) {
            pdfPageIndex++
            render()
        }
        root.addView(controls)
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                AndroidPdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) {
                        line("دا PDF تش دی.")
                        return
                    }
                    if (pdfPageIndex >= renderer.pageCount) pdfPageIndex = renderer.pageCount - 1
                    line("پاڼه ${pdfPageIndex + 1} له ${renderer.pageCount} څخه")
                    val pageToRender = renderer.openPage(pdfPageIndex)
                    try {
                        val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtMost(1600)
                        val width = maxWidth.coerceAtLeast(400)
                        val height = (width.toFloat() * pageToRender.height / pageToRender.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        pageToRender.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        root.addView(ImageView(this).apply {
                            setImageBitmap(bitmap)
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(Color.WHITE)
                        }, LinearLayout.LayoutParams(-1, -2))
                    } finally {
                        pageToRender.close()
                    }
                }
            } ?: line("دې PDF فایل ته لاسرسی ونه شو.")
        } catch (e: Exception) {
            line("PDF پرانیستل نه شو: ${e.message ?: "د فایل ستونزه"}")
        }
    }

    private fun removePdfFromLibrary(uriText: String) {
        val old = readPdfLibrary()
        val updated = org.json.JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("uri") != uriText) updated.put(item)
        }
        prefs.edit().putString("pdf_library", updated.toString()).apply()
        runCatching {
            val savedUri = Uri.parse(uriText)
            if (savedUri.scheme == "file") {
                val file = File(savedUri.path ?: "")
                if (file.canonicalPath.startsWith(File(filesDir, "pdf_library").canonicalPath)) file.delete()
            } else {
                contentResolver.releasePersistableUriPermission(savedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK) {
            val recognized = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!recognized.isNullOrBlank()) {
                voiceTextInput?.let { field ->
                    field.append(if (field.text.isNullOrBlank()) recognized else " $recognized")
                }
            }
            return
        }
        if (requestCode == AUDIO_PICK_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { saveAudioFile(it) }
            return
        }
        if (requestCode != PDF_PICK_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val takeFlags = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (takeFlags != 0) contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            Toast.makeText(this, "د فایل د اجازې ساتل ممکن نه شول؛ بل فایل وازمویئ", Toast.LENGTH_LONG).show()
            return
        }
        val edit = EditText(this).apply {
            setText(getPdfDisplayName(uri))
            hint = "د کتاب یا لیکنې نوم"
            setTextColor(Color.WHITE)
        }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("کتاب", "زما شخصي لیکنې", "څېړنې او مقالې", "نور اسناد"))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply { text = "د کتاب نوم"; setTextColor(Color.WHITE) })
            addView(edit)
            addView(TextView(this@MainActivity).apply { text = "ډول"; setTextColor(Color.WHITE); setPadding(0, dp(8), 0, 0) })
            addView(category)
        }
        AlertDialog.Builder(this)
            .setTitle("کتابتون ته PDF ورزیاتول")
            .setView(panel)
            .setPositiveButton("ساتل") { _, _ ->
                val kinds = listOf("کتاب", "زما شخصي لیکنې", "څېړنې او مقالې", "نور اسناد")
                savePdfToLibrary(uri, edit.text.toString().trim(), kinds[category.selectedItemPosition])
            }
            .setNegativeButton("لغوه") { _, _ ->
                runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            .show()
    }

    companion object {
        private const val PDF_PICK_REQUEST = 7401
        private const val VOICE_REQUEST = 7402
        private const val AUDIO_PICK_REQUEST = 7403
    }

    private fun showKeyboardSetup() {
        line("کیبورډ د موبایل په نورو اپلیکیشنونو کې هم کار کوي. لومړی یې فعال او بیا یې د لیکلو پر مهال انتخاب کړئ.")
        addButton(root, "د کیبورډونو تنظیمات پرانیستل", 1f) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        addButton(root, "شرفزی کیبورډ انتخابول", 1f) {
            val manager = (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            manager.showInputMethodPicker()
        }
    }

    private fun showAppSettings() {
        line("د شرفزی اپلیکیشن عمومي تنظیمات")
        addButton(root, "د خبرتیاوو اجازه", 1f) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701)
            else Toast.makeText(this, "په دې Android نسخه کې جلا اجازه نه غواړي", Toast.LENGTH_SHORT).show()
        }
        addButton(root, "د دقیق الارم اجازه", 1f) {
            if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            else Toast.makeText(this, "دقیق الارم اجازه نه غواړي", Toast.LENGTH_SHORT).show()
        }
        line("ډیزاین: تور، سپین او خړ. معلومات او یادښتونه په همدې وسیله کې ساتل کېږي.")
    }

    private fun showPhrases() {
        line("د کاپي کولو لپاره پر هره جمله کلیک وکړئ.")
        val groups = listOf(
            "اسلامي" to listOf("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "مَا شَاءَ اللَّهُ لَا قُوَّةَ إِلَّا بِاللَّهِ", "جَزَاكَ اللَّهُ خَيْرًا", "بَارَكَ اللَّهُ فِيكَ"),
            "ورځنۍ محاوره" to listOf("السلام علیکم ورحمة الله وبرکاته", "وعلیکم السلام ورحمة الله وبرکاته", "څنګه یاست؟", "ډېره مننه", "الله دې مل شه", "په خیر اوسئ"),
            "دعایي جملې" to listOf("اللَّهُمَّ اغْفِرْ لِي", "رَبِّ زِدْنِي عِلْمًا", "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ", "اللَّهُمَّ يَسِّرْ وَلَا تُعَسِّرْ")
        )
        groups.forEach { group ->
            line(group.first)
            group.second.forEach { phrase -> addButton(root, phrase, 1f) {
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Sharafzai", phrase))
                Toast.makeText(this, "جمله کاپي شوه", Toast.LENGTH_SHORT).show()
            } }
        }
    }

    private fun showAdhkar(kind: String) {
        val morning = kind == "morning"
        val evening = kind == "evening"
        line(when { morning -> "د سهار اذکار"; evening -> "د ماښام اذکار"; else -> "ورځني عمومي اذکار" })
        val items = when {
            morning -> listOf(
                Triple("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ", "سهار مو کړ او پاچاهي الله لره ده.", "مسلم؛ د سهار ذکر"),
                Triple("اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ", "اې الله! ستا په مرسته سهار او ماښام کوو، ستا په مرسته ژوند او مرګ کوو او ستا لوري ته بیا راپاڅېدل دي.", "ابوداود او ترمذي؛ د سهار او ماښام ذکر"),
                Triple("رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا", "پر الله د رب، پر اسلام د دین او پر محمد ﷺ د نبي په توګه راضي شوم.", "ابوداود او ترمذي")
            )
            evening -> listOf(
                Triple("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ", "ماښام مو کړ او پاچاهي الله لره ده.", "مسلم؛ د ماښام ذکر"),
                Triple("اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ", "اې الله! ستا په مرسته ماښام او سهار کوو، ستا په مرسته ژوند او مرګ کوو او ستا لوري ته ورتګ دی.", "ابوداود او ترمذي"),
                Triple("رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا", "پر الله د رب، پر اسلام د دین او پر محمد ﷺ د نبي په توګه راضي شوم.", "ابوداود او ترمذي")
            )
            else -> listOf(
                Triple("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "الله له هر عیب څخه پاک دی او ستاینه یې کوم.", "صحیح البخاري او صحیح مسلم"),
                Triple("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "له الله څخه بښنه غواړم او هغه ته توبه کوم.", "صحیح البخاري"),
                Triple("لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "له الله پرته د ګناه د پرېښودو او د نېکۍ د کولو ځواک نشته.", "صحیح البخاري او صحیح مسلم"),
                Triple("سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ", "الله پاک دی، ټولې ستاینې الله لره دي، له الله پرته بل معبود نشته او الله تر ټولو لوی دی.", "صحیح مسلم؛ د ذکر فضیلت")
            )
        }
        items.forEachIndexed { index, item ->
            line((index + 1).toString() + ". " + item.first)
            line(item.second)
            line("ماخذ: " + item.third)
            addButton(root, "ذکر کاپي کړئ", 1f) {
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("ذکر", item.first))
                Toast.makeText(this, "ذکر کاپي شو", Toast.LENGTH_SHORT).show()
            }
        }
        addButton(root, "د دې اذکارو لپاره یادوونکی جوړ کړئ", 1f) { chooseAdhkarReminder(kind) }
        line("یادونه: دا لومړنی انتخاب دی؛ د بشپړو اذکارو، شمېرونو او روایتونو دقیق تخریج به د وروستۍ نسخې تر خپرېدو مخکې جلا وکتل شي.")
    }

    private fun chooseAdhkarReminder(kind: String) {
        val morning = kind == "morning"
        val evening = kind == "evening"
        val defaultHour = if (evening) 18 else 6
        TimePickerDialog(this, { _, hour, minute ->
            val id = (System.nanoTime() % Int.MAX_VALUE).toInt()
            val label = if (morning) "د سهار اذکار" else if (evening) "د ماښام اذکار" else "د ورځنیو اذکارو یادونه"
            val alarm = AlarmItem(id, hour, minute, label, true)
            val saved = AlarmStore.read(this).toMutableList()
            saved.add(alarm)
            AlarmStore.write(this, saved)
            scheduleAlarm(alarm)
            Toast.makeText(this, "یادوونکی ثبت شو", Toast.LENGTH_SHORT).show()
        }, defaultHour, 0, true).show()
    }

    private fun showCalculator() {
        line("ساده محاسبه: جمع، تفریق، ضرب او تقسیم")
        val display = EditText(this).apply {
            hint = "0"; textSize = 26f; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setSingleLine(true); gravity = Gravity.END; setBackgroundColor(Color.rgb(40, 40, 40))
        }
        root.addView(display, LinearLayout.LayoutParams(-1, dp(58)))
        val keys = listOf("7","8","9","÷","4","5","6","×","1","2","3","−","0",".","C","+","=")
        keys.chunked(4).forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { key -> addButton(row, key, 1f) {
                when (key) {
                    "C" -> display.setText("")
                    "=" -> runCatching { display.setText(calculate(display.text.toString()).toString()) }
                        .onFailure { Toast.makeText(this, "ناسم محاسبه", Toast.LENGTH_SHORT).show() }
                    else -> display.append(when (key) { "×" -> "*"; "÷" -> "/"; "−" -> "-"; else -> key })
                }
            } }
            root.addView(row)
        }
    }

    private fun calculate(expression: String): Double {
        class Parser(private val source: String) {
            var pos = 0
            fun spaces() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
            fun eat(c: Char): Boolean { spaces(); if (pos < source.length && source[pos] == c) { pos++; return true }; return false }
            fun number(): Double { spaces(); val start = pos; while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++; require(pos > start); return source.substring(start,pos).toDouble() }
            fun factor(): Double { if (eat('+')) return factor(); if (eat('-')) return -factor(); if (eat('(')) { val v=expr(); require(eat(')')); return v }; return number() }
            fun term(): Double { var v=factor(); while (true) { if (eat('*')) v*=factor() else if (eat('/')) { val d=factor(); require(d != 0.0); v/=d } else return v } }
            fun expr(): Double { var v=term(); while (true) { if (eat('+')) v+=term() else if (eat('-')) v-=term() else return v } }
        }
        val parser = Parser(expression); val result = parser.expr(); parser.spaces(); require(parser.pos == expression.length); return result
    }

    private fun showCalendar() {
        val month = (chosen.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val monthTitle = SimpleDateFormat("MMMM yyyy", Locale("ps", "AF")).format(month.time)

        val monthNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addButton(monthNavigation, "‹", 0.8f) { moveMonth(-1) }
        monthNavigation.addView(TextView(this).apply {
            text = monthTitle
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, 50, 3f))
        addButton(monthNavigation, "›", 0.8f) { moveMonth(1) }
        root.addView(monthNavigation)

        val calendarGrid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 7
            useDefaultMargins = false
        }

        val weekdays = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
        weekdays.forEachIndexed { column, dayName ->
            val cell = TextView(this).apply {
                text = dayName
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(205, 205, 205))
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(0),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = dp(36)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            calendarGrid.addView(cell, params)
        }

        val firstWeekday = month.get(Calendar.DAY_OF_WEEK) % 7
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        for (day in 1..daysInMonth) {
            val position = firstWeekday + day - 1
            val row = 1 + position / 7
            val column = position % 7
            val isSelected = chosen.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                chosen.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
                chosen.get(Calendar.DAY_OF_MONTH) == day
            val isToday = today.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) == day

            val cell = TextView(this).apply {
                text = day.toString()
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    when {
                        isSelected -> Color.rgb(82, 82, 82)
                        isToday -> Color.rgb(75, 83, 96)
                        else -> Color.TRANSPARENT
                    }
                )
                setOnClickListener {
                    chosen.set(Calendar.YEAR, month.get(Calendar.YEAR))
                    chosen.set(Calendar.MONTH, month.get(Calendar.MONTH))
                    chosen.set(Calendar.DAY_OF_MONTH, day)
                    render()
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = dp(42)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            calendarGrid.addView(cell, params)
        }
        root.addView(calendarGrid)

        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
        line("ټاکل شوې نېټه: " + dateText)
        addButton(root, "نېټه وټاکئ", 1f) {
            DatePickerDialog(this, { _, y, m, d ->
                chosen.set(y, m, d)
                render()
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH)).show()
        }
        addButton(root, "＋ د دې ورځې یادښت ثبت کړئ", 1f) { addNote() }
        addButton(root, "د ټولو یادښتونو لړلیک", 1f) { showNotes() }
        addButton(root, "نننۍ نېټه", 1f) {
            chosen = Calendar.getInstance()
            render()
        }

        val key = "notes_" + dateText
        val notes = prefs.getStringSet(key, emptySet()).orEmpty()
        line("د دې ورځې یادښتونه:")
        if (notes.isEmpty()) line("تر اوسه یادښت نشته.") else notes.forEach { line("• " + it) }
        line("یادښتونه یوازې د شرفزی په دې موبایل کې ساتل کېږي.")
    }

    private fun moveMonth(amount: Int) {
        val day = chosen.get(Calendar.DAY_OF_MONTH)
        chosen.set(Calendar.DAY_OF_MONTH, 1)
        chosen.add(Calendar.MONTH, amount)
        chosen.set(Calendar.DAY_OF_MONTH, minOf(day, chosen.getActualMaximum(Calendar.DAY_OF_MONTH)))
        render()
    }

    private fun addNote() {
        val edit = EditText(this).apply { hint = "د یادښت متن ولیکئ" }
        AlertDialog.Builder(this).setTitle("نوی یادښت").setView(edit)
            .setPositiveButton("ساتل") { _, _ ->
                val value = edit.text.toString().trim()
                if (value.isNotEmpty()) {
                    val key = "notes_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                    val saved = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                    saved.add(value)
                    prefs.edit().putStringSet(key, saved).apply()
                    render()
                }
            }.setNegativeButton("لغوه", null).show()
    }

    private fun showNotes() {
        val all = prefs.all.filterKeys { it.startsWith("notes_") }.toSortedMap()
        val lines = all.flatMap { entry ->
            (entry.value as? Set<*>)?.map { entry.key.removePrefix("notes_") + " : " + it } ?: emptyList()
        }
        AlertDialog.Builder(this).setTitle("خوندي یادښتونه")
            .setMessage(if (lines.isEmpty()) "یادښت نشته" else lines.joinToString("\n"))
            .setPositiveButton("سمه ده", null).show()
    }

    private fun showAlarms() {
        addButton(root, "＋ نوی الارم", 1f) { chooseAlarmTime() }
        val manager = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            addButton(root, "د دقیق الارم اجازه فعاله کړئ", 1f) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            line("دقیق الارم د Android ځانګړې اجازې ته اړتیا لري.")
        }
        val items = AlarmStore.read(this)
        if (items.isEmpty()) line("تر اوسه الارم نه دی ثبت شوی.")
        items.forEach { alarm ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = String.format(Locale.US, "%02d:%02d  %s", alarm.hour, alarm.minute, alarm.label)
                textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, 54, 1f))
            addButton(row, if (alarm.enabled) "بند" else "فعال", 0.8f) { toggleAlarm(alarm.id) }
            addButton(row, "حذف", 0.8f) { deleteAlarm(alarm) }
            root.addView(row)
        }
        line("الارمونه په همدې وسیله کې ثبتېږي؛ د موبایل Clock اپلیکیشن ته اړتیا نه لري.")
    }

    private fun chooseAlarmTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val edit = EditText(this).apply { hint = "د الارم نوم (اختیاري)" }
            AlertDialog.Builder(this).setTitle("د الارم نوم").setView(edit)
                .setPositiveButton("ثبتول") { _, _ ->
                    val id = (System.nanoTime() % Int.MAX_VALUE).toInt()
                    val alarm = AlarmItem(id, hour, minute, edit.text.toString().ifBlank { "شرفزی الارم" }, true)
                    val list = AlarmStore.read(this).toMutableList()
                    list.add(alarm); AlarmStore.write(this, list); scheduleAlarm(alarm); render()
                }.setNegativeButton("لغوه", null).show()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun scheduleAlarm(alarm: AlarmItem) {
        val manager = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            Toast.makeText(this, "د دقیق الارم اجازه فعاله کړئ", Toast.LENGTH_LONG).show()
            return
        }
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour); set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pending = PendingIntent.getBroadcast(this, alarm.id,
            Intent(this, AlarmReceiver::class.java).putExtra("alarm_id", alarm.id).putExtra("label", alarm.label),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        if (Build.VERSION.SDK_INT >= 23) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
        else manager.setExact(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
    }

    private fun cancelAlarm(alarm: AlarmItem) {
        val pending = PendingIntent.getBroadcast(this, alarm.id, Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java).cancel(pending)
    }

    private fun toggleAlarm(id: Int) {
        val list = AlarmStore.read(this).map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        AlarmStore.write(this, list)
        list.firstOrNull { it.id == id }?.let { if (it.enabled) scheduleAlarm(it) else cancelAlarm(it) }
        render()
    }

    private fun deleteAlarm(alarm: AlarmItem) {
        cancelAlarm(alarm)
        AlarmStore.write(this, AlarmStore.read(this).filterNot { it.id == alarm.id })
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun line(value: String) {
        root.addView(TextView(this).apply {
            text = value; textSize = 15f; setTextColor(Color.LTGRAY); setPadding(5, 10, 5, 10)
        })
    }

    private fun addButton(parent: LinearLayout, label: String, weight: Float, action: () -> Unit) {
        val button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(82, 82, 82)); setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, 50, weight).apply { setMargins(3, 3, 3, 3) })
    }
}

data class AlarmItem(val id: Int, val hour: Int, val minute: Int, val label: String, val enabled: Boolean)

object AlarmStore {
    fun read(context: Context): List<AlarmItem> {
        val raw = context.getSharedPreferences("SharafzaiTools", Context.MODE_PRIVATE).getString("alarms", "[]") ?: "[]"
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                AlarmItem(item.getInt("id"), item.getInt("hour"), item.getInt("minute"), item.getString("label"), item.getBoolean("enabled"))
            }
        }.getOrDefault(emptyList())
    }
    fun write(context: Context, items: List<AlarmItem>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().put("id", item.id).put("hour", item.hour).put("minute", item.minute).put("label", item.label).put("enabled", item.enabled))
        }
        context.getSharedPreferences("SharafzaiTools", Context.MODE_PRIVATE).edit().putString("alarms", array.toString()).apply()
    }
}

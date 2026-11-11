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
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.ImageType
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("SharafzaiTools", MODE_PRIVATE) }
    private var page = "home"
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
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("sharafzai_alarms", "Sharafzai Alarms", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
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
        line("د کتابونو دننه لټون")
        val searchInput = EditText(this).apply {
            hint = "د موضوع، عبارت یا کلمې نوم ولیکئ"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(52)))
        val choices = mutableListOf("ټول کتابونه")
        for (i in 0 until books.length()) {
            choices.add(books.optJSONObject(i)?.optString("title", "بې نومه PDF") ?: "بې نومه PDF")
        }
        val bookChoice = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
        }
        root.addView(bookChoice, LinearLayout.LayoutParams(-1, dp(48)))
        val searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addButton(root, "⌕  موضوع ولټوئ", 1f) {
            val query = searchInput.text.toString().trim()
            if (query.isBlank()) {
                Toast.makeText(this, "لومړی د لټون موضوع ولیکئ", Toast.LENGTH_SHORT).show()
            } else {
                searchPdfLibrary(query, books, bookChoice.selectedItemPosition, searchResults)
            }
        }
        root.addView(searchResults)
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

    private fun prepareOcrData(): File {
        val dataRoot = File(filesDir, "tesseract").apply { mkdirs() }
        val tessData = File(dataRoot, "tessdata").apply { mkdirs() }
        listOf("ara", "fas", "pus", "eng").forEach { language ->
            val target = File(tessData, "$language.traineddata")
            if (!target.exists() || target.length() < 100_000) {
                assets.open("tessdata/$language.traineddata").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return dataRoot
    }

    private fun searchPdfLibrary(query: String, books: org.json.JSONArray, selected: Int, resultsView: LinearLayout) {
        resultsView.removeAllViews()
        val status = TextView(this).apply {
            text = "د متن او سکین شوو پاڼو لټون روان دی..."
            setTextColor(Color.LTGRAY)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        resultsView.addView(status)
        Thread {
            val found = mutableListOf<String>()
            val terms = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
            var tess: TessBaseAPI? = null
            try {
                PDFBoxResourceLoader.init(applicationContext)
                val indexes = if (selected == 0) (0 until books.length()).toList() else listOf(selected - 1)
                for (index in indexes) {
                    val item = books.optJSONObject(index) ?: continue
                    val title = item.optString("title", "بې نومه PDF")
                    val uriText = item.optString("uri", "")
                    if (uriText.isBlank()) continue
                    try {
                        contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                            PDDocument.load(stream).use { document ->
                                val stripper = PDFTextStripper()
                                val renderer = PDFRenderer(document)
                                for (pageNumber in 1..document.numberOfPages) {
                                    stripper.startPage = pageNumber
                                    stripper.endPage = pageNumber
                                    var pageText = stripper.getText(document)
                                    if (pageText.trim().length < 35) {
                                        if (tess == null) {
                                            val api = TessBaseAPI()
                                            val dataPath = prepareOcrData().absolutePath
                                            if (!api.init(dataPath, "pus+fas+ara+eng")) {
                                                api.recycle()
                                                throw IllegalStateException("OCR ژبني معلومات نه شول چمتو کېدای")
                                            }
                                            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                                            tess = api
                                        }
                                        val bitmap = renderer.renderImageWithDPI(pageNumber - 1, 220f, ImageType.GRAY)
                                        try {
                                            tess?.setImage(bitmap)
                                            val recognized = tess?.utF8Text ?: ""
                                            if (recognized.isNotBlank()) pageText = recognized
                                        } finally {
                                            bitmap.recycle()
                                            tess?.clear()
                                        }
                                    }
                                    val normalized = pageText.lowercase(Locale.ROOT)
                                    if (terms.all { normalized.contains(it) }) {
                                        val first = terms.firstOrNull()?.let { normalized.indexOf(it) } ?: 0
                                        val from = (first - 90).coerceAtLeast(0)
                                        val to = (first + 260).coerceAtMost(pageText.length)
                                        val excerpt = pageText.substring(from, to).replace(Regex("\\s+"), " ").trim()
                                        found.add("$title  |  پاڼه $pageNumber\\n$excerpt")
                                        if (found.size >= 100) break
                                    }
                                }
                            }
                        } ?: found.add("$title: فایل ته لاسرسی ونه شو.")
                    } catch (e: Exception) {
                        found.add("$title: د دې کتاب لوستلو کې ستونزه پېښه شوه: ${e.message ?: "نامعلومه ستونزه"}")
                    }
                    if (found.size >= 100) break
                }
            } catch (e: Exception) {
                found.add("د OCR چمتو کولو ستونزه: ${e.message ?: "مهرباني وکړئ بیا هڅه وکړئ"}")
            } finally {
                tess?.recycle()
            }
            runOnUiThread {
                resultsView.removeAllViews()
                resultsView.addView(TextView(this).apply {
                    text = if (found.isEmpty()) "پایله ونه موندل شوه." else "د لټون پایلې: ${found.size}"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setPadding(6, dp(10), 6, dp(10))
                })
                found.forEach { result ->
                    resultsView.addView(TextView(this).apply {
                        text = result
                        textSize = 14f
                        setTextColor(Color.LTGRAY)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        setBackgroundColor(Color.rgb(38, 38, 38))
                    })
                }
            }
        }.start()
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
        val books = readPdfLibrary()
        if ((0 until books.length()).any { books.optJSONObject(it)?.optString("uri") == uri.toString() }) {
            Toast.makeText(this, "دا PDF له مخکې په کتابتون کې شته", Toast.LENGTH_SHORT).show()
            return
        }
        books.put(org.json.JSONObject()
            .put("uri", uri.toString())
            .put("title", title.ifBlank { "PDF کتاب" })
            .put("category", category)
            .put("added", System.currentTimeMillis()))
        prefs.edit().putString("pdf_library", books.toString()).apply()
        Toast.makeText(this, "PDF کتابتون ته ورزیات شو", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun openPdf(uriText: String) {
        try {
            val uri = Uri.parse(uriText)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "د PDF لوستلو اپلیکیشن نشته؛ لومړی یو PDF Reader نصب کړئ", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "دا PDF فایل نه پرانیستل کېږي؛ کېدای شي فایل له خپل ځایه لرې شوی وي", Toast.LENGTH_LONG).show()
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
        runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(uriText), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
    }

    private fun showKeyboardSetup() {
        line("کیبورډ د موبایل په نورو اپلیکیشنونو کې هم کار کوي. لومړی یې فعال او بیا یې د لیکلو پر مهال انتخاب کړئ.")
        addButton(root, "د کیبورډونو تنظیمات پرانیستل", 1f) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        addButton(root, "شرفزی کیبورډ انتخابول", 1f) {
            val manager = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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

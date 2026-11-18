package com.sharafzai.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Notes editor hosted by an Activity so Android can connect the SUNDUS IME to its EditTexts. */
class NotesActivity : Activity() {
    private data class Note(val id: Long, val title: String, val body: String, val updatedAt: Long, val pinned: Boolean = false)
    private val store by lazy { AtomicFile(File(filesDir, "sundus_notes_v2.json")) }
    private val backup by lazy { AtomicFile(File(filesDir, "sundus_notes_v2.backup")) }
    private var unreadable = false
    private val maxChars = 100_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        manager()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun base(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(245, 248, 250))
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 48, 77)); setPadding(0, dp(6), 0, dp(10))
        })
        setContentView(root)
        return root
    }
    private fun btn(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { click() }
    }
    private fun manager() {
        val root = base("زما نوټس")
        val actions = LinearLayout(this)
        actions.addView(btn("نوی نوټ") { editor(null) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(btn("نسخه صادرول") {
            startActivity(Intent(this, NotesBackupActivity::class.java).putExtra(NotesBackupActivity.EXTRA_MODE, NotesBackupActivity.MODE_EXPORT))
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(btn("نسخه واردول") {
            startActivity(Intent(this, NotesBackupActivity::class.java).putExtra(NotesBackupActivity.EXTRA_MODE, NotesBackupActivity.MODE_IMPORT))
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(actions)
        val search = EditText(this).apply { hint = "د نوټ سرلیک یا متن ولټوئ"; setSingleLine(true); textSize = 16f }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun refresh(q: String = search.text.toString()) {
            list.removeAllViews()
            val found = readNotes().filter { q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) }
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            if (found.isEmpty()) list.addView(TextView(this).apply {
                text = if (q.isBlank()) "تر اوسه مو نوټ نه دی جوړ کړی." else "د لټون پایله ونه موندل شوه."
                gravity = Gravity.CENTER; textSize = 15f; setPadding(0, dp(24), 0, dp(24))
            })
            found.forEach { n ->
                list.addView(TextView(this).apply {
                    text = "📝  " + n.title + "\n" + n.body.replace("\n", " ").take(100)
                    textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(5,48,77)); cornerRadius = dp(10).toFloat() }
                    setOnClickListener { details(n) }
                    setOnLongClickListener {
                        AlertDialog.Builder(this@NotesActivity).setTitle("نوټ حذفول؟").setNegativeButton("بېرته", null)
                            .setPositiveButton("هو، حذف یې کړه") { _, _ -> if (save(readNotes().filterNot { it.id == n.id })) manager() else toast("نوټ حذف نه شو") }.show()
                        true
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refresh("")
        search.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun details(n: Note) {
        val root = base(n.title)
        root.addView(ScrollView(this).apply { addView(TextView(this@NotesActivity).apply {
            text = n.body; textSize = 17f; setTextColor(Color.BLACK); setPadding(dp(8), dp(8), dp(8), dp(8))
        }) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this)
        row.addView(btn("سمول") { editor(n) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(btn("حذف") {
            AlertDialog.Builder(this).setTitle("نوټ حذفول؟").setNegativeButton("بېرته", null)
                .setPositiveButton("هو، حذف یې کړه") { _, _ -> if (save(readNotes().filterNot { it.id == n.id })) manager() else toast("حذف ترسره نه شو") }.show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(btn("بېرته") { manager() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
    }
    private fun editor(old: Note?) {
        val root = base(if (old == null) "نوی نوټ جوړول" else "نوټ اصلاح کول")
        val title = EditText(this).apply { hint = "د نوټ سرلیک"; setSingleLine(true); setText(old?.title ?: "") }
        val body = EditText(this).apply {
            hint = "د موضوع بشپړ متن دلته ولیکئ (تر ۱۰۰ زره تورو پورې)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START; minLines = 8; setSingleLine(false); setText(old?.body ?: "")
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val count = TextView(this).apply { text = body.text.length.toString() + " / " + maxChars }
        body.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { this@NotesActivity.runOnUiThread { count.text = (s?.length ?: 0).toString() + " / " + maxChars } }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.addView(count)
        val row = LinearLayout(this)
        row.addView(btn(if (old == null) "خوندي کول" else "بدلون خوندي کول") {
            val t = title.text.toString().trim(); val b = body.text.toString()
            if (t.isBlank() || b.isBlank()) toast("د نوټ سرلیک او متن دواړه ولیکئ")
            else if (b.length > maxChars) toast("د نوټ متن له ۱۰۰ زره تورو زیات نه شي کېدای")
            else {
                val all = readNotes()
                if (unreadable) toast("د زړې زېرمې لوستل ستونزمن دي؛ بدلون ونه ساتل شو")
                else {
                    val n = Note(old?.id ?: System.currentTimeMillis(), t, b, System.currentTimeMillis(), old?.pinned ?: false)
                    val i = all.indexOfFirst { it.id == n.id }; if (i >= 0) all[i] = n else all.add(n)
                    if (save(all)) { toast("نوټ په بریالیتوب خوندي شو"); manager() } else toast("نوټ خوندي نه شو")
                }
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        row.addView(btn("بېرته") { if (old == null) manager() else details(old) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(row)
        title.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun parse(raw: String): MutableList<Note> {
        val a = JSONArray(raw)
        return MutableList(a.length()) { i -> val o = a.getJSONObject(i); Note(o.optLong("id"), o.optString("title"), o.optString("body"), o.optLong("updatedAt", o.optLong("id")), o.optBoolean("pinned")) }
            .filter { it.title.isNotBlank() || it.body.isNotBlank() }.toMutableList()
    }
    private fun read(file: AtomicFile): MutableList<Note>? = runCatching { file.openRead().bufferedReader(Charsets.UTF_8).use { parse(it.readText()) } }.getOrNull()
    private fun readNotes(): MutableList<Note> {
        unreadable = false
        if (store.baseFile.exists() || File(store.baseFile.path + ".bak").exists()) {
            read(store)?.let { return it }
            read(backup)?.let { recovered -> write(store, encode(recovered)); return recovered }
            unreadable = true; return mutableListOf()
        }
        val legacy = getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).getString("keyboardNotesV1", null) ?: return mutableListOf()
        return runCatching { val n = parse(legacy); if (save(n)) getSharedPreferences("SharafzaiSettings", MODE_PRIVATE).edit().remove("keyboardNotesV1").commit(); n }
            .getOrElse { unreadable = true; mutableListOf() }
    }
    private fun encode(notes: List<Note>): ByteArray {
        val a = JSONArray()
        notes.forEach { n -> a.put(JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("updatedAt", n.updatedAt).put("pinned", n.pinned)) }
        return a.toString().toByteArray(Charsets.UTF_8)
    }
    private fun write(file: AtomicFile, bytes: ByteArray): Boolean {
        var stream: FileOutputStream? = null
        return try { stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream); true }
        catch (e: Exception) { if (stream != null) file.failWrite(stream); false }
    }
    private fun save(notes: List<Note>): Boolean {
        if (unreadable) return false
        return runCatching {
            if (store.baseFile.exists()) { val previous = store.openRead().use { it.readBytes() }; if (!write(backup, previous)) return false }
            write(store, encode(notes))
        }.getOrDefault(false)
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

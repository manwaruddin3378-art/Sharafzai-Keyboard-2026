package com.sharafzai.keyboard

import android.app.Activity
import android.os.Bundle
import android.util.AtomicFile
import android.widget.Toast
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * SAF-based export/import for the private SUNDUS notes store.
 * Export creates a portable JSON file. Import validates the entire file before
 * replacing anything and preserves the current store as a recovery backup.
 */
class NotesBackupActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "notes_backup_mode"
        const val MODE_EXPORT = "export"
        const val MODE_IMPORT = "import"
        private const val REQUEST_CREATE = 7101
        private const val REQUEST_OPEN = 7102
        private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
        private const val MAX_NOTE_CHARS = 100_000
    }

    private val notesFile by lazy { AtomicFile(File(filesDir, "sundus_notes_v2.json")) }
    private val backupFile by lazy { AtomicFile(File(filesDir, "sundus_notes_v2.backup")) }
    private var mode: String = MODE_EXPORT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_EXPORT
        when (mode) {
            MODE_EXPORT -> createExportFile()
            MODE_IMPORT -> openBackupFile()
            else -> finishWithMessage("د بیک‌اپ غوښتنه ناسمه ده")
        }
    }

    private fun createExportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "SUNDUS-Notes-Backup.json")
        }
        startActivityForResult(intent, REQUEST_CREATE)
    }

    private fun openBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
        }
        startActivityForResult(intent, REQUEST_OPEN)
    }

    @Deprecated("Uses the platform document picker result for compatibility with minSdk 21")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) {
            finish()
            return
        }
        val uri = data.data!!
        when (requestCode) {
            REQUEST_CREATE -> exportTo(uri)
            REQUEST_OPEN -> importFrom(uri)
            else -> finish()
        }
    }

    private fun exportTo(uri: android.net.Uri) {
        try {
            val notes = readCurrentNotes()
            val root = JSONObject()
                .put("format", "SUNDUS_NOTES_BACKUP")
                .put("version", 1)
                .put("exportedAt", System.currentTimeMillis())
                .put("notes", notes)
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IllegalStateException("د ټاکل شوي فایل لیکل ممکن نه شول")
            finishWithMessage("د نوټونو بیک‌اپ په بریالیتوب صادر شو")
        } catch (error: Exception) {
            finishWithMessage("بیک‌اپ صادر نه شو: ${error.message ?: "نامعلومه ستونزه"}")
        }
    }

    private fun importFrom(uri: android.net.Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BACKUP_BYTES) { "د بیک‌اپ فایل له ۲۰ MB څخه لوی دی" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("فایل لوستل کېدای نه شي")

            // Fully parse and validate before touching the current notes.
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            require(root.optString("format") == "SUNDUS_NOTES_BACKUP") { "دا د SUNDUS د نوټونو بیک‌اپ نه دی" }
            require(root.optInt("version") == 1) { "د بیک‌اپ نسخه نه پېژندل کېږي" }
            val array = root.getJSONArray("notes")
            require(array.length() <= 10_000) { "په فایل کې د نوټونو شمېر له ټاکلي حد زیات دی" }
            val ids = HashSet<Long>()
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                val id = note.getLong("id")
                val title = note.getString("title")
                val body = note.getString("body")
                require(ids.add(id)) { "په بیک‌اپ کې تکراري پېژندشمېره شته" }
                require(title.isNotBlank()) { "د یوه نوټ سرلیک تش دی" }
                require(body.isNotBlank() && body.length <= MAX_NOTE_CHARS) { "د یوه نوټ متن تش یا له ۱۰۰ زره تورو زیات دی" }
            }

            // Keep a byte-for-byte recovery copy of the current store before replacement.
            if (notesFile.baseFile.exists()) {
                val current = notesFile.openRead().use { it.readBytes() }
                writeAtomic(backupFile, current)
            }
            val incoming = root.getJSONArray("notes").toString().toByteArray(Charsets.UTF_8)
            writeAtomic(notesFile, incoming)
            finishWithMessage("بیک‌اپ وارد شو؛ ${array.length()} نوټونه بېرته خوندي شول")
        } catch (error: Exception) {
            finishWithMessage("بیک‌اپ وارد نه شو؛ اوسني نوټونه مو بدل نه شول. ${error.message ?: ""}")
        }
    }

    private fun readCurrentNotes(): JSONArray {
        if (!notesFile.baseFile.exists()) return JSONArray()
        val raw = notesFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONArray(raw)
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}

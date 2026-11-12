package com.sharafzai.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.TextView
import android.widget.Toast

class VoiceInputActivity : Activity() {
    private var languageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageIndex = intent.getIntExtra("language", 0).coerceIn(0, 4)
        setContentView(TextView(this).apply {
            text = "د سندس غږیز لیکل\n\nد غږ پېژندنې کړکۍ پرانیستل کېږي. د آفلاین کار لپاره باید ستاسو په موبایل کې د اړوندې ژبې آفلاین غږیزه بسته نصب وي."
            textSize = 18f
            setPadding(28, 48, 28, 28)
        })

        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else {
            launchRecognition()
        }
    }

    private fun launchRecognition() {
        val locales = listOf("ps-AF", "ar", "en-US", "fa-AF", "ur-PK")
        val locale = locales[languageIndex]
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "اوس خبرې وکړئ")
        }
        try {
            startActivityForResult(request, REQUEST_SPEECH)
        } catch (_: Exception) {
            Toast.makeText(this, "په موبایل کې د غږ پېژندنې خدمت یا د دې ژبې آفلاین بسته نشته.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with the system speech recognizer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        if (resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                sendBroadcast(
                    Intent(ACTION_VOICE_TEXT)
                        .setPackage(packageName)
                        .putExtra(EXTRA_RECOGNIZED_TEXT, text)
                )
            }
        }
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            launchRecognition()
        } else if (requestCode == REQUEST_MIC) {
            Toast.makeText(this, "د غږ لیکلو لپاره د مایکروفون اجازه اړینه ده.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        const val ACTION_VOICE_TEXT = "com.sharafzai.keyboard.VOICE_TEXT"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        private const val REQUEST_MIC = 5101
        private const val REQUEST_SPEECH = 5102
    }
}

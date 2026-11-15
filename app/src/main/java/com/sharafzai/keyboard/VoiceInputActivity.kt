package com.sharafzai.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.TextView
import android.widget.Toast

/** Delegates speech capture to Android's configured recognizer; this app requests no RECORD_AUDIO permission. */
class VoiceInputActivity : Activity() {
    private var languageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageIndex = intent.getIntExtra("language", 0).coerceIn(0, 3)
        setContentView(TextView(this).apply {
            text = "د سندس غږیز لیکل\n\nد موبایل د غږ پېژندنې خدمت پرانیستل کېږي. د آفلاین کار لپاره د اړوندې ژبې آفلاین بسته نصب کړئ."
            textSize = 18f
            setPadding(28, 48, 28, 28)
        })
        launchRecognition()
    }

    private fun launchRecognition() {
        val locales = listOf("ps-AF", "ar", "en-US", "fa-AF")
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
            Toast.makeText(this, "په موبایل کې د غږ پېژندنې خدمت نشته یا دا ژبه نه ملاتړ کېږي.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with speech recognizers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        if (resultCode == RESULT_OK) {
            val recognized = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (recognized.isNotBlank()) {
                sendBroadcast(
                    Intent(ACTION_VOICE_TEXT)
                        .setPackage(packageName)
                        .putExtra(EXTRA_RECOGNIZED_TEXT, recognized)
                )
            }
        }
        finish()
    }

    companion object {
        const val ACTION_VOICE_TEXT = "com.sharafzai.keyboard.VOICE_TEXT"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        private const val REQUEST_SPEECH = 5102
    }
}

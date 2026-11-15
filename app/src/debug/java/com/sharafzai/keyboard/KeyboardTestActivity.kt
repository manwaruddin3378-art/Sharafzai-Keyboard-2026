package com.sharafzai.keyboard

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/** Debug-only host activity used by the emulator keyboard smoke test. */
class KeyboardTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(EditText(this).apply {
            contentDescription = "keyboard-test-editor"
            hint = "SUNDUS emulator test"
            textSize = 20f
            setSingleLine(false)
            if (intent.getBooleanExtra("privateEditor", false)) {
                imeOptions = imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        })
    }
}

package com.sharafzai.keyboard

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText

/** Debug-only host activity used by the emulator keyboard smoke test. */
class KeyboardTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val editor = EditText(this).apply {
            id = android.R.id.edit
            contentDescription = "keyboard-test-editor"
            hint = "SUNDUS emulator test"
            textSize = 20f
            setSingleLine(false)
            if (intent.getBooleanExtra("privateEditor", false)) {
                imeOptions = imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
        setContentView(editor)
        editor.requestFocus()
        // Explicitly request the selected IME: on API 34, a focused EditText alone
        // may not bring up the keyboard in an instrumentation-launched Activity.
        editor.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

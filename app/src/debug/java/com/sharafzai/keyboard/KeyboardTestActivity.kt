package com.sharafzai.keyboard

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** Debug-only host activity used by the emulator keyboard smoke test. */
class KeyboardTestActivity : Activity() {
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        editor = EditText(this).apply {
            id = android.R.id.edit
            contentDescription = "keyboard-test-editor"
            hint = "SUNDUS emulator test"
            textSize = 20f
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            setSingleLine(false)
            if (intent.getBooleanExtra("privateEditor", false)) {
                imeOptions = imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
        setContentView(editor)
        editor.requestFocus()
        Log.e("SUNDUS_TEST_ACTIVITY", "onCreate editor attached=${editor.isAttachedToWindow}")
    }

    override fun onResume() {
        super.onResume()
        editor.postDelayed({ requestKeyboardForTest() }, 500)
    }

    /**
     * Explicitly re-establish the editor connection after the test taps the field
     * and after Android switches the selected input method.
     */
    fun requestKeyboardForTest() {
        if (isFinishing || !::editor.isInitialized || !editor.isAttachedToWindow) return
        editor.requestFocus()
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.restartInput(editor)
        val accepted = manager.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.show(WindowInsets.Type.ime())
        }
        Log.e(
            "SUNDUS_TEST_ACTIVITY",
            "requestKeyboard windowFocus=${hasWindowFocus()} editorFocus=${editor.hasFocus()} active=${manager.isActive(editor)} accepted=${accepted}"
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.e("SUNDUS_TEST_ACTIVITY", "windowFocus=${hasFocus} initialized=${::editor.isInitialized}")
        if (!hasFocus || !::editor.isInitialized) return
        listOf(100L, 500L, 1_000L, 2_000L).forEach { delay ->
            editor.postDelayed({ requestKeyboardForTest() }, delay)
        }
    }
}

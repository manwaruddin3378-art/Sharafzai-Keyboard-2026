package com.sharafzai.keyboard

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowInsets
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
    }

    override fun onResume() {
        super.onResume()
        editor.postDelayed({
            if (!isFinishing && editor.isAttachedToWindow) {
                editor.requestFocus()
                val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                manager.restartInput(editor)
                manager.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    window.insetsController?.show(WindowInsets.Type.ime())
                }
            }
        }, 500)
    }

    /**
     * Request the IME only after this Activity owns window focus.
     * Calling showSoftInput from onCreate/post can race Android's input
     * connection setup; API 34 then rejects it because the view is not served.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        android.util.Log.e("SUNDUS_TEST_ACTIVITY", "windowFocus=" + hasFocus + " initialized=" + ::editor.isInitialized)
        if (!hasFocus || !::editor.isInitialized) return

        // Emulator boot and InputMethodManager binding can be slow on CI.
        // Retry after the editor is attached and the window has an input connection.
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        listOf(250L, 750L, 1_500L, 3_000L).forEach { delay ->
            editor.postDelayed({
                if (!isFinishing && editor.hasFocus() && editor.isAttachedToWindow) {
                    editor.requestFocus()
                    manager.restartInput(editor)
                    manager.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        window.insetsController?.show(WindowInsets.Type.ime())
                    }
                }
            }, delay)
        }
    }
}

package com.sharafzai.keyboard

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Keeps app-owned screens readable when Android 15/16 enforces edge-to-edge.
 * The keyboard service itself remains managed by the IME window.
 */
internal fun View.applySundusSystemBarInsets() {
    if (Build.VERSION.SDK_INT < 35) return

    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom

    setOnApplyWindowInsetsListener { view, insets ->
        val safeInsets = insets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        view.setPadding(
            baseLeft + safeInsets.left,
            baseTop + safeInsets.top,
            baseRight + safeInsets.right,
            baseBottom + safeInsets.bottom
        )
        insets
    }
    requestApplyInsets()
}

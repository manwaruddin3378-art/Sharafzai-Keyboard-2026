package com.sharafzai.keyboard

import android.content.Intent
import android.widget.EditText
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardSmokeTest {
    @Test
    fun keyboardStartsTypesInTwoLanguagesAndOpensDictionaryDialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val enableResult = device.executeShellCommand("ime enable com.sharafzai.keyboard/.KeyboardService")
        val setResult = device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        Log.e("SUNDUS_IME_DIAG", "enable=[" + enableResult + "] set=[" + setResult + "] list=[" + device.executeShellCommand("ime list -s") + "] default=[" + device.executeShellCommand("settings get secure default_input_method") + "]")
        device.wakeUp()
        runCatching { device.executeShellCommand("wm dismiss-keyguard") }
        runCatching { device.executeShellCommand("input keyevent 82") }
        val launchIntent = Intent(instrumentation.targetContext, KeyboardTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(launchIntent) as KeyboardTestActivity
        var editor: EditText? = null
        instrumentation.runOnMainSync {
            editor = activity.findViewById(android.R.id.edit)
            editor?.requestFocus()
            editor?.performClick()
        }
        assertNotNull("Test editor view was not created", editor)
        val activeEditor = editor!!
        // Select the IME only after the editor Activity owns window focus. Selecting it
        // before launch can leave Android bound to the previous IME on API 34.
        val focusedSetResult = device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        Log.e("SUNDUS_IME_DIAG", "post-focus ime set=[" + focusedSetResult + "] default=[" + device.executeShellCommand("settings get secure default_input_method") + "]")

        val keyboardAppeared = device.wait(Until.hasObject(By.text("✦ SUNDUS | سندس ✦")), 30_000)
        if (!keyboardAppeared) {
            Log.e("SUNDUS_IME_DIAG", "input_method=[" + device.executeShellCommand("dumpsys input_method | grep -E 'mCurMethodId|mCurId|mInputShown|mShowRequested|mCurFocusedWindow|mImeWindowVis'") + "]")
            Log.e("SUNDUS_IME_DIAG", "window=[" + device.executeShellCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'") + "]")
        }
        assertTrue("SUNDUS keyboard did not appear", keyboardAppeared)

        val pashtoKey = device.wait(Until.findObject(By.text("ض")), 3_000)
        assertNotNull("Pashto key was not found", pashtoKey)
        pashtoKey!!.click()
        assertEquals("Pashto key did not insert text", "ض", activeEditor.text.toString())

        val englishTab = device.wait(Until.findObject(By.text("English")), 3_000)
        assertNotNull("English language tab was not found", englishTab)
        englishTab!!.click()

        val englishKey = device.wait(Until.findObject(By.text("q")), 3_000)
        assertNotNull("English key was not found", englishKey)
        englishKey!!.click()
        assertEquals("English key did not insert text", "ضq", activeEditor.text.toString())

        val dictionaryButton = device.wait(Until.findObject(By.text("📖")), 3_000)
        assertNotNull("Dictionary toolbar button was not found", dictionaryButton)
        dictionaryButton!!.click()

        assertTrue(
            "Dictionary dialog did not appear above the keyboard",
            device.wait(Until.hasObject(By.text("قاموس او لټون")), 5_000)
        )
    }
}

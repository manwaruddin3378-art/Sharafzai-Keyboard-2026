package com.sharafzai.keyboard

import android.content.Intent
import android.widget.EditText
import android.os.SystemClock
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
    fun keyboardStartsTypesInTwoLanguagesAndBackspaceWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // Enable the IME first, but do not select it until a real editor window has focus.
        val enableResult = device.executeShellCommand("ime enable com.sharafzai.keyboard/.KeyboardService")
        Log.e("SUNDUS_IME_DIAG", "enable=[" + enableResult + "]")
        device.wakeUp()
        runCatching { device.executeShellCommand("wm dismiss-keyguard") }

        val launchIntent = Intent(instrumentation.targetContext, KeyboardTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val activity = instrumentation.startActivitySync(launchIntent) as KeyboardTestActivity
        var editor: EditText? = null
        instrumentation.runOnMainSync {
            editor = activity.findViewById(android.R.id.edit)
            editor?.requestFocus()
        }
        assertNotNull("Test editor view was not created", editor)
        val activeEditor = editor!!

        // Use a real coordinate tap rather than an accessibility-node lookup.
        // Some API 30 emulator images omit EditText nodes from UiAutomator's tree
        // even though the Activity and editor are rendered and attached.
        var tapX = -1
        var tapY = -1
        for (attempt in 0 until 40) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                // The hosted emulator can report window focus late even after the
                // Activity's editor is attached. Obtain its real screen bounds first;
                // the subsequent UiDevice tap brings the app window to the foreground.
                if (activeEditor.isAttachedToWindow && activeEditor.width > 0 && activeEditor.height > 0) {
                    val location = IntArray(2)
                    activeEditor.getLocationOnScreen(location)
                    tapX = location[0] + activeEditor.width / 2
                    tapY = location[1] + activeEditor.height / 2
                }
            }
            if (tapX >= 0 && tapY >= 0) break
            SystemClock.sleep(250)
        }
        assertTrue(
            "Test editor did not attach with measurable screen bounds",
            tapX >= 0 && tapY >= 0
        )
        assertTrue("Could not tap the test editor", device.click(tapX, tapY))
        device.waitForIdle()

        var focusState = ""
        instrumentation.runOnMainSync {
            activeEditor.requestFocus()
            activity.requestKeyboardForTest()
            focusState = "windowFocus=${activity.hasWindowFocus()} editorFocus=${activeEditor.hasFocus()} attached=${activeEditor.isAttachedToWindow}"
        }
        val setResult = device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        Log.e(
            "SUNDUS_IME_DIAG",
            "post-tap state=[${focusState}] set=[${setResult}] default=[${device.executeShellCommand("settings get secure default_input_method")}]"
        )
        instrumentation.runOnMainSync {
            activity.requestKeyboardForTest()
        }
        device.waitForIdle()

        val keyboardAppeared = device.wait(Until.hasObject(By.text("سندس")), 30_000)
        if (!keyboardAppeared) {
            Log.e("SUNDUS_IME_DIAG", "activity_state=[${focusState}]")
            Log.e("SUNDUS_IME_DIAG", "input_method=[${device.executeShellCommand("dumpsys input_method")}]")
            Log.e("SUNDUS_IME_DIAG", "windows=[${device.executeShellCommand("dumpsys window windows")}]")
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

        val backspace = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key was not found", backspace)
        backspace!!.click()
        assertEquals("Backspace did not delete the previous character", "ض", activeEditor.text.toString())

        val backspaceAgain = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key disappeared after the first deletion", backspaceAgain)
        backspaceAgain!!.click()
        assertEquals("Backspace did not delete the remaining character", "", activeEditor.text.toString())

        val spaceKey = device.wait(Until.findObject(By.text("سندس")), 3_000)
        assertNotNull("Space key was not found", spaceKey)
        spaceKey!!.click()
        assertEquals("Space key did not insert a space", " ", activeEditor.text.toString())

        // Emoji panel must open, insert an emoji, and remain deletable by Backspace.
        val emojiTool = device.wait(Until.findObject(By.text("😀")), 3_000)
        assertNotNull("Emoji access button was not found", emojiTool)
        emojiTool!!.click()
        val emojiKey = device.wait(Until.findObject(By.text("😃")), 3_000)
        assertNotNull("Emoji panel did not open", emojiKey)
        emojiKey!!.click()
        assertEquals("Emoji key did not insert", " 😃", activeEditor.text.toString())
        val emojiBackspace = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key was not available after emoji insertion", emojiBackspace)
        emojiBackspace!!.click()
        assertEquals("Backspace did not remove emoji", " ", activeEditor.text.toString())

        // Quick phrases open as a single dialog, not as a duplicate keyboard panel.
        val phrasesTool = device.wait(Until.findObject(By.text("جملې")), 3_000)
        assertNotNull("Quick phrases button was not found", phrasesTool)
        phrasesTool!!.click()
        assertTrue("Quick phrases dialog did not open",
            device.wait(Until.hasObject(By.text("د سندس چټکې جملې")), 3_000))
        val closePhrase = device.wait(Until.findObject(By.text("بند")), 3_000)
        assertNotNull("Quick phrases dialog close button was not found", closePhrase)
        closePhrase!!.click()

        val clipboardTool = device.wait(Until.findObject(By.text("📋")), 3_000)
        assertNotNull("Clipboard button was not found", clipboardTool)
        clipboardTool!!.click()
        assertNotNull("Clipboard panel did not open", device.wait(Until.findObject(By.text("کاپي")), 3_000))
        val clipboardClose = device.wait(Until.findObject(By.text("بندول")), 3_000)
        assertNotNull("Clipboard close button was not found", clipboardClose)
        clipboardClose!!.click()

        val settingsTool = device.wait(Until.findObject(By.text("⚙")), 3_000)
        assertNotNull("Settings button was not found", settingsTool)
        settingsTool!!.click()
        assertTrue("Settings panel did not open",
            device.wait(Until.hasObject(By.text("د سندس کیبورډ تنظیمات")), 3_000))
    }
}

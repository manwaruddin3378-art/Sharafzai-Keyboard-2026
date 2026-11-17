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
        // EditText accessibility nodes are not consistently exposed by the
        // hosted API-30 emulator. The attached view's measured screen bounds above
        // are authoritative, so tap those coordinates instead of waiting for DESC.
        assertTrue("Could not tap the attached test editor", device.click(tapX, tapY))
        device.waitForIdle()

        var focusState = ""
        var windowFocused = false
        for (attempt in 0 until 40) {
            instrumentation.runOnMainSync {
                activeEditor.requestFocus()
                windowFocused = activity.hasWindowFocus() && activeEditor.hasFocus()
                focusState = "windowFocus=${activity.hasWindowFocus()} editorFocus=${activeEditor.hasFocus()} attached=${activeEditor.isAttachedToWindow}"
                if (windowFocused) activity.requestKeyboardForTest()
            }
            if (windowFocused) break
            // A second tap after first draw brings the Activity in front on slower
            // hosted emulators; do not select the IME while another window is focused.
            device.click(tapX, tapY)
            SystemClock.sleep(250)
        }
        assertTrue("Test Activity never received window focus: $focusState", windowFocused)
        val setResult = device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        Log.e(
            "SUNDUS_IME_DIAG",
            "post-tap state=[${focusState}] set=[${setResult}] default=[${device.executeShellCommand("settings get secure default_input_method")}]"
        )
        instrumentation.runOnMainSync {
            activity.requestKeyboardForTest()
        }
        device.waitForIdle()

        val keyboardAppeared = device.wait(Until.hasObject(By.textContains("SUNDUS")), 30_000)
        if (!keyboardAppeared) {
            Log.e("SUNDUS_IME_DIAG", "activity_state=[${focusState}]")
            Log.e("SUNDUS_IME_DIAG", "input_method=[${device.executeShellCommand("dumpsys input_method")}]")
            Log.e("SUNDUS_IME_DIAG", "windows=[${device.executeShellCommand("dumpsys window windows")}]")
        }
        assertTrue("SUNDUS keyboard did not appear", keyboardAppeared)

        // Adhkar must render inside the keyboard, above its logo and keys.
        val dhikr = device.wait(Until.findObject(By.desc("اذکار")), 3_000)
        assertNotNull("Adhkar ticker is not visible above the keyboard", dhikr)
        val logo = device.wait(Until.findObject(By.textContains("SUNDUS")), 3_000)
        assertNotNull("SUNDUS logo is not visible", logo)
        Log.e("SUNDUS_DHIKR_BOUNDS", "dhikr=${dhikr!!.visibleBounds} logo=${logo!!.visibleBounds}")
        assertTrue("Adhkar must appear above the logo: dhikr=${dhikr!!.visibleBounds}, logo=${logo!!.visibleBounds}", dhikr!!.visibleBounds.top < logo!!.visibleBounds.top)

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

        // The 123 layer must insert real digits without losing the English layout.
        val numbersKey = device.wait(Until.findObject(By.text("123")), 3_000)
        assertNotNull("123 key was not found", numbersKey)
        numbersKey!!.click()
        val digitOne = device.wait(Until.findObject(By.text("1")), 3_000)
        assertNotNull("English digit 1 was not available inside the 123 layer", digitOne)
        digitOne!!.click()
        assertEquals("123 layer did not insert an English digit", "ضq1", activeEditor.text.toString())
        val returnToLetters = device.wait(Until.findObject(By.text("ABC")), 3_000)
        assertNotNull("ABC return key was not found", returnToLetters)
        returnToLetters!!.click()
        assertNotNull("English letters did not return after 123", device.wait(Until.findObject(By.text("q")), 3_000))

        val backspace = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key was not found", backspace)
        backspace!!.click()
        assertEquals("Backspace did not delete the inserted digit", "ضq", activeEditor.text.toString())

        val backspaceAgain = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key disappeared after the first deletion", backspaceAgain)
        backspaceAgain!!.click()
        assertEquals("Backspace did not delete the English character", "ض", activeEditor.text.toString())

        val backspaceThird = device.wait(Until.findObject(By.text("⌫")), 3_000)
        assertNotNull("Backspace key disappeared after the second deletion", backspaceThird)
        backspaceThird!!.click()
        assertEquals("Backspace did not delete the Pashto character", "", activeEditor.text.toString())

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

        // Primary toolbar actions must open their real offline tools from the IME.
        val colorsTool = device.wait(Until.findObject(By.text("🎨 رنګونه")), 3_000)
        assertNotNull("Colors button was not found", colorsTool)
        colorsTool!!.click()
        assertTrue("Colors picker did not open", device.wait(Until.hasObject(By.text("د سندس رنګونه")), 3_000))
        device.wait(Until.findObject(By.text("بند")), 3_000)!!.click()

        val dictionaryTool = device.wait(Until.findObject(By.text("قاموس")), 3_000)
        assertNotNull("Dictionary button was not found", dictionaryTool)
        dictionaryTool!!.click()
        assertTrue("Dictionary home did not open", device.wait(Until.hasObject(By.text("قاموس او لټون")), 3_000))
        device.wait(Until.findObject(By.text("بند")), 3_000)!!.click()

        val translationTool = device.wait(Until.findObject(By.text("ژباړه")), 3_000)
        assertNotNull("Translation button was not found", translationTool)
        translationTool!!.click()
        assertTrue("Translation language picker did not open", device.wait(Until.hasObject(By.text("د ژباړې هدف ژبه وټاکئ")), 3_000))
        device.wait(Until.findObject(By.text("بند")), 3_000)!!.click()

        val searchTool = device.wait(Until.findObject(By.text("لټون")), 3_000)
        assertNotNull("Search button was not found", searchTool)
        searchTool!!.click()
        assertTrue("Offline search dialog did not open", device.wait(Until.hasObject(By.text("د سندس آفلاین لټون")), 3_000))
        device.wait(Until.findObject(By.text("بند")), 3_000)!!.click()

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

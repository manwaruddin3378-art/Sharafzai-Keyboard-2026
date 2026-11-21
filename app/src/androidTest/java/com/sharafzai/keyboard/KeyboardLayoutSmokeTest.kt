package com.sharafzai.keyboard

import android.content.Intent
import android.widget.EditText
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardLayoutSmokeTest {
    @Test
    fun requestedLayoutAndCoreTypingControlsWork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        fun tapKey(key: UiObject) {
            val bounds = key.visibleBounds
            assertTrue("Could not tap visible key at $bounds", device.click(bounds.centerX(), bounds.centerY()))
        }
        val appInfo = instrumentation.targetContext.packageManager
            .getPackageInfo(instrumentation.targetContext.packageName, 0)
        assertEquals("SUNDUS must target Android 16 (API 36)", 36, appInfo.applicationInfo?.targetSdkVersion)
        assertTrue(
            "SUNDUS must remain offline and must not request INTERNET permission",
            appInfo.requestedPermissions?.contains(android.Manifest.permission.INTERNET) != true
        )

        device.executeShellCommand("ime enable com.sharafzai.keyboard/.KeyboardService")
        device.wakeUp()
        runCatching { device.executeShellCommand("wm dismiss-keyguard") }

        val intent = Intent(instrumentation.targetContext, KeyboardTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val activity = instrumentation.startActivitySync(intent) as KeyboardTestActivity
        var editor: EditText? = null
        instrumentation.runOnMainSync {
            editor = activity.findViewById(android.R.id.edit)
            editor?.requestFocus()
        }
        assertNotNull("Test editor was not created", editor)
        val activeEditor = editor!!

        var tapX = -1
        var tapY = -1
        for (attempt in 0 until 40) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
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
        assertTrue("Test editor did not attach", tapX >= 0 && tapY >= 0)
        assertTrue("Could not focus the test editor", device.click(tapX, tapY))

        var focused = false
        for (attempt in 0 until 40) {
            instrumentation.runOnMainSync {
                activeEditor.requestFocus()
                focused = activity.hasWindowFocus() && activeEditor.hasFocus()
                if (focused) activity.requestKeyboardForTest()
            }
            if (focused) break
            device.click(tapX, tapY)
            SystemClock.sleep(250)
        }
        assertTrue("Test editor never received focus", focused)
        device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        instrumentation.runOnMainSync { activity.requestKeyboardForTest() }
        assertTrue(
            "SUNDUS keyboard did not appear",
            device.wait(Until.hasObject(By.textContains("SUNDUS")), 30_000)
        )

        // Verify the header: SUNDUS on the left, analog clock on the right,
        // three calendars in the centered date strip, and the rotating dhikr area.
        assertNotNull("SUNDUS logo is missing", device.wait(Until.findObject(By.desc("SUNDUS – سندس")), 5_000))
        assertNotNull("Analog clock is missing", device.wait(Until.findObject(By.desc("کلاسیک ساعت")), 5_000))
        assertNotNull("Hijri/Solar/Gregorian date strip is missing", device.wait(Until.findObject(By.textContains("قمري")), 5_000))
        assertNotNull("Dhikr display is missing", device.wait(Until.findObject(By.desc("اذکار")), 5_000))

        // The ordinary typing surface must stay free of the removed secondary toolbar.
        assertNull("The old mini-toolbar toggle is still visible", device.findObject(By.text("•••")))
        assertNull("Voice shortcut must be hidden", device.findObject(By.text("غږ")))
        assertNull("Dictionary shortcut must be hidden", device.findObject(By.text("قاموس")))
        assertNull("Translation shortcut must be hidden", device.findObject(By.text("ژباړه")))
        assertNull("Phrases shortcut must be hidden", device.findObject(By.text("جملې")))
        assertNull("Settings shortcut must be hidden", device.findObject(By.text("⚙")))
        assertNull("AutoText shortcut must be hidden", device.findObject(By.text("AutoText")))
        assertNull("Copy button must not be visible", device.findObject(By.text("کاپي")))
        assertNull("Hide-keyboard shortcut must not be visible", device.findObject(By.text("پټول")))

        // The 123 key opens one unified symbols/harakat/Islamic-marks panel.
        val symbolsButton = device.wait(Until.findObject(By.desc("Numbers, harakat and symbols")), 5_000)
        assertNotNull("Symbols button is missing", symbolsButton)
        tapKey(symbolsButton!!)
        assertNotNull("General symbols category is missing", device.wait(Until.findObject(By.desc("نښې")), 3_000))
        val harakatTab = device.wait(Until.findObject(By.desc("اعراب")), 3_000)
        assertNotNull("Harakat category is missing", harakatTab)
        tapKey(harakatTab!!)
        assertNotNull("Arabic harakat are not displayed", device.wait(Until.findObject(By.text("َ")), 3_000))
        assertNotNull("Islamic symbols category is missing", device.findObject(By.desc("اسلامي")))
        val returnToLetters = device.findObject(By.desc("Return to letters"))
        assertNotNull("ABC return key is missing", returnToLetters)
        tapKey(returnToLetters!!)

        // The dedicated emoji key opens a categorized panel containing Islamic emoji.
        val emojiButton = device.wait(Until.findObject(By.desc("Emoji and Islamic emoji")), 5_000)
        assertNotNull("Dedicated emoji button is missing", emojiButton)
        tapKey(emojiButton!!)
        val islamicEmojiTab = device.wait(Until.findObject(By.text("☪️")), 5_000)
        assertNotNull("Islamic emoji category is missing", islamicEmojiTab)
        tapKey(islamicEmojiTab!!)
        assertNotNull("Islamic emoji such as the mosque are missing", device.wait(Until.findObject(By.text("🕌")), 3_000))
        tapKey(device.findObject(By.desc("Emoji and Islamic emoji")))

        // Verify that the spacebar is centered, then exercise space, tatweel and backspace.
        val spacebar = device.wait(Until.findObject(By.text("فاصله")), 5_000)
        assertNotNull("Centered spacebar is missing", spacebar)
        val centerX = spacebar!!.visibleBounds.centerX()
        assertTrue(
            "Spacebar is not centered on the keyboard",
            kotlin.math.abs(centerX - device.displayWidth / 2) <= 45
        )

        val pashtoTab = device.findObject(By.text("پښتو"))
        assertNotNull("Pashto language tab is missing", pashtoTab)
        pashtoTab!!.click()
        instrumentation.runOnMainSync { activeEditor.setText("") }
        device.waitForIdle()
        val letterN = device.wait(Until.findObject(By.text("ن")), 3_000)
        assertNotNull("Pashto letter key is missing", letterN)
        tapKey(letterN!!)
        tapKey(device.findObject(By.text("کش")))
        assertEquals("Tatweel should follow the selected/preceding letter", "نـ", activeEditor.text.toString())
        tapKey(device.findObject(By.text("فاصله")))
        assertEquals("Spacebar did not insert a space", "نـ ", activeEditor.text.toString())
        tapKey(device.findObject(By.text("⌫")))
        assertEquals("Backspace did not remove the previous space", "نـ", activeEditor.text.toString())

        // Language switching and Shift remain available after the layout changes.
        instrumentation.runOnMainSync { activeEditor.setText("") }
        device.findObject(By.text("عربي")).click()
        assertNotNull("Arabic letter keys did not appear", device.wait(Until.findObject(By.text("ض")), 3_000))
        device.findObject(By.text("English")).click()
        val lowerA = device.wait(Until.findObject(By.text("a")), 3_000)
        assertNotNull("English letter A key is missing", lowerA)
        val lowerABounds = lowerA!!.visibleBounds
        val shift = device.wait(Until.findObject(By.text("⇧")), 3_000)
        assertNotNull("Shift key is missing", shift)
        tapKey(shift!!)
        // The uppercase alternate is also shown as a small hint before Shift;
        // tap the unchanged key position so the test activates the actual key body.
        assertTrue("Could not tap the shifted A key", device.click(lowerABounds.centerX(), lowerABounds.centerY()))
        assertEquals("Shifted English key did not type uppercase A", "A", activeEditor.text.toString())
    }
}

package com.sharafzai.keyboard

import android.Manifest
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.EditText
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardFeatureAuditTest {
    private val packageName = "com.sharafzai.keyboard"

    private fun launchEditor(
        instrumentation: Instrumentation,
        device: UiDevice,
        privateEditor: Boolean = false
    ): EditText {
        device.wakeUp()
        runCatching { device.executeShellCommand("wm dismiss-keyguard") }
        runCatching { device.executeShellCommand("input keyevent 82") }
        val launchIntent = Intent(instrumentation.targetContext, KeyboardTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("privateEditor", privateEditor)

        // startActivitySync returns the actual host Activity. Read its EditText directly
        // instead of searching the Android system window by framework resource ID;
        // UIAutomator can report the IME/system window as the current package on API 34.
        val activity = instrumentation.startActivitySync(launchIntent) as KeyboardTestActivity
        var editor: EditText? = null
        instrumentation.runOnMainSync {
            editor = activity.findViewById(android.R.id.edit)
            editor?.requestFocus()
            editor?.performClick()
        }
        assertNotNull("Keyboard test editor view was not created", editor)
        val activeEditor = editor!!
        val keyboardAppeared = device.wait(Until.hasObject(By.text("✦ SUNDUS | سندس ✦")), 15_000)
        if (!keyboardAppeared) {
            Log.e("SUNDUS_IME_DIAG", "input_method=[" + device.executeShellCommand("dumpsys input_method | grep -E 'mCurMethodId|mCurId|mInputShown|mShowRequested|mCurFocusedWindow|mImeWindowVis'") + "]")
            Log.e("SUNDUS_IME_DIAG", "window=[" + device.executeShellCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'") + "]")
        }
        assertTrue("SUNDUS keyboard did not appear", keyboardAppeared)
        return activeEditor
    }

    private fun tap(device: UiDevice, text: String, timeout: Long = 3_000): UiObject2 {
        val item = device.wait(Until.findObject(By.text(text)), timeout)
        assertNotNull("Keyboard control not found: $text", item)
        item!!.click()
        return item
    }

    @Test
    fun keyboardPanelsLanguagesAndCoreActionsAreReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val enableResult = device.executeShellCommand("ime enable $packageName/.KeyboardService")
        val setResult = device.executeShellCommand("ime set $packageName/.KeyboardService")
        Log.e("SUNDUS_IME_DIAG", "enable=[" + enableResult + "] set=[" + setResult + "] list=[" + device.executeShellCommand("ime list -s") + "] default=[" + device.executeShellCommand("settings get secure default_input_method") + "]")
        val editor = launchEditor(instrumentation, device)

        // All four language layouts insert their expected characters.
        tap(device, "پښتو")
        tap(device, "ض")
        tap(device, "دري")
        tap(device, "گ")
        tap(device, "عربي")
        tap(device, "ذ")
        tap(device, "English")
        tap(device, "q")
        assertTrue("One or more language layouts failed", editor.text.contains("ضگذq"))

        // Long press previews and commits the configured alternate without a second tap.
        val qKey = device.wait(Until.findObject(By.text("q")), 3_000)
        assertNotNull("English q key not found for long-press test", qKey)
        qKey!!.longClick()
        assertTrue("Long-press alternate was not inserted", editor.text.endsWith("1"))

        // Symbols: general panel, Arabic diacritics, and Islamic characters.
        tap(device, "123")
        assertTrue("Symbols categories missing", device.wait(Until.hasObject(By.text("اعراب")), 3_000))
        tap(device, "اسلامي")
        tap(device, "ﷺ")
        assertTrue("Islamic symbol did not insert", editor.text.contains("ﷺ"))
        tap(device, "ABC")

        // Emoji panel and its Islamic category.
        tap(device, "😊")
        tap(device, "اسلامي")
        tap(device, "🤲")
        assertTrue("Islamic emoji did not insert", editor.text.contains("🤲"))
        tap(device, "د ایموجي بندول")

        // Theme picker.
        tap(device, "🎨")
        assertTrue("Theme picker did not open", device.wait(Until.hasObject(By.text("د سندس رنګونه")), 3_000))
        tap(device, "بند")

        // Settings panel and its number-row switch.
        tap(device, "⚙")
        assertTrue("Settings panel did not open", device.wait(Until.hasObject(By.text("د سندس کیبورډ تنظیمات")), 3_000))
        val visibleNumbers = device.findObject(By.text("شمېرې: ښکاره"))
        val hiddenNumbers = device.findObject(By.text("شمېرې: پټې"))
        assertTrue("Number-row setting missing", visibleNumbers != null || hiddenNumbers != null)
        (visibleNumbers ?: hiddenNumbers)!!.click()
        tap(device, "⚙")

        // Clipboard, quick phrases, AutoText, offline translation, search, and dictionary.
        tap(device, "📋")
        assertTrue("Clipboard actions did not open", device.wait(Until.hasObject(By.text("کاپي")), 3_000))
        tap(device, "بندول")

        val phraseButton = device.wait(Until.findObject(By.textContains("جملې")), 3_000)
        assertNotNull("Quick phrases button missing", phraseButton)
        phraseButton!!.click()
        assertTrue("Quick phrases dialog did not open", device.wait(Until.hasObject(By.text("د سندس چټکې جملې")), 3_000))
        tap(device, "بند")

        tap(device, "⭐ متن")
        assertTrue("AutoText editor did not open", device.wait(Until.hasObject(By.text("شخصي AutoText لنډیزونه")), 3_000))
        tap(device, "بند")

        val globeButtons = device.findObjects(By.text("🌐"))
        assertTrue("Language and translation toolbar buttons missing", globeButtons.size >= 2)
        globeButtons[1].click()
        assertTrue("Offline translation picker did not open", device.wait(Until.hasObject(By.text("د ژباړې هدف ژبه وټاکئ")), 3_000))
        tap(device, "بند")

        tap(device, "🔎")
        assertTrue("Offline search dialog did not open", device.wait(Until.hasObject(By.text("د سندس آفلاین لټون")), 3_000))
        tap(device, "بند")

        tap(device, "📖")
        assertTrue("Dictionary home did not open", device.wait(Until.hasObject(By.text("قاموس او لټون")), 3_000))
        tap(device, "بند")

        // Voice entry remains present, while the SUNDUS package itself requests no microphone permission.
        assertNotNull("Voice typing control missing", device.findObject(By.text("🎙")))
        val info = instrumentation.targetContext.packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertFalse("SUNDUS must not request RECORD_AUDIO", Manifest.permission.RECORD_AUDIO in requested)

        // Exercise the recognizer-result handoff into the active editor without requiring
        // a microphone or a cloud speech service in the CI emulator.
        instrumentation.targetContext.sendBroadcast(
            Intent(VoiceInputActivity.ACTION_VOICE_TEXT)
                .setPackage(packageName)
                .putExtra(VoiceInputActivity.EXTRA_RECOGNIZED_TEXT, "غږیز ازمایښت")
        )
        assertTrue(
            "Recognized speech was not committed into the active editor",
            device.wait(Until.hasObject(By.textContains("غږیز ازمایښت")), 3_000) ||
                editor.text.contains("غږیز ازمایښت")
        )
    }

    @Test
    fun completedWordLearningHonorsNoPersonalizedLearningFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("SharafzaiSettings", 0)
        prefs.edit()
            .putBoolean("autoLearnWords", true)
            .remove("personalWords_2")
            .commit()

        device.executeShellCommand("ime enable $packageName/.KeyboardService")
        device.executeShellCommand("ime set $packageName/.KeyboardService")

        // Ordinary text fields may learn one completed word locally.
        launchEditor(instrumentation, device)
        tap(device, "English")
        "qzxvbn".forEach { tap(device, it.toString()) }
        tap(device, "✦ SUNDUS | سندس ✦")
        assertTrue(
            "Completed word was not learned in a normal text field",
            prefs.getStringSet("personalWords_2", emptySet()).orEmpty().contains("qzxvbn")
        )

        // The Android private-editor flag must prevent learning in the same keyboard.
        prefs.edit().remove("personalWords_2").commit()
        val privateEditor = launchEditor(instrumentation, device, privateEditor = true)
        tap(device, "English")
        "plmokn".forEach { tap(device, it.toString()) }
        tap(device, "✦ SUNDUS | سندس ✦")
        assertFalse(
            "Keyboard learned a word from an editor that forbids personalized learning",
            prefs.getStringSet("personalWords_2", emptySet()).orEmpty().contains("plmokn")
        )
        val micButton = device.wait(Until.findObject(By.text("🎙")), 3_000)
        assertNotNull("Voice control should remain visible", micButton)
        micButton!!.click()
        assertTrue(
            "Voice entry was not blocked in a private editor",
            device.wait(Until.hasObject(By.text("په پټنوم او ځانګړو خانو کې غږیز لیکل بند دي.")), 3_000)
        )
        assertNotNull(privateEditor)
    }
}

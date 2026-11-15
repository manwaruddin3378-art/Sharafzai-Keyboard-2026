package com.sharafzai.keyboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardFeatureAuditTest {
    private val packageName = "com.sharafzai.keyboard"
    private val component = "$packageName/.KeyboardTestActivity"

    private fun launchEditor(device: UiDevice, privateEditor: Boolean = false): UiObject2 {
        val extra = if (privateEditor) " --ez privateEditor true" else ""
        device.executeShellCommand("am start -W -n $component$extra")
        val editor = device.wait(
            Until.findObject(By.desc("keyboard-test-editor")),
            8_000
        )
        assertNotNull("Keyboard test editor did not open", editor)
        editor!!.click()
        assertTrue(
            "SUNDUS keyboard did not appear",
            device.wait(Until.hasObject(By.text("✦ SUNDUS | سندس ✦")), 8_000)
        )
        return editor
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
        device.executeShellCommand("ime enable $packageName/.KeyboardService")
        device.executeShellCommand("ime set $packageName/.KeyboardService")
        val editor = launchEditor(device)

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
        device.pressBack()

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
        device.pressBack()

        tap(device, "⭐ متن")
        assertTrue("AutoText editor did not open", device.wait(Until.hasObject(By.text("شخصي AutoText لنډیزونه")), 3_000))
        device.pressBack()

        val globeButtons = device.findObjects(By.text("🌐"))
        assertTrue("Language and translation toolbar buttons missing", globeButtons.size >= 2)
        globeButtons[1].click()
        assertTrue("Offline translation picker did not open", device.wait(Until.hasObject(By.text("د ژباړې هدف ژبه وټاکئ")), 3_000))
        device.pressBack()

        tap(device, "🔎")
        assertTrue("Offline search dialog did not open", device.wait(Until.hasObject(By.text("د سندس آفلاین لټون")), 3_000))
        device.pressBack()

        tap(device, "📖")
        assertTrue("Dictionary home did not open", device.wait(Until.hasObject(By.text("قاموس او لټون")), 3_000))
        device.pressBack()

        // Voice entry remains present, while the SUNDUS package itself requests no microphone permission.
        assertNotNull("Voice typing control missing", device.findObject(By.text("🎙")))
        val info = instrumentation.targetContext.packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertFalse("SUNDUS must not request RECORD_AUDIO", Manifest.permission.RECORD_AUDIO in requested)
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
        launchEditor(device)
        tap(device, "English")
        "qzxvbn".forEach { tap(device, it.toString()) }
        tap(device, "✦ SUNDUS | سندس ✦")
        assertTrue(
            "Completed word was not learned in a normal text field",
            prefs.getStringSet("personalWords_2", emptySet()).orEmpty().contains("qzxvbn")
        )

        // The Android private-editor flag must prevent learning in the same keyboard.
        prefs.edit().remove("personalWords_2").commit()
        val privateEditor = launchEditor(device, privateEditor = true)
        tap(device, "English")
        "plmokn".forEach { tap(device, it.toString()) }
        tap(device, "✦ SUNDUS | سندس ✦")
        assertFalse(
            "Keyboard learned a word from an editor that forbids personalized learning",
            prefs.getStringSet("personalWords_2", emptySet()).orEmpty().contains("plmokn")
        )
        assertNotNull(privateEditor)
    }
}

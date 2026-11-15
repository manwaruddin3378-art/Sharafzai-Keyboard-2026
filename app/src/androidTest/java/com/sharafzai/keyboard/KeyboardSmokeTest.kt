package com.sharafzai.keyboard

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

        device.executeShellCommand("ime enable com.sharafzai.keyboard/.KeyboardService")
        device.executeShellCommand("ime set com.sharafzai.keyboard/.KeyboardService")
        device.executeShellCommand("am start -W -n com.sharafzai.keyboard/.KeyboardTestActivity")

        val editor = device.wait(
            Until.findObject(By.clazz("android.widget.EditText")),
            8_000
        )
        assertNotNull("Test editor did not open", editor)
        editor!!.click()

        assertTrue(
            "SUNDUS keyboard did not appear",
            device.wait(Until.hasObject(By.text("✦ SUNDUS | سندس ✦")), 8_000)
        )

        val pashtoKey = device.wait(Until.findObject(By.text("ض")), 3_000)
        assertNotNull("Pashto key was not found", pashtoKey)
        pashtoKey!!.click()
        assertEquals("Pashto key did not insert text", "ض", editor.text)

        val englishTab = device.wait(Until.findObject(By.text("English")), 3_000)
        assertNotNull("English language tab was not found", englishTab)
        englishTab!!.click()

        val englishKey = device.wait(Until.findObject(By.text("q")), 3_000)
        assertNotNull("English key was not found", englishKey)
        englishKey!!.click()
        assertEquals("English key did not insert text", "ضq", editor.text)

        val dictionaryButton = device.wait(Until.findObject(By.text("📖")), 3_000)
        assertNotNull("Dictionary toolbar button was not found", dictionaryButton)
        dictionaryButton!!.click()

        assertTrue(
            "Dictionary dialog did not appear above the keyboard",
            device.wait(Until.hasObject(By.text("قاموس او لټون")), 5_000)
        )
    }
}

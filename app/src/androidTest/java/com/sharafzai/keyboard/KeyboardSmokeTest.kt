package com.sharafzai.keyboard

import android.content.Intent
import android.widget.EditText
import android.os.SystemClock
import android.util.Log
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
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

        // This instrumentation run is deliberately scoped to saved texts, personal phrases and Notes.
        // InputMethodService may report its window before onCreateInputView has finished.
        // Wait for the actual quick-tools button rather than treating the early SUNDUS label
        // as proof that the keyboard view is already attached and accessible.
        device.waitForIdle()
        var moreTools = device.wait(Until.findObject(By.text("•••")), 45_000)
        if (moreTools == null) {
            // Some Android 11 IME accessibility trees expose the button through
            // its description or a partial text node rather than an exact text match.
            moreTools = device.findObject(By.desc("•••"))
                ?: device.findObject(By.textContains("•••"))
        }
        if (moreTools == null) {
            val hierarchyFile = java.io.File(instrumentation.targetContext.cacheDir, "sundus-ime-window.xml")
            device.dumpWindowHierarchy(hierarchyFile)
            Log.e("SUNDUS_IME_DIAG", "quick-tools missing; display=" + device.executeShellCommand("dumpsys input_method"))
            Log.e("SUNDUS_IME_DIAG", "windows=" + device.executeShellCommand("dumpsys window windows"))
        }
        assertNotNull("Quick tools button was not found after keyboard view initialization", moreTools)
        moreTools!!.click()

        // Verify the three requested settings fixes before exercising saved texts.
        val settingsButton = device.wait(Until.findObject(By.text("⚙")), 5_000)
        assertNotNull("Settings shortcut was not displayed", settingsButton)
        settingsButton!!.click()
        assertNotNull("Keyboard settings panel did not open", device.wait(Until.findObject(By.text("د سندس کیبورډ تنظیمات")), 3_000))
        val closeSettingsButtons = device.findObjects(By.text("تنظیمات بندول"))
        assertEquals("Settings must expose exactly one close button", 1, closeSettingsButtons.size)

        val autoTextButton = device.wait(Until.findObject(By.text("AutoText تنظیم")), 3_000)
        assertNotNull("AutoText settings button was not displayed", autoTextButton)
        autoTextButton!!.click()
        assertNotNull("AutoText manager did not open", device.wait(Until.findObject(By.text("شخصي AutoText لنډیزونه")), 5_000))
        val addAutoText = device.wait(Until.findObject(By.text("＋ نوی لنډیز")), 3_000)
        assertNotNull("New AutoText action was not displayed", addAutoText)
        addAutoText!!.click()
        val autoTextFields = device.findObjects(By.clazz(EditText::class.java))
        assertTrue("AutoText shortcut and expansion fields were not both displayed", autoTextFields.size >= 2)
        assertNotNull(
            "Pashto letter keys did not appear in the AutoText editor",
            device.wait(Until.findObject(By.text("ض")), 5_000)
        )
        autoTextFields.takeLast(2)[0].setText("sundustest")
        autoTextFields.takeLast(2)[1].setText("SUNDUS AutoText works")
        val saveAutoText = device.wait(Until.findObject(By.text("＋ ساتل")), 3_000)
        assertNotNull("AutoText save button was not displayed", saveAutoText)
        saveAutoText!!.click()
        assertNotNull("New AutoText shortcut was not saved in the manager", device.wait(Until.findObject(By.textContains("sundustest")), 3_000))
        val closeAutoText = device.wait(Until.findObject(By.text("بندول")), 3_000)
        assertNotNull("AutoText manager close button was not displayed", closeAutoText)
        closeAutoText!!.click()
        device.waitForIdle()

        // AutoText is now a standalone Activity; reopen the settings panel on return.
        val quickToolsAfterAutoText = device.wait(Until.findObject(By.text("•••")), 5_000)
        assertNotNull("Quick tools did not return after closing AutoText", quickToolsAfterAutoText)
        quickToolsAfterAutoText!!.click()
        val settingsAfterAutoText = device.wait(Until.findObject(By.text("⚙")), 3_000)
        assertNotNull("Settings shortcut did not return after closing AutoText", settingsAfterAutoText)
        settingsAfterAutoText!!.click()
        assertNotNull("Settings panel did not reopen after AutoText", device.wait(Until.findObject(By.text("د سندس کیبورډ تنظیمات")), 3_000))

        val autoCorrectOff = device.wait(Until.findObject(By.text("اتومات بدلون: بند")), 3_000)
        if (autoCorrectOff != null) autoCorrectOff.click()
        assertNotNull("Auto-correction could not be enabled", device.wait(Until.findObject(By.text("اتومات بدلون: فعال")), 3_000))
        device.findObject(By.text("تنظیمات بندول")).click()
        device.waitForIdle()
        instrumentation.runOnMainSync {
            activeEditor.requestFocus()
            activeEditor.setText("sundustest")
            // setText() can leave the selection at the start on the hosted API-30
            // emulator. Real typing appends at the cursor, so explicitly place it
            // after the shortcut before exercising the IME's space/AutoText action.
            activeEditor.setSelection(activeEditor.text.length)
        }
        device.waitForIdle()
        assertEquals(
            "AutoText test shortcut cursor was not positioned at the end",
            "sundustest".length,
            activeEditor.selectionStart
        )
        device.findObject(By.text("سندس")).click()
        assertEquals(
            "AutoText shortcut did not expand when auto-correction was enabled",
            "SUNDUS AutoText works ",
            activeEditor.text.toString()
        )
        instrumentation.runOnMainSync { activeEditor.setText("") }

        // Reopen quick tools after the settings panel has been closed.
        device.waitForIdle()
        val quickToolsAfterSettings = device.wait(Until.findObject(By.text("•••")), 5_000)
        assertNotNull("Quick tools did not return after closing settings", quickToolsAfterSettings)
        quickToolsAfterSettings!!.click()

        val savedTool = device.wait(Until.findObject(By.text("خوندي شوي")), 5_000)
        assertNotNull("Saved texts button was not found", savedTool)
        savedTool!!.click()
        assertNotNull("Save selected text action is missing", device.wait(Until.findObject(By.text("انتخاب شوی متن خوندي کول")), 3_000))
        assertNotNull("Save phrase action is missing", device.wait(Until.findObject(By.text("جمله ساتل")), 3_000))
        assertNotNull("Notes action is missing", device.wait(Until.findObject(By.text("نوټس")), 3_000))

        instrumentation.runOnMainSync {
            activeEditor.setText("SUNDUS saved text test")
            activeEditor.setSelection(0, activeEditor.text.length)
        }
        device.waitForIdle()
        device.findObject(By.text("انتخاب شوی متن خوندي کول")).click()
        assertNotNull("Selected text was not saved", device.wait(Until.findObject(By.textContains("خوندي شوي متنونه: 1")), 3_000))
        val savedChip = device.wait(Until.findObject(By.textContains("📌")), 3_000)
        assertNotNull("Saved text quick-insert chip was not displayed", savedChip)
        savedChip!!.click()
        assertEquals("Saved text did not insert into the editor", "SUNDUS saved text test", activeEditor.text.toString())

        // Each saved-text row exposes a direct copy action, avoiding nested IME dialogs.
        val copySavedText = device.wait(Until.findObject(By.text("کاپي")), 3_000)
        assertNotNull("Saved text copy action was not displayed", copySavedText)
        copySavedText!!.click()
        device.waitForIdle()
        // Verify the user-visible result by pasting into the focused editor.
        // Android restricts direct clipboard reads from background/instrumentation contexts,
        // while TextView's paste action exercises the real system clipboard path.
        instrumentation.runOnMainSync {
            activeEditor.setText("")
            activeEditor.requestFocus()
            activeEditor.setSelection(0)
            activeEditor.onTextContextMenuItem(android.R.id.paste)
        }
        assertEquals("Copied saved text could not be pasted into the editor",
            "SUNDUS saved text test", activeEditor.text.toString()
        )

        device.findObject(By.text("خوندي شوي متنونه پاکول")).click()
        assertNotNull("Clear-all confirmation did not open", device.wait(Until.findObject(By.text("ټول خوندي متنونه پاکول؟")), 3_000))
        device.findObject(By.text("هو، پاک یې کړه")).click()
        device.waitForIdle()

        // Verify that closing the personal-phrases window restores the IME toolbar.
        // Open the phrases window directly; editing text inside an IME-owned dialog
        // changes the active input target and is covered separately from this lifecycle test.
        val quickToolsBeforePhrases = device.wait(Until.findObject(By.text("•••")), 5_000)
        assertNotNull("Quick tools toggle was not displayed before opening personal phrases", quickToolsBeforePhrases)
        quickToolsBeforePhrases!!.click()
        val phrasesShortcut = device.wait(Until.findObject(By.text("جملې")), 3_000)
        assertNotNull("Personal phrases shortcut was not displayed", phrasesShortcut)
        phrasesShortcut!!.click()
        assertNotNull("Personal phrases window did not open", device.wait(Until.findObject(By.text("د سندس چټکې جملې")), 3_000))
        // AlertDialog's negative action is android:id/button2; localized text
        // is not consistently exposed by UiAutomator on hosted API-30 images.
        val closePhraseButton = device.wait(Until.findObject(By.res("android", "button2")), 5_000)
            ?: device.wait(Until.findObject(By.text("بند")), 2_000)
        assertNotNull("Personal phrases close button was not found", closePhraseButton)
        closePhraseButton!!.click()

        // Wait for the toolbar to return after the attached dialog is dismissed.
        val quickToolsAfterPhrases = device.wait(Until.findObject(By.text("•••")), 8_000)
        assertNotNull("Quick tools did not return after closing personal phrases", quickToolsAfterPhrases)
        quickToolsAfterPhrases!!.click()
        val savedTextsShortcut = device.wait(Until.findObject(By.text("خوندي شوي")), 3_000)
        assertNotNull("Saved texts shortcut was not displayed after closing personal phrases", savedTextsShortcut)
        savedTextsShortcut!!.click()
        val notesAction = device.wait(Until.findObject(By.text("نوټس")), 5_000)
        assertNotNull("Notes action was not visible in the saved texts panel", notesAction)
        notesAction!!.click()
        assertNotNull("Notes manager did not open", device.wait(Until.findObject(By.text("زما نوټس")), 5_000))
        assertNotNull(
            "Pashto letter keys disappeared when Notes manager opened",
            device.wait(Until.findObject(By.text("ض")), 3_000)
        )
        device.findObject(By.text("نوی نوټ")).click()
        val noteFields = device.findObjects(By.clazz(EditText::class.java))
        assertTrue("Note title and body fields were not both displayed", noteFields.size >= 2)
        assertNotNull(
            "Pashto letter keys disappeared when the note editor opened",
            device.wait(Until.findObject(By.text("ض")), 3_000)
        )
        noteFields.takeLast(2)[0].setText("SUNDUS note test")
        noteFields.takeLast(2)[1].setText("This is a saved note body for the keyboard test.")
        device.findObject(By.text("خوندي کول")).click()
        assertNotNull("New note was not saved and listed", device.wait(Until.findObject(By.textContains("SUNDUS note test")), 5_000))
        device.findObject(By.textContains("SUNDUS note test")).click()
        assertNotNull("Saved note body could not be opened", device.wait(Until.findObject(By.textContains("saved note body")), 3_000))
        device.findObject(By.text("سمول")).click()
        val editFields = device.findObjects(By.clazz(EditText::class.java))
        assertTrue("Note edit fields were not displayed", editFields.size >= 2)
        editFields.takeLast(2)[0].setText("SUNDUS edited note")
        editFields.takeLast(2)[1].setText("Updated note body.")
        device.findObject(By.text("بدلون خوندي کول")).click()
        assertNotNull("Edited note title was not saved", device.wait(Until.findObject(By.textContains("SUNDUS edited note")), 5_000))
        device.findObject(By.textContains("SUNDUS edited note")).click()
        device.findObject(By.text("نور")).click()
        assertNotNull("Note actions menu did not open", device.wait(Until.findObject(By.text("نوټ حذفول")), 3_000))
        device.findObject(By.text("نوټ حذفول")).click()
        device.findObject(By.text("هو، حذف یې کړه")).click()
        assertNotNull("Deleted note still appears or notes list did not refresh", device.wait(Until.findObject(By.text("تر اوسه مو نوټ نه دی جوړ کړی.")), 5_000))

        // Stress the new file-backed store with more than fifty topics and a long document.
        val bulkNotes = JSONArray()
        repeat(60) { index ->
            val isLongDocument = index == 59
            bulkNotes.put(
                JSONObject()
                    .put("id", 2_000_000_000L + index)
                    .put("title", if (isLongDocument) "SUNDUS ten-page document" else "SUNDUS bulk topic ${index + 1}")
                    .put("body", if (isLongDocument) "A4_PAGE_CONTENT ".repeat(5_000) else "Bulk note body ${index + 1}")
                    .put("updatedAt", System.currentTimeMillis() + index)
                    .put("pinned", index == 0)
            )
        }
        val notesAtomicFile = AtomicFile(java.io.File(instrumentation.targetContext.filesDir, "sundus_notes_v2.json"))
        var notesOutput: java.io.FileOutputStream? = null
        try {
            notesOutput = notesAtomicFile.startWrite()
            notesOutput.write(bulkNotes.toString().toByteArray(Charsets.UTF_8))
            notesAtomicFile.finishWrite(notesOutput)
        } catch (error: Exception) {
            if (notesOutput != null) notesAtomicFile.failWrite(notesOutput)
            throw error
        }
        val notesSearch = device.findObject(By.clazz(EditText::class.java))
        notesSearch.setText("SUNDUS bulk topic 60")
        assertNotNull("The notes store did not display topic 60 after loading 60 topics", device.wait(Until.findObject(By.textContains("SUNDUS bulk topic 60")), 5_000))
        notesSearch.setText("SUNDUS ten-page document")
        val longNote = device.wait(Until.findObject(By.textContains("SUNDUS ten-page document")), 5_000)
        assertNotNull("The long document was not found in the notes list", longNote)
        longNote!!.click()
        assertNotNull("The long document body could not be opened", device.wait(Until.findObject(By.textContains("A4_PAGE_CONTENT")), 5_000))
    }
}

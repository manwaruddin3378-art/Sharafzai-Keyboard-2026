# Sharafzai Keyboard – کیبورډ شرفزی

A lightweight Android on-screen keyboard with Pashto, Dari, Arabic, and English layouts.

## Implemented in the current source

- Four selectable keyboard languages: Pashto, Dari, Arabic, and English.
- Offline starter-word suggestions for each language.
- An explicit personal dictionary: tap **＋** to save the word currently being typed. Entries are stored locally and separately for each language.
- Emoji, symbols, and Arabic/Dari diacritics panels.
- Multiple color themes, remembered language and theme selection.
- Shift, English Caps, Tab, cursor arrows, Space, Enter, and repeating Backspace.
- Unicode-aware Backspace handling for supplementary characters and joined emoji.
- English sentence-start capitalization.
- No INTERNET permission is declared; typed text is not sent to a server.

## Important scope notes

This is an actively developed first version. The built-in word lists are starter lists, not complete dictionaries or full spell-checking models. Voice typing, full offline translation, calculator, calendar, prayer-time tools, and a separate settings screen are not yet implemented. The Pashto and Dari key layouts still need review against the preferred standard layouts before release.

## Build

The GitHub Actions workflow builds a debug APK with Android SDK 34 and Java 17. Install and test the APK only after the planned feature set and final review are complete.

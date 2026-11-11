# Sharafzai Keyboard – کیبورډ شرفزی

A lightweight Android on-screen keyboard with Pashto, Dari, Arabic, and English layouts.

## Implemented in the current source

- Four selectable keyboard languages: Pashto, Dari, Arabic, and English.
- Offline starter-word suggestions for each language.
- An explicit personal dictionary: tap **＋** to save the word currently being typed. Entries are stored locally and separately for each language.
- Emoji, symbols, and Arabic/Dari diacritics panels.
- An offline calculator panel with parentheses and basic arithmetic; results are inserted into the active text field.
- Quick shortcuts to open the device's installed Calendar and Clock/Alarms apps; these use Android system apps rather than storing calendar events or managing alarms inside the keyboard.
- Multiple color themes, remembered language and theme selection.
- Three adjustable key sizes, remembered locally.
- Shift, English Caps, Tab, cursor arrows, Space, Enter, and repeating Backspace.
- Unicode-aware Backspace handling for supplementary characters and joined emoji.
- English sentence-start capitalization.
- No INTERNET permission is declared; typed text is not sent to a server.

## Important scope notes

This is an actively developed first version. The built-in word lists are starter lists, not complete dictionaries or full spell-checking models. Voice typing, full offline translation, built-in calendar/event management, prayer-time calculation, and a separate settings screen are not yet implemented. Calendar and alarm buttons are shortcuts to the device's existing system apps. The calculator is a basic offline tool for addition, subtraction, multiplication, and division; it is not a scientific calculator. The Pashto and Dari key layouts still need review against the preferred standard layouts before release.

## Build

The GitHub Actions workflow builds a debug APK with Android SDK 34 and Java 17. Install and test the APK only after the planned feature set and final review are complete.

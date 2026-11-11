# Sharafzai App – شرفزی اپلیکیشن

A lightweight, monochrome Android utility app that brings the Sharafzai multilingual keyboard and everyday tools together in one application.

## Main features

- **Sharafzai keyboard:** Pashto, Dari, Arabic, and English layouts; offline starter-word suggestions; personal word list; emoji, symbols, diacritics, adjustable key size, and keyboard settings. The keyboard works independently as an Android input method in other apps.
- **Smart calculator:** basic arithmetic for everyday use, available offline.
- **Calendar and notes:** month navigation and locally saved date notes.
- **Alarms and reminders:** local daily-time alarms and notifications; Android may require exact-alarm and notification permissions.
- **Adhkar:** general, morning, and evening remembrance screens, plus a home-screen adhkar widget.
- **Phrases:** Islamic and everyday phrases that can be copied into other apps.
- **My PDF Library:** import and organize personal PDFs, open them in the app, and search their text.
- **Scanned-PDF OCR:** offline recognition for Pashto, Dari/Persian, Arabic, and English pages. OCR can be slower on large books and accuracy depends on scan quality.
- **Voice tools:** speech-to-text through the Android speech-recognition service and text-to-speech through the device's installed speech engine. Language availability varies by device; speech recognition may require internet.
- **Scholar audio library:** import audio lectures and speeches; imported files can be played offline.
- **Online dictionary and translation:** multilingual word lookup through FreeDictionaryAPI (Wiktionary-derived data) and text translation through MyMemory. Internet access is required; coverage and free-service limits may vary.
- **AI shortcut:** opens ChatGPT in the installed app or browser. AI conversations are not embedded inside Sharafzai App.
- **Monochrome interface:** black, white, and gray styling.

## Offline and online behavior

Most tools—including the keyboard, calculator, calendar, alarms, adhkar, PDF library, OCR, and imported audio playback—are designed to work offline.

Internet is used for online dictionary lookup, text translation, and opening the ChatGPT service. Speech recognition may also use an online service depending on the Android device. The keyboard itself does not send typed text to an online server; text is sent to the translation provider only when the user deliberately submits it from the translation screen.

## Android compatibility

- Minimum Android version: **Android 5.0 (API 21)**.
- Target SDK: Android 14 (API 34).
- Application ID: `com.sharafzai.keyboard`.
- The release build includes ARM and other native-library support supplied by its dependencies. Actual behavior still needs testing on physical older and newer devices before a general release.

## Privacy and local storage

PDFs and audio files imported into the app, as well as notes and settings, are stored in the app's private storage. Clearing app data or uninstalling the app removes these local copies. Keep separate backups of important personal documents and recordings.

## Dictionary data attribution

Dictionary entries are provided by **FreeDictionaryAPI**, using data derived from **Wiktionary** and licensed under **CC BY-SA 4.0**. Where available, the app displays the original source link and attribution. Translation is provided separately by MyMemory.

## Build

GitHub Actions builds an optimized release APK using Android SDK 34 and Java 17. The release build enables code shrinking and resource shrinking. The latest successful release build produced an APK of approximately **23 MB**.

The APK is intended for manual installation. Complete feature verification on physical devices before treating the app as a production release.

# Sharafzai App 1.0 – شرفزی اپلیکیشن

The first public-ready milestone is intentionally small, offline-first, and focused on everyday use.

## Included in version 1.0

- **Sharafzai keyboard:** Pashto, Dari, Arabic, and English layouts; offline starter-word suggestions; personal dictionary; symbols, emoji, and diacritics. It can be enabled separately in Android keyboard settings and used in other apps.
- **Calculator:** basic arithmetic, including parentheses.
- **Calendar and notes:** month navigation and locally saved date notes.
- **Alarms and reminders:** locally scheduled alarms and notifications. Exact-alarm and notification permissions may be requested by Android.
- **Adhkar:** selected general, morning, and evening remembrances, with a home-screen widget.
- **Useful phrases:** selected Islamic and everyday phrases.
- **PDF library and reader:** add PDF files from the phone, keep a library list, and read pages inside Sharafzai App using Android's built-in PDF renderer.
- **Monochrome interface:** black, white, and gray.

## Deliberately postponed to later updates

Online dictionary and translation, AI integration, speech-to-text, text-to-speech, scholar audio library, scanned-PDF OCR, and advanced PDF search are not part of the 1.0 home-screen feature set. They can be introduced in later versions after the lightweight base app is released and used.

## Offline behavior and privacy

The core version is designed to work offline. It does not request Internet permission. Calendar notes, alarms, and app settings are stored locally. Imported PDFs are referenced from the selected document provider; removing a book from the in-app list does not delete the original file.

## Android compatibility

- Minimum Android version: **Android 5.0 (API 21)**.
- Target SDK: Android 14 (API 34).
- Application ID: `com.sharafzai.keyboard`.
- The package ID is retained so future updates can use the same app identity. Physical-device verification is still required before claiming full compatibility across all devices.

## Build

GitHub Actions creates an optimized release APK using Java 17 and Android SDK 34. Code and resource shrinking are enabled. The first-stage build must be measured after compilation; no APK size is promised until the actual output is reported.

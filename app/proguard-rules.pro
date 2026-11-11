# Preserve JNI entry points used by the native Tesseract OCR library.
-keep class com.googlecode.tesseract.android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve JNI entry points used by the native Tesseract OCR library.
-keep class com.googlecode.tesseract.android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# PDFBox supports JPX images through an optional decoder that is not bundled.
-dontwarn com.gemalto.jp2.**

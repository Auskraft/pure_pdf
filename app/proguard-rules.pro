# PdfiumAndroid uses JNI; keep its classes so native bindings resolve under R8.
-keep class io.legere.pdfiumandroid.** { *; }
-dontwarn io.legere.pdfiumandroid.**

# Enum constant names are persisted via .name in DataStore and read back with valueOf();
# keep them so settings (accent / view / density) survive obfuscation.
-keepclassmembers enum com.auskraft.purepdf.** { *; }

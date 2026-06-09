# PdfiumAndroid uses JNI; keep its classes so native bindings resolve under R8.
-keep class io.legere.pdfiumandroid.** { *; }
-dontwarn io.legere.pdfiumandroid.**

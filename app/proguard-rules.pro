# F1 Pro release build (R8).
# Libraries (Compose, Glance, WorkManager, Coil, OkHttp, Kyant Backdrop) ship their own rules;
# manifest components (activity, receivers, widget receivers, tile service) are kept by AAPT.

# Readable stack traces in the on-device crash report.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Workers are created by WorkManager from their class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Enum names are saved in state (tab keys, prefs).
-keepclassmembers enum app.f1pro.** { *; }

# OkHttp's optional TLS providers aren't on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

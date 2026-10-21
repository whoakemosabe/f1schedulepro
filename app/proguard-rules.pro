# F1 Pro release build (R8).
# Libraries (Compose, Glance, WorkManager, Coil, OkHttp, Kyant Backdrop) ship their own rules;
# manifest components (activity, receivers, widget receivers, tile service) are kept by AAPT.

# Readable stack traces in the on-device crash report.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Workers: WorkManager ships its own rule that keeps worker constructors, so none needed here.

# Enum names are saved in state (tab keys, prefs): keep the constants' names and the
# values()/valueOf() lookups, but let R8 optimise the rest of each enum.
-keepclassmembers,allowoptimization enum app.f1pro.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp's optional TLS providers aren't on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

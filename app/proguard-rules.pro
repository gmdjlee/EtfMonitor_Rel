# ============================================
# ProGuard Rules for EtfMonitor
# ============================================

# --------------------------------------------
# General Settings
# --------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

# Keep line numbers for better stack traces (optional, aids debugging)
-renamesourcefileattribute SourceFile

# --------------------------------------------
# Room Database
# --------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# --------------------------------------------
# Room Entity Classes
# --------------------------------------------
-keep class com.etfmonitor.core.database.entities.** { *; }


# --------------------------------------------
# Kotlinx Serialization
# --------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.etfmonitor.**$$serializer { *; }
-keepclassmembers class com.etfmonitor.** {
    *** Companion;
}
-keepclasseswithmembers class com.etfmonitor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# --------------------------------------------
# Kotlin Coroutines
# --------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --------------------------------------------
# Hilt Dependency Injection
# --------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keepclasseswithmembers class * {
    @dagger.* <fields>;
}
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keepclassmembers class * {
    @javax.inject.* *;
}

# Keep Hilt generated classes
-keep class **_HiltModules* { *; }
-keep class **_Factory* { *; }
-keep class **_MembersInjector* { *; }

# --------------------------------------------
# AI API Client Models
# --------------------------------------------
-keep class com.etfmonitor.ai.** { *; }
-keepclassmembers class com.etfmonitor.ai.** { *; }

# --------------------------------------------
# OkHttp
# --------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --------------------------------------------
# AndroidX Security Crypto
# --------------------------------------------
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --------------------------------------------
# MPAndroidChart
# --------------------------------------------
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --------------------------------------------
# Vico Charts
# --------------------------------------------
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# --------------------------------------------
# WorkManager
# --------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }

# --------------------------------------------
# Repository and ViewModel Classes
# --------------------------------------------
-keep class com.etfmonitor.repository.** { *; }
-keep class com.etfmonitor.ui.screens.**ViewModel { *; }
-keepclassmembers class com.etfmonitor.ui.screens.**ViewModel {
    <init>(...);
}

# --------------------------------------------
# Data Classes and Models
# --------------------------------------------
-keep class com.etfmonitor.analysis.** { *; }
-keep class com.etfmonitor.oscillator.** { *; }

# --------------------------------------------
# Enum Classes
# --------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --------------------------------------------
# Parcelable
# --------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --------------------------------------------
# Native Methods
# --------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# --------------------------------------------
# Service Classes
# --------------------------------------------
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# --------------------------------------------
# Remove Debug Logging in Release Builds
# --------------------------------------------
# Strip verbose and debug logs from release builds
# This is a safety net in addition to BuildConfig.DEBUG checks in AppLogger
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# --------------------------------------------
# R8 Full Mode Compatibility
# --------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep Python/Chaquopy
-keep class com.chaquo.python.** { *; }

# Keep Data classes
-keep class com.etfmonitor.database.entities.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepattributes SourceFile,LineNumberTable
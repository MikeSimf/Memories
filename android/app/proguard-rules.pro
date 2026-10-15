# --- DnD Memory: правила ProGuard/R8 ---------------------------------------

# Gson работает по reflection над DTO и Room-сущностями.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# DTO сетевого слоя сериализуются Gson по именам полей.
-keep class ru.dndmemory.data.remote.dto.** { *; }
-keep class ru.dndmemory.data.model.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# WorkManager создаёт воркеры по имени класса.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

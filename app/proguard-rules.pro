# GamersHub ProGuard rules

# Keep names readable — obfuscation breaks the Shizuku user-service (loaded by
# class name inside the Shizuku daemon) and makes crash reports unhelpful.
-dontobfuscate

# Shizuku AIDL service stub must stay intact (it is instantiated reflectively by the Shizuku server)
-keep class com.gamershub.core.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gamershub.**$$serializer { *; }
-keepclassmembers class com.gamershub.** {
    *** Companion;
}
-keepclasseswithmembers class com.gamershub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used by reflection-free JSON anyway
-keep class com.gamershub.core.profiles.** { *; }

# OkHttp / OkIO
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose
-dontwarn androidx.compose.**

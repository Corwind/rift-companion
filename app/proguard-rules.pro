# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.riftcompanion.app.data.api.dto.** { *; }
-keep class com.riftcompanion.app.domain.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.riftcompanion.app.**$$serializer { *; }
-keepclassmembers class com.riftcompanion.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.riftcompanion.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Error Prone annotations (needed by Tink/crypto)
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }

# Tink (used by EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Coil
-dontwarn coil3.**
-keep class coil3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# AndroidX Security
-keep class androidx.security.crypto.** { *; }


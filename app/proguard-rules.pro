# kotlinx.serialization generates serializers as companion objects the shrinker
# cannot see referenced. Without these the release build parses every API
# response into an exception — and the debug build stays fine, so it is only
# found on a real device.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.wakaroute.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.wakaroute.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wakaroute.core.**$$serializer { *; }

# OkHttp references these only on platforms we do not run on.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink, underneath EncryptedSharedPreferences, is annotated with Error Prone
# markers that are compile-time only and deliberately not shipped. Their absence
# is not a missing dependency — R8 simply cannot tell the difference and fails
# the release build. Debug never sees this.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

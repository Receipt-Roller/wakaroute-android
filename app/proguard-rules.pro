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

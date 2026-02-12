# Seeker Verify ProGuard Rules

# Keep seeker-verify library classes
-keep class com.midmightbit.sgt.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.seekerverify.app.**$$serializer { *; }
-keepclassmembers class com.seekerverify.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.seekerverify.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Solana Mobile Wallet Adapter
-keep class com.solana.mobilewalletadapter.** { *; }

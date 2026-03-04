# Seeker Verify ProGuard Rules

# Keep Application and Activity classes
-keep class com.seekerverify.app.MainActivity { *; }

# Keep seeker-verify library classes
-keep class com.midmightbit.sgt.** { *; }
-dontwarn com.midmightbit.sgt.**

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

# Google Tink / Crypto (used by AndroidX Security Crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Solana Mobile Wallet Adapter
-keep class com.solana.mobilewalletadapter.** { *; }
-dontwarn com.solana.mobilewalletadapter.**

# WorkManager — workers instantiated via reflection
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.seekerverify.app.worker.DailyCheckInWorker { *; }
-keep class com.seekerverify.app.worker.WidgetRefreshWorker { *; }

# Widget provider — instantiated via reflection
-keep class com.seekerverify.app.widget.SeekerWidgetProvider { *; }

# ViewModels — instantiated via reflection by ViewModelProvider.Factory
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Jetpack Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }

# Strip debug and verbose logs from release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

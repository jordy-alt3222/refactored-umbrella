# R8 rules for re-optimising ServiceFinder-debug-2.apk from its dex.
# The libraries' own consumer rules are not packaged in an APK, so the ones this app needs are restated here.

-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*,RuntimeVisible*Annotations,AnnotationDefault
-ignorewarnings
-dontwarn **

# Manifest entry points (activity, startup provider + initializers, profile installer).
-keep class com.servicefinder.app.MainActivity { <init>(); }
-keep class androidx.core.app.CoreComponentFactory { <init>(); }
-keep class androidx.startup.InitializationProvider { <init>(); }
-keep class * implements androidx.startup.Initializer { <init>(); }
-keep class androidx.profileinstaller.ProfileInstallReceiver { <init>(); }
-keep class androidx.emoji2.text.EmojiCompatInitializer { <init>(); }
-keep class androidx.lifecycle.ProcessLifecycleInitializer { <init>(); }
-keep class okhttp3.internal.platform.PlatformInitializer { <init>(); }

# ViewModels are created reflectively by the default ViewModelProvider factory.
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# WebView JavaScript bridge (rego lookup).
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# Coroutines ServiceLoader entries.
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.servicefinder.app.**$$serializer { *; }
-keepclassmembers class com.servicefinder.app.** { *** Companion; }
-keepclasseswithmembers class com.servicefinder.app.** { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }

# Enums looked up by name.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Anthropic Java SDK (Jackson + Kotlin reflection), following the SDK's shipped rules.
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepclassmembers class com.fasterxml.jackson.** extends java.lang.Enum { <fields>; public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers @interface com.fasterxml.jackson.annotation.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.core.type.TypeReference { *; }
-keep class * extends com.fasterxml.jackson.core.type.TypeReference { *; }
# The Claude lookup can't be exercised without network access in testing, so the SDK is kept whole
# rather than trusting reflection-driven keep rules. It only loads when a lookup runs.
-keep class com.anthropic.** { *; }
-keep class com.github.victools.** { *; }
-keepclassmembers class * extends com.fasterxml.jackson.databind.JsonDeserializer { <init>(...); }
-keepclassmembers class * extends com.fasterxml.jackson.databind.JsonSerializer { <init>(...); }

# Strip Compose debug tooling calls (what the Compose library's release rules do).
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer, java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer, int, java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
    boolean isTraceInProgress();
    void traceEventStart(int, int, int, java.lang.String);
    void traceEventEnd();
}
-assumevalues class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress() return false;
}

# Kotlin null-check intrinsics on parameters are pure overhead in a release build.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
}

# Coroutines debug probes off.
-assumevalues class kotlinx.coroutines.DebugKt {
    boolean getASSERTIONS_ENABLED() return false;
    boolean getDEBUG() return false;
    boolean getRECOVER_STACK_TRACES() return false;
}

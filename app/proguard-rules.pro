# ===========================================================================================
# ScanForge R8 / ProGuard rules (release minify + resource shrinking).
# Compose, Hilt/Dagger, Room and ML Kit ship their own consumer rules; the entries below cover the
# bits R8 cannot infer on its own: kotlinx.serialization, the OpenCV JNI bridge, persisted enum
# names, and the reflection-free models we round-trip through Json/DataStore.
# ===========================================================================================

# --- Keep line numbers for readable crash reports, then hide the original source file name. ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,InnerClasses

# --- kotlinx.serialization -------------------------------------------------------------------
# Keep generated serializers and the companion .serializer() accessors for every @Serializable type.
-dontnote kotlinx.serialization.**
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keep,includedescriptorclasses class com.scanforge.**$$serializer { *; }
-keepclassmembers class com.scanforge.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Domain models round-tripped via Json + DataStore (persist enum .name, must not be renamed). ---
-keep class com.scanforge.core.domain.model.** { *; }
-keepclassmembers enum com.scanforge.core.domain.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# @Serializable payloads (export options, OCR document, page processing, backup manifest, sync).
-keep @kotlinx.serialization.Serializable class com.scanforge.** { *; }

# --- OpenCV: classes are reached from native code via JNI; keep them and their native methods. ---
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** {
    native <methods>;
}
-dontwarn org.opencv.**

# --- ML Kit text recognition (bundled, on-device). Mostly self-keeping; silence missing optionals. ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# --- Hand-rolled PDF writer + embedded TrueType parser: pure Kotlin, no reflection, safe to shrink,
#     but keep public entry points so partial-inlining never drops the searchable-text path. ---
-keep class com.scanforge.core.export.pdf.** { public *; }

# --- WorkManager Hilt workers are instantiated reflectively by the HiltWorkerFactory. ---
-keep class * extends androidx.work.ListenableWorker

# --- Coroutines: standard, avoids spurious warnings. ---
-dontwarn kotlinx.coroutines.**

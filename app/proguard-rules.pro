# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization — protect @Serializable classes (including private ones) from R8 stripping
-keep @kotlinx.serialization.Serializable class com.github.username.cardapp.** { *; }

# ML Kit text recognition — keep model classes loaded via reflection
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }

# CameraX — keep lifecycle-aware components
-keep class androidx.camera.** { *; }

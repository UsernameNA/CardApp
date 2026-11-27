# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit text recognition finds its components by reflection. Without these rules R8
# strips them and TextRecognition.getClient() throws a NullPointerException, so the scan
# screen crashes on open in release builds only (v1.7.0). Don't remove them.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }

# kotlinx.serialization — protect @Serializable classes (including private ones) from R8 stripping
-keep @kotlinx.serialization.Serializable class com.github.username.cardapp.** { *; }

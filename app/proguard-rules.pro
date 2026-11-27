# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization — protect @Serializable classes (including private ones) from R8 stripping
-keep @kotlinx.serialization.Serializable class com.github.username.cardapp.** { *; }

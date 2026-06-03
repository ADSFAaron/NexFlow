# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt file.

# Keep Hilt-generated code
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep Room entities
-keep class com.nexflow.data.local.entity.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.nexflow.** {
    @kotlinx.serialization.Serializable <methods>;
}

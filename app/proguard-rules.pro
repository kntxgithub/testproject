-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.caltrack.api.** { *; }
-keep class com.example.caltrack.data.** { *; }

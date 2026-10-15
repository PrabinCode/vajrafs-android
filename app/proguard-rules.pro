# Proguard rules for VajraFS
-keepattributes *Annotation*
-dontwarn java.lang.management.**
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

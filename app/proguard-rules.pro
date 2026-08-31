# Keep attributes required for reflection and serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization generated classes
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

# Keep AetherHop domain model data classes
-keep class com.kadhafi.aetherhop.domain.model.** { *; }

# Keep Coroutines internal dispatcher classes
-keep class kotlinx.coroutines.** { *; }

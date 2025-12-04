# MicroTempo ProGuard Rules

# Keep NTP-related classes (network packet parsing)
-keepclassmembers class com.microtempo.PreciseNtpClock {
    *;
}

# Keep data classes used with JSON parsing
-keepclassmembers class com.microtempo.WeatherData {
    *;
}

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

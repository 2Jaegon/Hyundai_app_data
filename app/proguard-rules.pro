# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve source file and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 1. Hyundai Pleos Connect SDKs (Vehicle, FusedLocation, TextToSpeech, SpeechToText, NaviHelper)
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# 2. MapLibre Native Android SDK (JNI bindings & Style/GeoJSON models)
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn org.maplibre.**

# 3. Kotlin Reflection & Coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# 4. App Domain & UI Models
-keep class com.example.hyundai.model.** { *; }

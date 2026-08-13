# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firebase and Google Play Services models are mostly covered by consumer rules,
# but these app models are serialized through maps/JSON and should keep members.
-keep class com.example.agriscout.data.local.** { *; }
-keep class com.example.agriscout.detection.** { *; }
-keep class com.example.agriscout.data.remote.** { *; }

# Keep Firebase Messaging service entry points.
-keep class com.example.agriscout.messaging.** { *; }
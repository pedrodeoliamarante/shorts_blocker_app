# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces
-renamesourcefileattribute SourceFile

# Keep the accessibility service - required for it to work properly
-keep class com.shortsblocker.app.ShortBlockAccessibilityService { *; }

# Keep MainActivity
-keep class com.shortsblocker.app.MainActivity { *; }

# Keep all classes referenced in AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep R8 from stripping the Accessibility Service metadata
-keepattributes *Annotation*

# Keep setters in Views so that animations can still work.
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

# Firebase Crashlytics (if added later)
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

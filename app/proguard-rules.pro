# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/antoine/Android/adt-bundle/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#
# Dont touch WebView Javascript Interface
-keepclassmembers class com.ayuget.redface.ui.view.TopicPageView.JsInterface {
    public *;
}

-keepclassmembers class com.ayuget.redface.ui.view.SmileySelectorView.JsInterface {
    public *;
}

# Only obfuscate
-dontshrink

# Keep source file and line numbers for better crash logs
-keepattributes SourceFile,LineNumberTable

# Avoid throws declarations getting removed from retrofit service definitions
-keepattributes Exceptions

# Allow obfuscation of android.support.v7.internal.view.menu.**
# to avoid problem on Samsung 4.2.2 devices with appcompat v21
# see https://code.google.com/p/android/issues/detail?id=78377
-keep class !android.support.v7.internal.view.menu.** { *; }

# ButterKnife uses some annotations not available on Android.
-dontwarn butterknife.internal.**
# Prevent ButterKnife annotations from getting renamed.
-keepnames class * { @butterknife.InjectView *;}

# Do not touch Otto annotated classes
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.squareup.otto.Subscribe public *;
    @com.squareup.otto.Produce public *;
}

# Keep retrofit
-keep class retrofit.** { *; }
-keep class com.ayuget.redface.data.api.model.** { *; }
-keepclassmembernames interface * {
    @retrofit.http.* <methods>;
}

# OkHttp has some internal stuff not available on Android.
-dontwarn com.squareup.okhttp.internal.**

# Okio has some stuff not available on Android.
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature
# Gson specific classes
-dontwarn sun.misc.Unsafe

# OkHttp has some internal stuff not available on Android.
-dontwarn com.squareup.okhttp.internal.**

# Okio has some stuff not available on Android.
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Retrofit has some optional dependencies we don't use.
-dontwarn rx.**
-dontwarn retrofit.appengine.**

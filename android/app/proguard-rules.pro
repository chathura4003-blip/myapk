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

# ProGuard rules for Cloud Drive Leech Standalone Android Edition

# 1. NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# 2. AndroidX Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 3. Room Database & SQLite
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# 4. Gson & Data Models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class com.clouddrive.leech.extractor.models.** { *; }
-keep class com.clouddrive.leech.database.entities.** { *; }

# 5. OkHttp, Jsoup & Coroutines
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class kotlinx.coroutines.** { *; }

# 6. Capacitor Native Plugins
-keep class com.getcapacitor.** { *; }
-keep class com.clouddrive.leech.plugins.** { *; }
-keepclassmembers class com.clouddrive.leech.plugins.** {
    @com.getcapacitor.PluginMethod public *;
}

# 7. Native LibTorrent4j BitTorrent Engine
-keep class org.libtorrent4j.** { *; }
-keep class org.libtorrent4j.swig.** { *; }
-keepclassmembers class org.libtorrent4j.swig.** { *; }
-dontwarn org.libtorrent4j.**

# 8. Gomobile / Libv2ray Xray Core
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-dontwarn libv2ray.**

# 9. WebView Javascript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 10. AndroidX Security Crypto & Tink
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# 11. Glide Image Loader
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

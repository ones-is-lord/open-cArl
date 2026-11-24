-keep class dev.opencarl.terminal.NativePty { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn org.apache.commons.compress.**

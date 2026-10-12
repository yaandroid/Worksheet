# Keep WebView JavaScript interface methods (called from JS)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.brahm.office.ui.DocActivity$Bridge { *; }

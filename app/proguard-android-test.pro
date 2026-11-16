# Error Prone annotations on AndroidX Test are compile-time metadata.
# javax.lang.model.* only exists on a desktop JDK, not on Android devices.
# This rule is applied to the test APK only, never the published Yomu APK.
-dontwarn javax.lang.model.element.**

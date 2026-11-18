# Seeds — R8 rules. Room, WorkManager and Compose ship their own consumer rules.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Widget providers & receivers are referenced from the manifest (kept by AAPT rules).

# osmdroid (maps for runs) ships no consumer rules; keep it intact.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

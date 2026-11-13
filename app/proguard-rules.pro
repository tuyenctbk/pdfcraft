# ProGuard / R8 Rules for PDFCraft Release Obfuscation and Optimization

# Retain line numbers and source file attributes for Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database and Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# PDFBox Android (Apache PDFBox ported for Android)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn javax.xml.stream.**
-dontwarn org.bouncycastle.**

# ZXing Core
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Google Play In-App Billing
-keep class com.android.billingclient.api.** { *; }

# Coil Image Loader
-dontwarn io.coilkt.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Firebase
-dontwarn com.google.firebase.**


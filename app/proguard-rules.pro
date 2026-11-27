# ProGuard / R8 Rules for PDFCraft Release Obfuscation and Optimization

# Retain line numbers and source file attributes for Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class *_*Impl { *; }
-dontwarn androidx.room.paging.**

# Kotlin Metadata & Serialization
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

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

# Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**



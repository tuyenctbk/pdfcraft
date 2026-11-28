# ProGuard / R8 Rules for PDFCraft Release Obfuscation and Optimization

# Retain line numbers and source file attributes for Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations and generic signatures required by Compose/Room/Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database, DAOs, and Entities (allow obfuscation of non-reflective internals)
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keep,allowobfuscation @androidx.room.Entity class * { *; }
-keep,allowobfuscation @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Suppress benign warnings from optional desktop/JavaSE references in third-party libraries
# (PDFBox-Android, Firebase, Billing, and Room all bundle their own official consumer ProGuard rules)
-dontwarn kotlin.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn javax.xml.stream.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.zxing.**
-dontwarn io.coilkt.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**




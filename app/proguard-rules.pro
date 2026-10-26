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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Room: entity dibaca oleh ekspor/impor cadangan lewat getter Kotlin (tanpa refleksi), jadi properti dijaga.
# Kode DAO dan database yang dihasilkan Room dibawa oleh consumer rules Room.
-keep class com.temankuliah.app.data.local.*Entity { *; }
-keep class * extends androidx.room.RoomDatabase

# ML Kit text recognition membawa consumer rules sendiri; ini hanya membungkam
# peringatan untuk kelas opsional yang tidak dipakai.
-dontwarn com.google.mlkit.**

# org.json adalah bagian platform Android (tidak dibundel). Aturan ini hanya pengaman agar tidak dihapus.
-keep class org.json.** { *; }

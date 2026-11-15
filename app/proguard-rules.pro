# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.zcal.**$$serializer { *; }
-keepclassmembers class app.zcal.** {
    *** Companion;
}
-keepclasseswithmembers class app.zcal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor & OkHttp
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Ktor on Android (R8 sees JVM-only classes referenced by the client)
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# Hilt EntryPoint used by the Glance widget
-keep @dagger.hilt.EntryPoint interface * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

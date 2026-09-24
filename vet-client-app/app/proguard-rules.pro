# Proguard rules for Livestock Vet Client
-keep class com.livestock.vetclient.data.model.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static abstract ** getInstance(...);
}

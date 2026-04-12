# Proguard rules for LeadHunters

# Preserve Hilt/Dagger generated classes
-keep class com.example.leadhunters.di.** { *; }
-keep class * {
    @dagger.hilt.android.lifecycle.HiltViewModel public <methods>;
}

# Retrofit & Gson
-keep class com.example.leadhunters.data.remote.model.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomOpenHelper

# Firebase & Crashlytics
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Preserve line numbers for better reports in Crashlytics
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
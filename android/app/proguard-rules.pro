# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Gson: DTO разбираются рефлексией, имена полей заданы @SerializedName
-keepattributes AnnotationDefault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.voicehabit.tracker.data.remote.dto.** { *; }
-dontwarn com.google.gson.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Enum Priority: имена констант попадают в Room (TaskEntity.priority = priority.name)
# и приходят из ответа модели ("HIGH"/"MEDIUM"/"LOW"), поэтому константы переименовывать
# нельзя — иначе release-сборка молча читает всё как MEDIUM. allowobfuscation здесь
# запрещён намеренно: он действует и на членов, и снял бы имена констант.
-keepclassmembers enum com.voicehabit.tracker.domain.model.Priority {
    public static com.voicehabit.tracker.domain.model.Priority *;
}

# Kotlin metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Widgets и Activities создаются системой по имени класса
-keep class com.voicehabit.tracker.widget.** { *; }
-keep class com.voicehabit.tracker.MainActivity { *; }
-keep class com.voicehabit.tracker.VoiceHabitApp { *; }

# Встроенные ключи ИИ читаются из BuildConfig через рефлексию (BundledKeys) —
# без keep R8 вырезает поля как неиспользуемые и релиз остаётся без ключей.
-keep class com.voicehabit.tracker.BuildConfig { *; }

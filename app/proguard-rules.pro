# ===== 通用 =====
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ===== Kotlin 元数据 / 协程 =====
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# ===== 枚举：valueOf(String) 经反射调用（DataStore 存 name 还原），保留 values()/valueOf() =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Room（实体/DAO/RoomDatabase 入口保留；列级规则由 room-compiler 生成，无需手写）=====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class androidx.room.RoomDatabase$* { *; }
-dontwarn androidx.room.paging.**

# ===== Jetpack Compose =====
# 不再使用 -keep class androidx.compose.runtime.** { *; } 这类过宽规则。
# Compose 运行时自带的 consumer ProGuard 规则已覆盖所需保留项，
# 此处仅对确有需要的范围做最小化兜底。
-dontwarn androidx.compose.material.**

# ===== 保留 Parcelable CREATOR（本项目未用，兜底）=====
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

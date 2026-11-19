# Shizuku UserService is instantiated by class name in the shell process
-keep class io.github.j0p3s.aainstaller.ShellService { *; }
-keep class io.github.j0p3s.aainstaller.IShellService { *; }
-keep class io.github.j0p3s.aainstaller.IShellService$* { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# ARSCLib / apksig use reflection-light code but keep them intact to be safe
-keep class com.reandroid.** { *; }
-keep class com.android.apksig.** { *; }
-dontwarn com.reandroid.**
-dontwarn com.android.apksig.**
-dontwarn javax.annotation.**
-dontwarn org.xmlpull.**

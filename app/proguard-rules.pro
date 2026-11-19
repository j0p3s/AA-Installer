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

# v1.3.1: painterResource() crashed natively (SIGSEGV, resource id used as a pointer in
# XmlBlock.getAttributeListValue) in the R8-optimised release on nubia/RedMagic Android 16.
# Shrink, but don't optimise, so the Compose code runs as compiled.
-dontoptimize

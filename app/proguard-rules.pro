# The Rust engine looks these up by name through JNI, so R8 must keep them.
-keep class com.wordcraft.android.NativeBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }

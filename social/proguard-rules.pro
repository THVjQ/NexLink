# §10.6.3 — R8 is on from the first release, so keep-rules grow with the code.
#
# :social-contract ships its own consumer rules for the Parcelables that cross
# the process boundary (§16.4.1), so nothing is needed for them here.

# Kotlin metadata, needed for reflection-based serialisation later.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# §27.5 — the app must not carry logging that could emit identifiers into a
# release build. R8 strips these bodies entirely, which is defence in depth on
# top of redacting at the call site.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ── The SDK's FFI layer must not be renamed (found 2026-09-12) ───────────────
#
# Without these the release build dies before it draws a frame:
#
#   FATAL EXCEPTION: main
#   java.lang.UnsatisfiedLinkError: Can't obtain peer field ID for class com.sun.jna.Pointer
#       at com.sun.jna.Native.initIDs(Native Method)
#       at org.matrix.rustcomponents.sdk.UniffiRustCallStatus.<init>
#       at com.nexlink.social.SocialApplication.onCreate
#
# JNA's native half looks its Java fields up **by name** through JNI. R8 has no
# way to see that — the reference lives in C, not in bytecode — so it renames
# `Pointer.peer` to something short and `initIDs` fails at class-load time.
# uniffi sits directly on JNA, so every Rust call goes through this.
#
# Debug builds are unminified, which is exactly why this survived until the
# first time anyone launched a release build: every check up to here had been
# run on `assembleDebug`. §10.6.3 turned R8 on from the first release to find
# this early, and it did — but only once the APK was actually run.
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * implements com.sun.jna.Callback { *; }

# uniffi generates callback interfaces and vtable structs that Rust fills in by
# layout and calls back into. Renaming or stripping their members breaks the
# call in the same invisible way.
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }

# The vendored platform verifier (§11.7.4) is invoked from Rust, so nothing in
# Kotlin references it and R8 would otherwise remove it entirely.
-keep class org.rustls.platformverifier.** { *; }

# JNA is a desktop library first, so it carries an AWT bridge that references
# java.awt.* — classes that do not exist on Android. Keeping all of JNA (above)
# makes R8 see those references and fail the build on them. They are never
# reached here: Native$AWT is only touched by desktop code paths.
-dontwarn java.awt.**

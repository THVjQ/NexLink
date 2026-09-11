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

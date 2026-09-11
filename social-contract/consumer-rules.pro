# Parcelable types cross a process boundary by class name. R8 in :app (or any
# other consumer) must not rename or strip them, or the unmarshalling in the
# other process fails at runtime with a ClassNotFoundException that names a
# class nobody can find in the source.
-keep class com.nexlink.social.contract.** { *; }

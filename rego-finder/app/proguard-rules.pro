# R8 runs in full mode (the AGP default). The app itself needs no extra keep rules:
#  - kotlinx-serialization: every serializer is resolved at compile time (Json.decodeFromString<T>()),
#    and the library ships its own R8 rules for @Serializable classes.
#  - OkHttp, Okio, AndroidX (Compose, DataStore, Browser, ProfileInstaller) ship consumer rules.
#  - No reflection, JNI, or classes named in XML other than MainActivity, which AAPT keeps.

# Keep file and line numbers so crash stack traces can be retraced with build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

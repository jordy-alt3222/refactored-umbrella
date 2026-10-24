#!/usr/bin/env bash
# Rebuilds ServiceFinder-debug-2.apk as a latency-optimised release-style APK.
#
# There is no source for debug-2, so this works on the APK itself:
#   1. dex -> jar twice: enjarify for method bodies, dex2jar for annotations/signatures, merged by
#      MergeJars.java (dex2jar alone mistranslates some branches; enjarify alone drops annotations)
#   2. R8 shrink + optimise with rules.pro (restates the library keep rules the debug build never applied,
#      strips Compose sourceInformation/trace calls and Kotlin parameter null checks)
#   3. manifest: drop android:debuggable and the debug-only PreviewActivity
#   4. compile baseline-prof.txt into assets/dexopt so ART AOT-compiles startup code
#   5. zipalign + sign
#
# Usage: tools/optimize_debug2/build.sh [input.apk] [output.apk]
# Needs: JDK 17+, ANDROID_HOME with build-tools;37.0.0, platforms;android-37.0 and cmdline-tools/latest.
# Signing: set KEYSTORE, KEYSTORE_PASS and KEY_ALIAS, otherwise a local key is generated in the work dir.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
IN="${1:-$ROOT/ServiceFinder-debug-2.apk}"
OUT="${2:-$ROOT/ServiceFinder-debug-2-optimized.apk}"
WORK="${WORK:-$ROOT/tools/work/optimize_debug2}"
SDK="${ANDROID_HOME:?set ANDROID_HOME}"
BT="$SDK/build-tools/37.0.0"
ANDROID_JAR="$SDK/platforms/android-37.0/android.jar"
PROFGEN="$SDK/cmdline-tools/latest/bin/profgen"

APKTOOL_VER=2.12.1
DEX2JAR_VER=2.4.38
ASM_VER=9.10.1
R8_VER=9.5.28
ENJARIFY_REV=f2db0563aa83885ce6e6acd5b7dc9a8a1a1e1987

mkdir -p "$WORK/tools"
cd "$WORK"

fetch() { [ -s "$2" ] || curl -fsSL --retry 5 --retry-all-errors --retry-delay 5 -o "$2" "$1"; }
fetch "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar" tools/apktool.jar
fetch "https://dl.google.com/android/maven2/com/android/tools/r8/$R8_VER/r8-$R8_VER.jar" tools/r8.jar
mkdir -p tools/lib
MAVEN=https://repo1.maven.org/maven2
for a in dex-tools d2j-base-cmd dex-translator dex-reader dex-reader-api dex-ir d2j-external; do
  fetch "$MAVEN/de/femtopedia/dex2jar/$a/$DEX2JAR_VER/$a-$DEX2JAR_VER.jar" "tools/lib/$a-$DEX2JAR_VER.jar"
done
for a in asm asm-tree asm-commons asm-analysis asm-util; do
  fetch "$MAVEN/org/ow2/asm/$a/$ASM_VER/$a-$ASM_VER.jar" "tools/lib/$a-$ASM_VER.jar"
done
if [ ! -d tools/enjarify ]; then
  git clone -q https://github.com/google/enjarify tools/enjarify
  git -C tools/enjarify checkout -q "$ENJARIFY_REV"
fi
dex2jar() { java -Xmx8g -cp "tools/lib/*" com.googlecode.dex2jar.tools.Dex2jarCmd -dsn -f "$@" >/dev/null; }

echo "== decode resources and manifest"
rm -rf decoded && java -jar tools/apktool.jar d -q -s -o decoded "$IN"

echo "== dex -> jar"
# Both translations are slow and depend only on the input APK, so they are reused while it is unchanged.
[ app-code.jar -nt "$IN" ] || (cd tools/enjarify && python3 -O -m enjarify.main "$IN" -o "$WORK/app-code.jar" -f | tail -1)
[ app-meta.jar -nt "$IN" ] || dex2jar -o app-meta.jar "$IN"
mkdir -p mergejars && javac -d mergejars -cp "tools/lib/*" "$HERE/MergeJars.java"
java -Xmx8g -cp "tools/lib/*:mergejars" MergeJars app-code.jar app-meta.jar app-merged.jar "$ANDROID_JAR"

echo "== R8"
rm -rf r8out && mkdir r8out
java -Xmx10g -cp tools/r8.jar com.android.tools.r8.R8 --release --min-api 26 \
  --lib "$ANDROID_JAR" --pg-conf "$HERE/rules.pro" --pg-map-output r8out/mapping.txt \
  --output r8out app-merged.jar > r8.log 2>&1
grep -E "^(Warning|Error)" r8.log | grep -v "java.lang.Record" || true
rm -f decoded/classes*.dex && cp r8out/classes*.dex decoded/

echo "== manifest"
sed -i -e 's/ android:debuggable="true"//' \
       -e '/androidx.compose.ui.tooling.PreviewActivity/d' decoded/AndroidManifest.xml
sed -i -e 's/versionCode: 1$/versionCode: 2/' -e 's/versionName: 1.0$/versionName: 1.0.1-latency/' decoded/apktool.yml
# Coroutines debug-agent data; only read when kotlinx-coroutines-debug is attached.
rm -f decoded/unknown/DebugProbesKt.bin

echo "== package"
rm -f unsigned.apk && java -jar tools/apktool.jar b -q -o unsigned.apk decoded

echo "== baseline profile"
# profgen only expands wildcards against class files, so list the optimised dex's methods as a jar
# (only class and method names matter here, not the code).
rm -f r8out.zip && (cd r8out && zip -q ../r8out.zip classes*.dex)
dex2jar -o r8out.jar r8out.zip
"$PROFGEN" expandWildcards --profile "$HERE/baseline-prof.txt" --output "$WORK/baseline-expanded.txt" "$WORK/r8out.jar" >/dev/null
"$PROFGEN" bin "$WORK/baseline-expanded.txt" --apk "$WORK/unsigned.apk" \
  --output "$WORK/baseline.prof" --output-meta "$WORK/baseline.profm" >/dev/null
mkdir -p decoded/assets/dexopt && cp baseline.prof baseline.profm decoded/assets/dexopt/
rm -f unsigned.apk && java -jar tools/apktool.jar b -q -o unsigned.apk decoded

echo "== align and sign"
"$BT/zipalign" -f -P 16 4 unsigned.apk aligned.apk
if [ -z "${KEYSTORE:-}" ]; then
  KEYSTORE="$WORK/local-signing.jks"; KEYSTORE_PASS="${KEYSTORE_PASS:-servicefinder}"; KEY_ALIAS="${KEY_ALIAS:-servicefinder}"
  [ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Service Finder" >/dev/null 2>&1
fi
"$BT/apksigner" sign --v4-signing-enabled false --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" --out "$OUT" aligned.apk
"$BT/apksigner" verify "$OUT"
ls -la "$IN" "$OUT"

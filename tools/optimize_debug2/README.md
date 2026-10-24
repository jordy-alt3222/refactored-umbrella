# Latency-optimised build of `ServiceFinder-debug-2.apk`

`ServiceFinder-debug-2-optimized.apk` in the repository root is the original debug-2 app
(Lookup, Garage, Manuals, Guides, Settings, the Ryco/Penrite/Valvoline lookups and the
Claude reset lookup) rebuilt for speed. The app's behaviour is unchanged. There is no
source for debug-2, so everything here works on the APK itself; `build.sh` reproduces it.

## Why debug-2 was slow

| Problem in debug-2 | Effect | Fix |
|---|---|---|
| `android:debuggable="true"` | ART runs debuggable apps in a slow interpreter/JIT mode and doesn't use the normal AOT compiled code | Removed from the manifest |
| No R8 at all: 126 MB of dex in 12 files | Slow install and dexopt, more I/O and class lookups at startup | R8 shrink + optimise with `rules.pro`; the dex is now 33 MB in 5 files |
| Compose debug instrumentation compiled into every composable (`sourceInformation`, `traceEventStart/End`) | Extra work on every recomposition | Stripped with `-assumenosideeffects`, as Compose's own release rules do |
| Kotlin parameter null checks on every call | Small per-call overhead | Stripped |
| No baseline profile | Startup code runs interpreted until the JIT warms up | `baseline-prof.txt` compiled into `assets/dexopt/`; `androidx.profileinstaller` (already in the app) installs it, and ART then AOT-compiles the app and Compose UI code |
| Debug-only `PreviewActivity`, exported | Not needed in a release build | Removed |

The Anthropic SDK (plus the Jackson and kotlin-reflect code it uses) is kept whole, not
shrunk. It is reflection-heavy, and its network path couldn't be exercised in testing (see
below). It only loads when a Claude lookup runs, so this costs APK size but not startup time.

## Results

APK: 38.4 MB → 14.3 MB. Dex: 126 MB → 33 MB.

Cold start to first frame (`ActivityTaskManager: Displayed`, 5 launches each), measured on
an API 30 x86_64 emulator running **without hardware acceleration**. Absolute times are
many times slower than a phone, so compare the ratios, not the seconds:

| State | debug-2 (median) | optimised (median) | Change |
|---|---|---|---|
| Straight after install | 29.8 s | 15.3 s | **−49 %** |
| Compiled: debug-2 with `compile -m speed` (its best case); optimised with `compile -m speed-profile`, which is what the baseline profile gets on a phone | 12.0 s | 8.2 s | **−32 %** |

Raw runs (s). debug-2: 31.8, 28.9, 29.8, 28.8, 32.7, then compiled: 11.5, 12.6, 12.2, 12.0, 10.9.
Optimised: 15.3, 16.1, 14.0, 16.0, 14.7, then compiled: 7.9, 8.2, 9.9, 8.3, 8.0.

## How it was checked

On the emulator, the optimised build was run through every tab with no crashes:

- Lookup: Make & Model, plus the Retry after a network error.
- Rego search: the Penrite path, then the Ryco WebView/reCAPTCHA fallback through the
  `@JavascriptInterface` bridge.
- Garage.
- Manuals: list → Isuzu → D-MAX 2025 → offline full-text search for "fuse" (37 matches).
- Guides: Reset lights and Tools.
- Settings: entering and saving an API key.

**Not tested end-to-end:** live responses from Ryco, Penrite, Valvoline and the Claude lookup.
The test sandbox intercepts the emulator's HTTPS traffic, so those calls fail with a
certificate error in both builds. The app shows that error the same way in both builds.
Check one real lookup of each kind on a phone before relying on this build.

## Installing

The APK is signed with a new key, not the key debug-2 was signed with, so Android won't
install it over debug-2. **Uninstall debug-2 first.** Doing so deletes its saved Garage
vehicles and settings.

## Rebuilding

```sh
ANDROID_HOME=/path/to/sdk tools/optimize_debug2/build.sh            # writes ServiceFinder-debug-2-optimized.apk
KEYSTORE=... KEYSTORE_PASS=... KEY_ALIAS=... tools/optimize_debug2/build.sh   # sign with your own key
```

It needs JDK 17+, Python 3, and an SDK with `build-tools;37.0.0`, `platforms;android-37.0` and
`cmdline-tools;latest`. It downloads apktool, R8, dex2jar and enjarify into `tools/work/`
(ignored by git). Without `KEYSTORE`, it generates a signing key in `tools/work/`. Keep that key
if you'll want to install later rebuilds as updates.

How the pipeline works:

1. The dex is turned back into class files twice. enjarify provides the method bodies, and
   dex2jar provides the annotations, generic signatures and inner-class data that enjarify
   drops. `MergeJars.java` combines them. dex2jar on its own mistranslates some branches:
   `if (0 > x)` becomes `if (x > 0)`, which crashed Compose.
2. `MergeJars.java` also repairs two things the dex format loses:
   - It restores the interface flag on static/private interface method calls.
   - It drops the empty framework-class stubs D8 adds for APIs newer than `minSdk`. These hid
     the real `WindowInsetsAnimation.Callback` from R8, which then stripped `onProgress` and
     crashed the app when the keyboard opened.
3. R8 runs with `rules.pro`. The manifest is edited, the baseline profile is compiled
   against the final dex, and the APK is zip-aligned (16 KB pages) and signed.

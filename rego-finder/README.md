# Rego Finder

An Android 17 app for looking up a vehicle by its Australian registration plate (rego) and
going straight to the right oils and filters from **Penrite**, **Valvoline** and **Ryco**.

- **Rego search.** Enter a plate, pick the state, and get make, model, year range, body and
  engine. When PlateAPI has them, it also returns the engine code, drivetrain and power. If the
  plate matches several variants, you pick the one that fits.
- **Oils and filters.** One tap opens the official Penrite rego search, the Valvoline Lube Guide
  or the Ryco filter finder. The plate is copied, ready to paste. A Ryco part number such as
  `Z411` opens that part's page directly.
- **Recent searches.** Saved on the phone. Opening one is instant and needs no connection or
  lookup credit.

Package `com.regofinder.app`, version 1.0.0. `compileSdk` 37, `targetSdk` 37, `minSdk` 26.

## Where the data comes from

| Data | Source | How the app uses it |
|---|---|---|
| Plate → vehicle | [PlateAPI](https://plateapi.com.au/docs/lookup/), a commercial Australian rego lookup | `GET https://api.plateapi.com.au/api/v1/lookup` with your key in `X-API-Key` |
| Oil and fluid recommendations | [Penrite rego search](https://penriteoil.com.au/rego-search) | Opened in a Custom Tab |
| Oil and fluid recommendations | [Valvoline Lube Guide (Australia)](https://www.valvolineglobal.com/en-au/lube-guide/) | Opened in a Custom Tab |
| Filters | [Ryco filter finder](https://rycofilters.com.au/search/-/-) and `rycofilters.com.au/part/<number>` | Opened in a Custom Tab |

The brands' recommendations are not copied into the app. Ryco's rego search runs on licensed
Autoinfo data and both Ryco's and Penrite's lookups are protected by reCAPTCHA, so calling them
from another app would mean working around those protections and their terms. Opening their
own pages keeps the recommendations current and correct. If you get a licensed data feed from
one of them later, it fits in next to `PlateApi` in `data/`.

**PlateAPI key.** Sign up at plateapi.com.au. The free plan allows 20 lookups a month at 1 per
minute; paid plans start at A$29 a month. Enter the key under the key icon in the app. To bake a
default key into a build you keep to yourself, add `-PplateApiKey=…` (or set `PLATEAPI_KEY`).
Anyone holding that APK can extract the key.

## Latency

- **Instant repeat searches.** Lookups are cached on the phone per plate and state. A repeat
  search shows immediately, uses no lookup credit, and shows when the result was fetched, with
  a refresh button.
- **Pre-connected API.** On launch the app opens the DNS, TLS and HTTP/2 connection to PlateAPI
  while you type, so pressing Search costs one round trip. One shared OkHttp client keeps
  connections pooled, and starting a new search cancels the previous request.
- **Pre-loaded finders.** The app binds to the browser's Custom Tabs service and warms it up at
  launch. Once a vehicle is shown, it asks the browser to pre-load the Penrite page and
  pre-connect to Valvoline and Ryco. A Custom Tab also shares the browser's cookies and cache.
- **Fast first launch.** A baseline profile (`app/src/main/baseline-prof.txt`) is compiled into
  the APK. `androidx.profileinstaller` applies it on sideloaded installs, where Play's cloud
  profiles never arrive. Startup work is deferred: the network client and history file are
  created off the main thread on first use. The window background matches the first frame.
- **Small release build.** R8 full mode, resource shrinking, no reflection, and few dependencies
  (no DI framework, database or image loader). The release APK is about 1.7 MB.

## Android 17 notes

Android 17 is API level 37, and its SDK (`platforms;android-37.0`) is final, not a preview. The
behavior changes that matter for apps targeting 37, and how this app handles them:

- **Local network access** now needs `ACCESS_LOCAL_NETWORK`. The app only talks to public HTTPS
  hosts, so it needs just `INTERNET`.
- **Certificate Transparency** is on by default. The app talks only to hosts with publicly trusted
  certificates and has no custom trust configuration.
- **Orientation and resizability locks** are ignored on large screens. The app sets none, and
  its single scrolling column works at any size.
- **Lock-free `MessageQueue`** and **unmodifiable `static final` fields**: the app uses no
  reflection, so neither applies.
- **Backups.** `allowBackup="false"` plus `dataExtractionRules` keep the API key and history
  out of cloud backups and device transfers.

## Project structure

```
rego-finder/
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── gradle/libs.versions.toml           versions (AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00)
├── keystore.properties.example         template for the git-ignored keystore.properties
├── RegoFinder-1.0.0-release.apk        the signed release build
└── app/
    ├── build.gradle.kts                release build type, signing, R8
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── baseline-prof.txt
        │   ├── java/com/regofinder/app/
        │   │   ├── RegoFinderApp.kt    creates the OkHttp client, repository and settings lazily
        │   │   ├── MainActivity.kt     edge-to-edge Compose host; binds the Custom Tabs service
        │   │   ├── data/
        │   │   │   ├── Plate.kt        states, plate normalisation and validation
        │   │   │   ├── PlateApi.kt     PlateAPI client, response models, typed errors, preconnect
        │   │   │   ├── RegoRepository.kt   cache and history in front of PlateAPI
        │   │   │   └── SettingsStore.kt    DataStore: API key and last state
        │   │   └── ui/
        │   │       ├── Brands.kt       Penrite/Valvoline/Ryco links and the Custom Tabs launcher
        │   │       ├── RegoViewModel.kt
        │   │       ├── RegoScreen.kt   the one screen, plus the API key dialog
        │   │       └── Theme.kt        Material 3, dynamic colour on Android 12+
        │   └── res/                    icons, theme, data extraction rules
        └── test/java/com/regofinder/app/data/
            ├── PlateApiTest.kt         request shape and response/error mapping (MockWebServer)
            └── RegoRepositoryTest.kt   caching, history persistence, validation
```

One screen and one ViewModel, with no navigation library and no DI framework. That is all an
app of this size needs.

## Building the signed release APK

Requirements: JDK 17 or newer (Android Studio's bundled JDK works), Android SDK Platform 37 and
Build-Tools 37.0.0 (`sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`), and an
Android Studio release that supports AGP 9.4. Open the `rego-finder` folder as the project.

### 1. Create a keystore (once)

```sh
keytool -genkeypair -v \
  -keystore ~/keys/regofinder-release.jks -storetype PKCS12 \
  -alias regofinder -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Rego Finder, C=AU"
```

Keep the keystore and its password safe and outside the repository. Android only installs
an update over an existing install when both are signed with the same key.

### 2. Point the build at it

Either copy `keystore.properties.example` to `keystore.properties` in this folder (it is
git-ignored) and fill it in, or set environment variables:

```sh
export REGOFINDER_STORE_FILE=~/keys/regofinder-release.jks
export REGOFINDER_STORE_PASSWORD=…
export REGOFINDER_KEY_ALIAS=regofinder
export REGOFINDER_KEY_PASSWORD=…
```

`REGOFINDER_SIGNING=/path/to/keystore.properties` also works. If signing isn't configured,
`assembleRelease` stops with an error instead of producing an unsigned APK.

### 3. Build

```sh
./gradlew :app:assembleRelease
```

Or in Android Studio: **Build > Generate Signed App Bundle / APK**, choose **APK**, select your
keystore and the `release` build variant.

### 4. Find the APK

`app/build/outputs/apk/release/app-release.apk`. If you used Android Studio's wizard, it goes
to `app/release/app-release.apk`, or the destination folder you picked. To check the signature:

```sh
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### 5. Install on an Android 17 device or emulator

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

For a phone, enable **Developer options > USB debugging**, or copy the APK to the phone and
open it. Allow "Install unknown apps" for the app you open it from. For an emulator, create an
**API 37** virtual device in Device Manager and either drag the APK onto it or use `adb install`.
If an install signed with a different key is already there, uninstall it first
(`adb uninstall com.regofinder.app`).

## Tests

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease
```

There are 9 JVM tests. They cover the lookup request (path, query, `X-API-Key`), parsing of
PlateAPI's documented response, every error status (401, 429 with `Retry-After`, 503, 400 and
`not_found`), cache hits making no request, plates being kept per state, history surviving a
restart, and plate validation.

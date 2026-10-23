import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from REGOFINDER_* environment variables, or else from keystore.properties
// (the file named by REGOFINDER_SIGNING, or keystore.properties in the project root). Neither is
// in version control. See keystore.properties.example.
val signingProps: Properties? = run {
    val env = System.getenv()
    if (env["REGOFINDER_STORE_FILE"] != null) {
        Properties().apply {
            setProperty("storeFile", env["REGOFINDER_STORE_FILE"])
            setProperty("storePassword", env["REGOFINDER_STORE_PASSWORD"].orEmpty())
            setProperty("keyAlias", env["REGOFINDER_KEY_ALIAS"].orEmpty())
            setProperty("keyPassword", env["REGOFINDER_KEY_PASSWORD"].orEmpty())
        }
    } else {
        (env["REGOFINDER_SIGNING"]?.let(::file) ?: rootProject.file("keystore.properties"))
            .takeIf { it.isFile }
            ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    }
}

// Optional default PlateAPI key baked into the APK (-PplateApiKey=… or PLATEAPI_KEY). Anyone with
// the APK can extract it, so only do this for builds you keep to yourself. Keys entered in the app
// take precedence.
val defaultPlateApiKey: String = providers.gradleProperty("plateApiKey")
    .orElse(providers.environmentVariable("PLATEAPI_KEY"))
    .getOrElse("")

android {
    namespace = "com.regofinder.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.regofinder.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "DEFAULT_PLATEAPI_KEY", "\"${defaultPlateApiKey.replace("\"", "")}\"")
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The dependency metadata block is only read by Google Play; this APK is sideloaded.
    dependenciesInfo {
        includeInApk = false
    }
}

// The release APK is the deliverable, so fail early rather than silently producing an unsigned one.
val checkReleaseSigning by tasks.registering {
    // A plain local, so the configuration cache doesn't have to serialise the build script.
    val hasReleaseSigning = signingProps != null
    doLast {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing is not configured. Create keystore.properties (see " +
                    "keystore.properties.example) or set REGOFINDER_STORE_FILE, " +
                    "REGOFINDER_STORE_PASSWORD, REGOFINDER_KEY_ALIAS and REGOFINDER_KEY_PASSWORD.",
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    // Applies the bundled baseline profile on sideloaded installs, where Play's cloud profiles never arrive.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

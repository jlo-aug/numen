plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

// Signing config is read from release/keystore.properties (not tracked) or from env vars
val keystorePropertiesFileCandidates = listOf(
    rootProject.file("release/keystore.properties"),
    rootProject.file("keystore.properties")
)
val keystorePropertiesFile = keystorePropertiesFileCandidates.firstOrNull { it.exists() }
    ?: keystorePropertiesFileCandidates.last()
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingProp(key: String, env: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

fun resolveSigningStoreFile(storePath: String): File =
    if (File(storePath).isAbsolute) {
        File(storePath)
    } else {
        keystorePropertiesFile.parentFile.resolve(storePath)
    }

fun hasSigningConfig(storePath: String?, storePass: String?, alias: String?, keyPass: String?): Boolean =
    storePath != null && storePass != null && alias != null && keyPass != null

fun gradleBooleanProperty(name: String): Boolean =
    providers.gradleProperty(name).orNull?.equals("true", ignoreCase = true) == true

fun shouldValidateNightlySigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleNightlyRelease",
        "bundleNightlyRelease",
        "packageNightlyRelease",
        "publishNightlyRelease",
        "installNightlyRelease"
    )
    return taskNames.any { task ->
        signingTaskHints.any { hint -> task.contains(hint, ignoreCase = true) }
    }
}

android {
    namespace = "it.palsoftware.pastiera"
    compileSdk = 36

    val defaultVersionCode = 86
    val defaultVersionName = "0.86"
    val ciVersionCode = providers.gradleProperty("PASTIERA_VERSION_CODE").orNull?.toIntOrNull()
    val ciVersionName = providers.gradleProperty("PASTIERA_VERSION_NAME").orNull
    val nightlyVersionCode = providers.gradleProperty("PASTIERA_NIGHTLY_VERSION_CODE").orNull?.toIntOrNull()
    val isUnsignedReleaseBuild = gradleBooleanProperty("PASTIERA_UNSIGNED_RELEASE_BUILD")

    defaultConfig {
        applicationId = "it.palsoftware.pastiera"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: defaultVersionCode
        versionName = ciVersionName ?: defaultVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("nightly") {
            val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
            val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
            val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
            val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")

            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                val resolvedStoreFile = resolveSigningStoreFile(storePath!!)
                storeFile = resolvedStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    flavorDimensions += "channel"

    productFlavors {
        create("nightly") {
            dimension = "channel"
            // Numen: a distinct app (Pastiera's forking policy requires its own identity), with
            // its own version line.
            applicationId = "app.numen.keyboard"
            versionCode = nightlyVersionCode ?: 121
            versionName = "1.2.1"
            manifestPlaceholders["appLabel"] = "Numen"
            manifestPlaceholders["imeLabel"] = "Numen"
            // Numen's own deep links and actions.
            buildConfigField("String", "SETTINGS_LINK_SCHEME", "\"numen\"")
            buildConfigField("String", "TOGGLE_KEYBOARD_MODE_ACTION", "\"app.numen.keyboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE\"")
            manifestPlaceholders["settingsLinkScheme"] = "numen"
            manifestPlaceholders["toggleKeyboardModeAction"] = "app.numen.keyboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE"
            val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
            val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
            val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
            val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")
            if (!isUnsignedReleaseBuild && hasSigningConfig(storePath, storePass, alias, keyPass)) {
                signingConfig = signingConfigs.getByName("nightly")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable lint for release to avoid file lock issues
            isDebuggable = false
        }
    }
    
    // Validate signing config only when building release
    tasks.whenTaskAdded {
        if (name.equals("preNightlyReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (isUnsignedReleaseBuild) {
                    logger.lifecycle("Building an unsigned nightly release for separate PIV signing.")
                    return@doFirst
                }
                if (!shouldValidateNightlySigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping nightly signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
                val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
                val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
                val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for nightly build. Define nightlyStoreFile, nightlyStorePassword, nightlyKeyAlias and nightlyKeyPassword in " +
                            "release/keystore.properties (not tracked) or in the environment variables PASTIERA_NIGHTLY_KEYSTORE_PATH, " +
                            "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD, PASTIERA_NIGHTLY_KEY_ALIAS, PASTIERA_NIGHTLY_KEY_PASSWORD."
                    )
                }
            }
        }
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // Exclude legacy JSON base dictionaries; keep serialized .dict and user_defaults.json
            excludes += "assets/common/dictionaries/*_base.json"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.material:material-icons-extended")
    // RecyclerView per performance ottimali nella griglia emoji
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Emoji2 per supporto emoji future-proof
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    // Kotlinx Serialization for dictionary optimization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.3")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.mockito:mockito-core:5.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

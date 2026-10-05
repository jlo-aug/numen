plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

import java.io.File
import java.util.Properties
import groovy.json.JsonOutput
import org.gradle.api.GradleException

// Config di firma letta da release/keystore.properties (non tracciato) o da env vars
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

fun shouldValidateStableSigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleStableRelease",
        "bundleStableRelease",
        "packageStableRelease",
        "publishStableRelease",
        "installStableRelease"
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
    val isFdroidBuild = gradleBooleanProperty("PASTIERA_FDROID_BUILD")
    val isUnsignedReleaseBuild = gradleBooleanProperty("PASTIERA_UNSIGNED_RELEASE_BUILD")
    val successorGithubRepository = providers.gradleProperty("PASTIERA_SUCCESSOR_GITHUB_REPOSITORY")
        .orNull ?: "pkb-rocks/plektra"
    if (!successorGithubRepository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
        throw GradleException(
            "PASTIERA_SUCCESSOR_GITHUB_REPOSITORY must have the form owner/repository"
        )
    }

    defaultConfig {
        applicationId = "it.palsoftware.pastiera"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: defaultVersionCode
        versionName = ciVersionName ?: defaultVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUCCESSOR_GITHUB_REPOSITORY", "\"$successorGithubRepository\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")

            // Only configure signing if all credentials are provided
            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                val resolvedStoreFile = resolveSigningStoreFile(storePath!!)
                storeFile = resolvedStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
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
        create("stable") {
            dimension = "channel"
            manifestPlaceholders["appLabel"] = "Pastiera"
            manifestPlaceholders["imeLabel"] = "Pastiera"
            buildConfigField("boolean", "UPSTREAM_BRANDING", "true")
            buildConfigField("String", "SETTINGS_LINK_SCHEME", "\"pastiera\"")
            buildConfigField("String", "SETTINGS_WEB_LINK_HOST", "\"pastiera.eu\"")
            buildConfigField("String", "TOGGLE_KEYBOARD_MODE_ACTION", "\"it.palsoftware.pastiera.action.TOGGLE_SOFTWARE_KEYBOARD_MODE\"")
            manifestPlaceholders["settingsLinkScheme"] = "pastiera"
            manifestPlaceholders["toggleKeyboardModeAction"] = "it.palsoftware.pastiera.action.TOGGLE_SOFTWARE_KEYBOARD_MODE"
            buildConfigField("String", "RELEASE_CHANNEL", "\"stable\"")
            buildConfigField("boolean", "IS_FDROID_BUILD", if (isFdroidBuild) "true" else "false")
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATE_CHECKS", if (isFdroidBuild) "false" else "true")
        }
        create("nightly") {
            dimension = "channel"
            // Numen: a distinct app (Pastiera's forking policy requires its own identity), with
            // its own version line and no update checks against Pastiera's releases.
            applicationId = "app.numen.keyboard"
            versionCode = nightlyVersionCode ?: 100
            versionName = "1.0.0"
            manifestPlaceholders["appLabel"] = "Numen"
            manifestPlaceholders["imeLabel"] = "Numen"
            buildConfigField("boolean", "UPSTREAM_BRANDING", "false")
            // Numen's own deep links and actions; no claim on Pastiera's web domains.
            buildConfigField("String", "SETTINGS_LINK_SCHEME", "\"numen\"")
            buildConfigField("String", "SETTINGS_WEB_LINK_HOST", "\"\"")
            buildConfigField("String", "TOGGLE_KEYBOARD_MODE_ACTION", "\"app.numen.keyboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE\"")
            manifestPlaceholders["settingsLinkScheme"] = "numen"
            manifestPlaceholders["toggleKeyboardModeAction"] = "app.numen.keyboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE"
            buildConfigField("String", "RELEASE_CHANNEL", "\"nightly\"")
            buildConfigField("boolean", "IS_FDROID_BUILD", if (isFdroidBuild) "true" else "false")
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATE_CHECKS", "false")
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
            // Only use signing config if it's properly configured
            val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")
            
            if (!isFdroidBuild && !isUnsignedReleaseBuild && hasSigningConfig(storePath, storePass, alias, keyPass)) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Disable lint for release to avoid file lock issues
            isDebuggable = false
        }
    }
    
    // Validate signing config only when building release
    tasks.whenTaskAdded {
        if (!isFdroidBuild && name.equals("preStableReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (isUnsignedReleaseBuild) {
                    logger.lifecycle("Building an unsigned stable release for separate PIV signing.")
                    return@doFirst
                }
                if (!shouldValidateStableSigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping stable signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
                val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
                val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
                val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for release build. Define storeFile, storePassword, keyAlias e keyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PASTIERA_KEYSTORE_PATH, " +
                            "PASTIERA_KEYSTORE_PASSWORD, PASTIERA_KEY_ALIAS, PASTIERA_KEY_PASSWORD. " +
                            "Use -PPASTIERA_FDROID_BUILD=true only for the unsigned stable F-Droid release path."
                    )
                }
            }
        }
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
                        "Missing signing config for nightly build. Define nightlyStoreFile, nightlyStorePassword, nightlyKeyAlias e nightlyKeyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PASTIERA_NIGHTLY_KEYSTORE_PATH, " +
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

// The last stable APK carries a machine-readable inventory of its runtime
// dependencies and the third-party assets documented in third_party_notices.md.
val stableSbomAssets = layout.buildDirectory.dir("generated/sbom/stableRelease/assets")
android.sourceSets.getByName("stable").assets.srcDir(stableSbomAssets)
val stableRuntimeClasspath = provider { configurations.getByName("stableReleaseRuntimeClasspath") }
val generateStableReleaseSbom = tasks.register("generateStableReleaseSbom") {
    inputs.files(stableRuntimeClasspath)
    inputs.property("versionName", android.defaultConfig.versionName ?: "")
    inputs.property("versionCode", android.defaultConfig.versionCode ?: 0)
    outputs.dir(stableSbomAssets)
    doLast {
        val versionName = android.defaultConfig.versionName ?: "unknown"
        val appRef = "pkg:generic/pastiera@$versionName"
        val libraries = stableRuntimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .map { artifact ->
                val id = artifact.moduleVersion.id
                linkedMapOf<String, Any>(
                    "type" to "library",
                    "group" to id.group,
                    "name" to id.name,
                    "version" to id.version,
                    "bom-ref" to "pkg:maven/${id.group}/${id.name}@${id.version}",
                    "purl" to "pkg:maven/${id.group}/${id.name}@${id.version}"
                )
            }
            .distinctBy { it["bom-ref"] }
            .sortedBy { it["bom-ref"].toString() }
        fun bundled(name: String, ref: String, source: String, license: String) =
            linkedMapOf<String, Any>(
                "type" to "data",
                "name" to name,
                "bom-ref" to ref,
                "externalReferences" to listOf(mapOf("type" to "website", "url" to source)),
                "licenses" to listOf(mapOf("license" to mapOf("id" to license)))
            )
        val bundledAssets = listOf(
            bundled("AOSP LatinIME-derived visuals", "vendored:aosp-latinime:127336e9f29d69607eab55982324b210279ae8c5", "https://android.googlesource.com/platform/packages/inputmethods/LatinIME", "Apache-2.0"),
            bundled("Google Material Symbols / Material Icons artwork", "vendored:material-icons", "https://github.com/google/material-design-icons", "Apache-2.0"),
            bundled("OpenGameArt keyboard soundpack", "vendored:opengameart-keyboard-soundpack", "https://opengameart.org/content/keyboard-soundpack-1-typing-and-single-keystrokes", "CC0-1.0"),
            bundled("OpenGameArt typewriter sounds", "vendored:opengameart-typewriter-sounds", "https://opengameart.org/content/typewriter-sounds", "CC0-1.0"),
            bundled("OpenGameArt mechanical sounds", "vendored:opengameart-mechanical-sounds", "https://opengameart.org/content/mechanical-sounds", "CC0-1.0"),
            bundled("Unicode CLDR emoji annotations", "vendored:unicode-cldr-annotations", "https://github.com/unicode-org/cldr-json", "Unicode-DFS-2016"),
            bundled("Leipzig Corpora frequency data", "vendored:leipzig-corpora", "https://corpora.uni-leipzig.de/", "CC-BY-3.0")
        )
        val components = libraries + bundledAssets
        val bom = linkedMapOf<String, Any>(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.6",
            "version" to 1,
            "metadata" to mapOf("component" to mapOf(
                "type" to "application",
                "name" to "Pastiera",
                "version" to versionName,
                "bom-ref" to appRef,
                "purl" to appRef,
                "properties" to listOf(mapOf(
                    "name" to "android:versionCode",
                    "value" to (android.defaultConfig.versionCode ?: 0).toString()
                ))
            )),
            "components" to components,
            "dependencies" to listOf(mapOf(
                "ref" to appRef,
                "dependsOn" to components.map { it["bom-ref"] }
            ))
        )
        stableSbomAssets.get().file("pastiera-sbom.cdx.json").asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(bom)) + "\n")
        }
    }
}
tasks.matching { it.name == "mergeStableReleaseAssets" }.configureEach {
    dependsOn(generateStableReleaseSbom)
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
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // RecyclerView per performance ottimali nella griglia emoji
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Emoji2 per supporto emoji future-proof
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    // Kotlinx Serialization for dictionary optimization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.3")
    // Shizuku for ADB shell access
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
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

// Numen's release ships the English dictionary only (about 35 MB smaller). Other languages
// download theirs in Settings from Pastiera's dictionary repository, like Greek or Czech already
// do. The raw *_base.json files are build-script inputs and are never read by the app.
tasks.matching { it.name == "mergeNightlyReleaseAssets" }.configureEach {
    doLast {
        val assetsDir = (this as com.android.build.gradle.tasks.MergeSourceSetFolders).outputDir.get().asFile
        assetsDir.resolve("common/dictionaries_serialized")
            .listFiles { file -> file.name.endsWith("_base.dict") && file.name != "en_base.dict" }
            ?.forEach { it.delete() }
        assetsDir.resolve("common/dictionaries")
            .listFiles { file -> file.name.endsWith("_base.json") }
            ?.forEach { it.delete() }
    }
}

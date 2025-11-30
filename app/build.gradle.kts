plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.0"
    id("com.ncorti.ktfmt.gradle") version "0.23.0"
    id("com.jaredsburrows.license") version "0.9.8"
}

android {
    namespace = "org.sunsetware.phocid"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.sunsetware.phocid"
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 20251130
        versionName = "20251130"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug { isPseudoLocalesEnabled = true }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        kotlinOptions.jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    @Suppress("UnstableApiUsage")
    androidResources.generateLocaleConfig = true
}

dependencies {
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // TODO: Maybe requiring a dependency just for `FilenameUtils` is too much
    implementation(libs.commons.io)
    implementation(libs.core.splashscreen)
    // ICU can't be mocked by Robolectric, WTF
    implementation(libs.icu4j)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.jaudiotagger)
    implementation(libs.reorderable)
    //noinspection UseTomlInstead
    implementation("org.sunsetware.omio:omio")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.assertj.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register("customSetup") { dependsOn("licenseReleaseReport") }

tasks.preBuild { dependsOn("customSetup") }

tasks.assembleUnitTest { dependsOn("customSetup") }

composeCompiler {
    stabilityConfigurationFiles =
        listOf(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

ktfmt { kotlinLangStyle() }

licenseReport {
    // Generate reports
    generateCsvReport = false
    generateHtmlReport = false
    generateJsonReport = true
    generateTextReport = false

    // Copy reports - These options are ignored for Java projects
    copyCsvReportToAssets = false
    copyHtmlReportToAssets = false
    copyJsonReportToAssets = true
    copyTextReportToAssets = false
    useVariantSpecificAssetDirs = false

    // Ignore licenses for certain artifact patterns
    ignoredPatterns = setOf()

    // Show versions in the report - default is false
    showVersions = true
}

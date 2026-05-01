import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "in.sakhi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.sakhi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Load secrets from local.properties (not committed to git)
    val localProps = Properties().also { props ->
        val file = rootProject.file("local.properties")
        if (file.exists()) props.load(file.inputStream())
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "USE_MOCK_INFERENCE", "true")
            buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"] ?: ""}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
            // Debug builds don't download the model — MockInferenceEngine is used instead
            buildConfigField("String", "MODEL_DOWNLOAD_URL", "\"\"")
            buildConfigField("String", "MODEL_SHA256", "\"\"")
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            buildConfigField("Boolean", "USE_MOCK_INFERENCE", "false")
            buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"] ?: ""}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
            // Set MODEL_DOWNLOAD_URL and MODEL_SHA256 in local.properties for release
            buildConfigField("String", "MODEL_DOWNLOAD_URL", "\"${localProps["MODEL_DOWNLOAD_URL"] ?: ""}\"")
            buildConfigField("String", "MODEL_SHA256", "\"${localProps["MODEL_SHA256"] ?: ""}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:inference"))
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:checkup"))
    implementation(project(":feature:chat"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    implementation(libs.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

plugins {
    id("com.android.application")
}

android {
    namespace = "com.adwio.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adwio.player"
        minSdk = 23
        targetSdk = 36
        versionCode = 33
        versionName = "5.0.5.5"
        buildConfigField("String", "APP_NAME", "\"ADWIO Player\"")
        buildConfigField("String", "CONTROL_API_URL", "\"${System.getenv("ADWIO_CONTROL_API_URL") ?: ""}\"")
    }

    val keystorePath = System.getenv("ADWIO_KEYSTORE_PATH")
    val keystorePassword = System.getenv("ADWIO_KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("ADWIO_KEY_ALIAS")
    val keyPasswordEnv = System.getenv("ADWIO_KEY_PASSWORD")

    val hasReleaseSigning =
        !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasEnv.isNullOrBlank() &&
        !keyPasswordEnv.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.picasso:picasso:2.8")
}

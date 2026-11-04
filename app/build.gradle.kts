import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clé d'envoi Google Play, hors du dépôt (voir docs/publication.md).
// Sans ce fichier, la version release est signée avec la clé de debug : installable pour tester,
// mais refusée par Google Play.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

android {
    namespace = "com.libreblock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.libreblock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // gradlew assembleDebug -Ppreview : copie séparée, pour tester le premier lancement sans
            // toucher aux réglages de l'appli installée.
            if (project.hasProperty("preview")) applicationIdSuffix = ".preview"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")

    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Llave fija del proyecto: TODAS las versiones se firman con ella, así cada APK nuevo se instala
// encima del anterior y conserva los datos. NO cambiar ni borrar app/firma/geomembrana.jks.
// (Por decisión del dueño del proyecto la llave está en el repositorio. Si se definen los secretos
// FIRMA_JKS_BASE64 / FIRMA_CLAVE en GitHub, se usan esos en su lugar.)
val firmaArchivo: String = System.getenv("FIRMA_ARCHIVO")?.ifBlank { null } ?: "firma/geomembrana.jks"
val firmaClave: String = System.getenv("FIRMA_CLAVE")?.ifBlank { null } ?: "xxvgn5Ppv920gOmVKUFH"

android {
    namespace = "co.obra.geomembrana"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.obra.geomembrana"
        minSdk = 26
        targetSdk = 34
        // Cada compilación en GitHub sube el número de versión (Android no instala una versión menor encima de una mayor)
        val numero = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 10
        versionCode = numero
        versionName = "1.1.$numero"
    }

    signingConfigs {
        create("obra") {
            storeFile = file(firmaArchivo)
            storePassword = firmaClave
            keyAlias = System.getenv("FIRMA_ALIAS")?.ifBlank { null } ?: "geomembrana"
            keyPassword = firmaClave
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("obra")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La llave de firma NO está en el repositorio (es público). En GitHub Actions llega desde los secretos
// FIRMA_JKS_BASE64 y FIRMA_CLAVE (ver README). Todas las versiones deben firmarse con la MISMA llave:
// así cada APK nuevo se instala encima del anterior y conserva los datos.
// Sin la llave (compilación local) se usa la de depuración: sirve para probar, no para distribuir.
val firmaArchivo: String? = System.getenv("FIRMA_ARCHIVO")
val firmaClave: String? = System.getenv("FIRMA_CLAVE")
val conFirma = !firmaArchivo.isNullOrBlank() && !firmaClave.isNullOrBlank() && file(firmaArchivo!!).exists()

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
        if (conFirma) {
            create("obra") {
                storeFile = file(firmaArchivo!!)
                storePassword = firmaClave
                keyAlias = System.getenv("FIRMA_ALIAS")?.ifBlank { null } ?: "geomembrana"
                keyPassword = firmaClave
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (conFirma) signingConfigs.getByName("obra") else signingConfigs.getByName("debug")
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

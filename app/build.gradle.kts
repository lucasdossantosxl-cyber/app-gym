import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// A chave de assinatura vem de variáveis de ambiente (GitHub Actions) ou de keystore.properties,
// que fica fora do Git. Sem ela, o build de release sai sem assinatura e não pode ser instalado.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun secret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: signing.getProperty(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "br.com.meutreino"
    // As bibliotecas AndroidX atuais exigem compilar com a API 37.1 ou mais nova.
    // O comportamento em tempo de execução segue o targetSdk.
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "br.com.meutreino"
        minSdk = 26
        targetSdk = 36
        // No GitHub Actions cada publicação recebe um código maior, para instalar por cima da anterior.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 4
        versionName = "2.1"
    }
    signingConfigs {
        val store = secret("MEUTREINO_KEYSTORE")
        if (store != null) create("release") {
            storeFile = rootProject.file(store)
            storePassword = secret("MEUTREINO_KEYSTORE_PASSWORD")
            keyAlias = secret("MEUTREINO_KEY_ALIAS")
            keyPassword = secret("MEUTREINO_KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    // Extrai o texto de PDFs no próprio aparelho para importar fichas de treino.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

}

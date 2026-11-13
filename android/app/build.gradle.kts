import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Podpis wydania poza repozytorium: android/keystore.properties (wzór: keystore.properties.example).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val backendOrigin = providers.gradleProperty("chatraBackendOrigin").orElse("").get()
val playProduct = providers.gradleProperty("chatraPlayProduct").orElse("").get()
val hdPilot = providers.gradleProperty("chatraHdPilot").orElse("false").get().toBooleanStrict()
require(backendOrigin.isEmpty() || Regex("https://[A-Za-z0-9.-]+(:[0-9]{1,5})?").matches(backendOrigin))
require(playProduct.isEmpty() || Regex("[a-z0-9_.]{1,100}").matches(playProduct))
require(backendOrigin.isNotEmpty() || (!hdPilot && playProduct.isEmpty()))

val firebaseConfig = listOf("ApiKey", "ApplicationId", "SenderId", "ProjectId").associateWith {
    providers.gradleProperty("chatraFirebase$it").orElse("").get().also { value ->
        require(value.matches(Regex("[A-Za-z0-9_:.\\-]*")))
    }
}
require(firebaseConfig.values.all { it.isEmpty() } || firebaseConfig.values.all { it.isNotEmpty() })

android {
    namespace = "app.chatra"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.chatra"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "data"
    productFlavors {
        // demo: dane z fixture CH006, brak prawdziwej wysyłki i zakupów.
        create("demo") {
            dimension = "data"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO", "true")
        }
        // full: provider Telephony, rola SMS i prawdziwe transporty.
        create("full") {
            dimension = "data"
            buildConfigField("boolean", "DEMO", "false")
            buildConfigField("String", "BACKEND_ORIGIN", "\"$backendOrigin\"")
            buildConfigField("String", "PLAY_PRODUCT", "\"$playProduct\"")
            buildConfigField("boolean", "HD_PILOT", hdPilot.toString())
            firebaseConfig.forEach { (name, value) -> buildConfigField("String", "FIREBASE_${name.uppercase()}", "\"$value\"") }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    "demoImplementation"(project(":core:testing"))
    "fullImplementation"(project(":core:database"))
    "fullImplementation"(libs.androidx.work.runtime)
    "fullImplementation"(libs.play.billing)
    "fullImplementation"("com.google.firebase:firebase-messaging:25.1.3")
    // Billing brings Fragment 1.1 through Play Services. Activity Result needs 1.3 or newer.
    "fullImplementation"(libs.androidx.fragment)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

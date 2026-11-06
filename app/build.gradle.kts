plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "pl.chatra"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "pl.chatra"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    flavorDimensions += "mode"
    productFlavors {
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "Chatra Demo")
        }
        create("production") {
            dimension = "mode"
            resValue("string", "app_name", "Chatra")
        }
    }
    signingConfigs {
        val store = System.getenv("CHATRA_KEYSTORE_PATH")
        if (!store.isNullOrBlank()) {
            create("externalRelease") {
                storeFile = file(store)
                storePassword = System.getenv("CHATRA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHATRA_KEY_ALIAS")
                keyPassword = System.getenv("CHATRA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("externalRelease")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
val generateDemoFixtures by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("node", "scripts/ch006_build_fixtures.mjs")
    inputs.files(rootProject.file("scripts/ch006_build_fixtures.mjs"), rootProject.file("docs/implementacja_android/Chatra_Pakiet_referencyjny/style_zrodlowe.json"))
    outputs.file(rootProject.layout.buildDirectory.file("ch006/screen-fixtures.json"))
}
abstract class PrepareDemoAssets : DefaultTask() {
    @get:InputFile abstract val referenceHtml: RegularFileProperty
    @get:InputFile abstract val fixturesJson: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val target = outputDirectory.get().asFile
        target.mkdirs()
        referenceHtml.get().asFile.copyTo(target.resolve("reference.html"), overwrite = true)
        fixturesJson.get().asFile.copyTo(target.resolve("screen-fixtures.json"), overwrite = true)
    }
}
val prepareDemoAssets by tasks.registering(PrepareDemoAssets::class) {
    dependsOn(generateDemoFixtures)
    referenceHtml.set(rootProject.layout.projectDirectory.file("docs/implementacja_android/Chatra_Pakiet_referencyjny/Chatra — Liquid Glass.html"))
    fixturesJson.set(rootProject.layout.buildDirectory.file("ch006/screen-fixtures.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/demoAssets"))
}
androidComponents {
    onVariants(selector().withFlavor("mode" to "demo")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareDemoAssets) { it.outputDirectory }
    }
}

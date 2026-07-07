import org.gradle.api.file.Directory
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.username.cardapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.username.cardapp"
        minSdk = 36
        targetSdk = 37
        versionCode = 4
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                props.load(propsFile.inputStream())
                storeFile = rootProject.file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            } else {
                // CI: read from environment variables
                storeFile = file(System.getenv("KEYSTORE_PATH") ?: "/dev/null")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Stub android.* methods (e.g. Log) as no-ops so JVM unit tests can
        // exercise classes that log.
        unitTests.isReturnDefaultValues = true
    }
    buildToolsVersion = "36.1.0"

}

// Release builds bundle the full image set from scripts/images/ as a generated
// asset directory; debug builds use the small subset in src/main/assets/images/.
// Wired through the Variant API so AGP owns the output location and task dependency.
abstract class CopyReleaseImagesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val images: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dest = outputDir.get().asFile.resolve("images")
        dest.deleteRecursively()
        dest.mkdirs()
        images.files.forEach { it.copyTo(dest.resolve(it.name), overwrite = true) }
    }
}

val releaseImagesDir: Directory = rootProject.layout.projectDirectory.dir("scripts/images")

val copyReleaseImages = tasks.register<CopyReleaseImagesTask>("copyReleaseImages") {
    images.from(fileTree(releaseImagesDir))
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyReleaseImages,
            CopyReleaseImagesTask::outputDir,
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
    // Export schema JSONs so future migrations can be validated with MigrationTestHelper.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

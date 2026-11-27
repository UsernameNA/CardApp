import org.gradle.api.file.Directory
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "com.github.username.cardapp"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.github.username.cardapp"
        minSdk = 36
        targetSdk = 37
        versionCode = 8
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Local builds read keystore.properties; the Release workflow passes the same values as
        // environment variables. Without either, release is left unsigned and refuses to package.
        val props = Properties()
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
        val keystore = props.getProperty("storeFile")?.let { rootProject.file(it) }
            ?: System.getenv("KEYSTORE_PATH")?.let { file(it) }
        val keystorePassword = props.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
        val alias = props.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
        val aliasPassword = props.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
        if (keystore?.isFile == true && !keystorePassword.isNullOrEmpty() &&
            !alias.isNullOrEmpty() && !aliasPassword.isNullOrEmpty()
        ) {
            create("release") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = alias
                keyPassword = aliasPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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
        unitTests.all {
            // Robolectric reaches into FileDescriptor internals, which recent JDKs close off.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

if (android.signingConfigs.findByName("release") == null) {
    tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
        doFirst {
            throw GradleException(
                "No release signing key: add keystore.properties (see README, Releasing) or set " +
                    "KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
            )
        }
    }
}

// Release builds bundle the full image set from scripts/images/ as a generated
// asset directory; debug builds use the small subset in src/main/assets/images/.
// Wired through the Variant API so AGP owns the output location and task dependency.
abstract class CopyReleaseImagesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val images: ConfigurableFileCollection

    /** Fewer images than this means scripts/images/ is missing or incomplete. */
    @get:Input
    abstract val minimumImageCount: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val webps = images.files.filter { it.extension == "webp" }
        if (webps.size < minimumImageCount.get()) {
            throw GradleException(
                "Found ${webps.size} card images in scripts/images/, expected at least " +
                    "${minimumImageCount.get()}. A release built now would render every card " +
                    "blank (see README, Releasing). Pass -PallowMissingReleaseImages=true only " +
                    "for a build that will not be published."
            )
        }
        val dest = outputDir.get().asFile.resolve("images")
        dest.deleteRecursively()
        dest.mkdirs()
        images.files.forEach { it.copyTo(dest.resolve(it.name), overwrite = true) }
    }
}

val releaseImagesDir: Directory = rootProject.layout.projectDirectory.dir("scripts/images")

val copyReleaseImages = tasks.register<CopyReleaseImagesTask>("copyReleaseImages") {
    images.from(fileTree(releaseImagesDir))
    // The full art set is ~3,080 images; well under that, the copy source is broken.
    val fullSetMinimum = 3000
    minimumImageCount.set(
        providers.gradleProperty("allowMissingReleaseImages")
            .map { if (it.toBoolean()) 0 else fullSetMinimum }
            .orElse(fullSetMinimum)
    )
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

room {
    // Exported schema JSONs are read by DatabaseMigrationTest.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

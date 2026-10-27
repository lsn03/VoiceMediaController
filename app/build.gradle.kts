plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    alias(libs.plugins.hilt)
    kotlin("kapt")
    alias(libs.plugins.ksp)
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

android {
    namespace = "ru.lsn03.voicemediacontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.lsn03.voicemediacontroller"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    hilt {
        enableAggregatingTask = false
    }
    kapt {
        correctErrorTypes = true
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)



    // Навигация
    implementation(libs.androidx.navigation.compose)
    // ViewModel в Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose.v285)
    // (Опционально) если будешь делать @HiltViewModel и hiltViewModel()
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.vosk.android)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation("com.squareup:javapoet:1.13.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

}

// Classes excluded from coverage: generated code (Hilt, Room, Compose, DataBinding, resources).
val jacocoCoverageExclusions = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Factory*.*",
    "**/*_MembersInjector*.*",
    "**/Dagger*Component*.*",
    "**/Hilt_*.*",
    "**/*_HiltModules*.*",
    "**/*_Hilt*.*",
    "**/*_Impl*.*",
    "**/ComposableSingletons*.*",
    "**/*ComposableSingletons*.*",
    "**/databinding/**",
    "**/android/**/*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates JaCoCo coverage report merged across build-type unit tests."

    val unitTestTasks = tasks.withType<Test>().matching { it.name.startsWith("test") && it.name.endsWith("UnitTest") }
    dependsOn(unitTestTasks)

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }

    // Use the debug variant's bytecode as the single source for class dirs; adding both debug and
    // release would feed JaCoCo duplicate class entries. Debug carries `enableUnitTestCoverage`.
    val kotlinClassDir = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(jacocoCoverageExclusions)
    }
    val javaClassDir = fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug") {
        exclude(jacocoCoverageExclusions)
    }
    classDirectories.setFrom(files(kotlinClassDir, javaClassDir))

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "jacoco/testReleaseUnitTest.exec",
                "outputs/unit_test_code_coverage/**/*.exec"
            )
        }
    )

    // Baseline reports must be produced even when there is no execution data yet
    // (the test suite grows incrementally; android-tester adds coverage after this PR).
    setOnlyIf { true }
}
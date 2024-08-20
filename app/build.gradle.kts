import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.elgenium.smartcity"
    compileSdk = 34

    val properties = Properties().apply {
        load(project.rootProject.file("local.properties").inputStream())
    }
    val clientID = properties.getProperty("DEFAULT_CLIENT_ID") ?: ""

    defaultConfig {
        buildConfigField("String", "DEFAULT_CLIENT_ID", clientID)
        applicationId = "com.elgenium.smartcity"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures{
        buildConfig = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.storage.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation (libs.lottie)
    implementation (libs.androidx.viewpager2)
    implementation(platform(libs.firebase.bom))
    implementation (libs.firebase.auth)
    implementation (libs.firebase.database.ktx)
    implementation (libs.play.services.auth)
    implementation (libs.circleimageview)
    implementation (libs.glide)
    annotationProcessor (libs.compiler)

}
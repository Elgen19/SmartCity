import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.gms.google-services")
    id ("kotlin-parcelize")
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)

}


secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
    ignoreList.add("keyToIgnore")
    ignoreList.add("sdk.*")
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
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resConfigs("en")
        multiDexEnabled = true
    }

    dexOptions {
        // This increases the amount of memory available to the dexer. This is required to build
        // apps using the Navigation SDK.
        javaMaxHeapSize = "4g"
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
        isCoreLibraryDesugaringEnabled = true
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
    implementation(libs.core)
    implementation(libs.play.services.location)
    implementation(libs.places)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.play.services.maps)
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
    implementation(libs.android)
    implementation (libs.androidx.transition)
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation ("com.google.android.libraries.navigation:navigation:5.2.1")
    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:1.1.9")
    implementation ("androidx.fragment:fragment:1.8.3")
    annotationProcessor (libs.androidx.annotation)
    implementation(libs.material.v1120)











}
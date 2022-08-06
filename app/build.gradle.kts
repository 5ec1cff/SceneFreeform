
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.7.10-1.0.6"
    id("dev.rikka.tools.refine") version "3.1.1"
}

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "five.ec1cff.scene_freeform"
        minSdk = 27
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"

        /*val configAuthority = "${applicationId}.provider"
        manifestPlaceholders["configAuthority"] = configAuthority
        buildConfigField("String", "CONFIG_AUTHORITY", "\"${configAuthority}\"")
        resValue("string", "config_authority", configAuthority)*/
    }

    buildTypes {
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
}

dependencies {
    implementation("androidx.preference:preference:1.2.0")
    compileOnly(project(":hidden_api_stub"))
    val hiddenApiRefineVersion: String by rootProject.extra
    implementation("dev.rikka.tools.refine:runtime:$hiddenApiRefineVersion")

    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("com.google.android.material:material:1.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    val yukiHookVersion: String by rootProject.extra
    implementation("com.highcapable.yukihookapi:api:$yukiHookVersion")
    ksp("com.highcapable.yukihookapi:ksp-xposed:$yukiHookVersion")
}

plugins {
    id("com.android.application") version "7.2.2" apply false
    id("com.android.library") version "7.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.7.10" apply false
}

val yukiHookVersion by extra("1.0.92")
val hiddenApiRefineVersion by extra("3.0.3")
val navVersion by extra("2.5.1")

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
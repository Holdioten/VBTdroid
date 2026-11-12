plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.vbtdroid.pcreplay.MainKt")
}

dependencies {
    implementation(project(":core"))
}

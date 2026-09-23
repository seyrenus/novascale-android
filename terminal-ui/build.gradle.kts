plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cc.galaxnet.novascale.terminal"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core-api"))
    testImplementation(libs.junit)
}

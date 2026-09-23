import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appVersionName = providers.gradleProperty("NOVASCALE_VERSION_NAME")
    .orElse(providers.environmentVariable("NOVASCALE_VERSION_NAME"))
    .orElse("0.1.0-dev")
    .get()
val appVersionCode = providers.gradleProperty("NOVASCALE_VERSION_CODE")
    .orElse(providers.environmentVariable("NOVASCALE_VERSION_CODE"))
    .orElse("1")
    .map(String::toInt)
    .get()
val sourceCodeUrl = providers.gradleProperty("NOVASCALE_SOURCE_URL")
    .orElse(providers.environmentVariable("NOVASCALE_SOURCE_URL"))
    .orElse("")
    .get()
// Owner-approved internal-test exception: keep a local source tag while public
// repository/document curation is deferred. Public builds still require a URL.
val releaseTrack = providers.environmentVariable("NOVASCALE_RELEASE_TRACK").orElse("").get()
val deferSourcePublication = providers.environmentVariable("NOVASCALE_DEFER_SOURCE_PUBLICATION")
    .orElse("false").map(String::toBoolean).get()
// Derive displayed upstream version from the actual module pin, never a UI literal.
val tailscaleVersion = Regex("""(?m)^\s*tailscale\.com\s+(v\S+)""")
    .find(providers.fileContents(rootProject.layout.projectDirectory.file("native/nova-tailnet-go/go.mod")).asText.get())
    ?.groupValues?.get(1) ?: error("Missing pinned tailscale.com module version")
val privacyPolicyUrl = "https://galaxnet.dev/nova/legal/privacy/"
val termsOfServiceUrl = "https://galaxnet.dev/nova/legal/tos/"
val uploadStorePath = providers.environmentVariable("NOVASCALE_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("NOVASCALE_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("NOVASCALE_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("NOVASCALE_UPLOAD_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    uploadStorePath,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

val tailnetAar = layout.projectDirectory.file("libs/nova-tailnet.aar")
val rustJniLibs = layout.buildDirectory.dir("generated/novaCore/jniLibs")
val generatedTerminalFontResources = layout.buildDirectory.dir("generated/terminalFonts/res")
val generatedLegalAssets = layout.buildDirectory.dir("generated/legalAssets")
val syncLegalAssets by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Package the application license and third-party notice inventory"
    from(rootProject.file("LICENSE")) {
        rename { "GPL-3.0-only.txt" }
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    into(generatedLegalAssets.map { it.dir("legal") })
}
val fetchTerminalFonts by tasks.registering(Exec::class) {
    group = "native"
    description = "Fetch checksum-pinned open-source terminal fonts"
    workingDir(rootProject.layout.projectDirectory)
    commandLine(
        "bash",
        rootProject.file("scripts/fetch-terminal-fonts.sh").absolutePath,
        generatedTerminalFontResources.get().dir("font").asFile.absolutePath,
    )
    inputs.file(rootProject.file("scripts/fetch-terminal-fonts.sh"))
    inputs.file(rootProject.file("third_party/terminal-fonts/README.md"))
    outputs.dir(generatedTerminalFontResources)
}
val buildTailnetAar by tasks.registering(Exec::class) {
    group = "native"
    description = "Build the pinned Tailscale gomobile AAR from source"
    workingDir(rootProject.layout.projectDirectory)
    commandLine(
        "bash",
        rootProject.file("scripts/build-tailnet-aar.sh").absolutePath,
        tailnetAar.asFile.absolutePath,
    )
    inputs.files(rootProject.fileTree("native/nova-tailnet-go") {
        exclude(".tools/**")
    })
    inputs.file(rootProject.file("scripts/build-tailnet-aar.sh"))
    outputs.file(tailnetAar)
}

val buildRustCore by tasks.registering(Exec::class) {
    group = "native"
    description = "Build the pinned Ghostty/Russh/Russh-SFTP Android libraries from source"
    workingDir(rootProject.layout.projectDirectory)
    commandLine(
        "bash",
        rootProject.file("scripts/build-rust-core.sh").absolutePath,
        rustJniLibs.get().asFile.absolutePath,
    )
    inputs.files(rootProject.fileTree("native/nova-core-rust") {
        exclude("target/**")
    })
    inputs.files(rootProject.fileTree("native/nova-ghostty-zig") {
        exclude(".tools/**", ".zig-cache/**", "zig-out/**")
    })
    inputs.file(rootProject.file("scripts/build-rust-core.sh"))
    inputs.file(rootProject.file("scripts/ensure-zig.sh"))
    outputs.dir(rustJniLibs)
}

tasks.named("preBuild") {
    dependsOn(buildTailnetAar, buildRustCore, fetchTerminalFonts, syncLegalAssets)
}

android {
    namespace = "cc.galaxnet.novascale"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.galaxnet.novascale"
        minSdk = 28
        testInstrumentationRunner = "cc.galaxnet.novascale.browser.ProxyInstrumentation"
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "TAILSCALE_VERSION", tailscaleVersion.asBuildConfigString())
        buildConfigField("String", "PRIVACY_POLICY_URL", privacyPolicyUrl.asBuildConfigString())
        buildConfigField("String", "TERMS_OF_SERVICE_URL", termsOfServiceUrl.asBuildConfigString())
        buildConfigField("String", "SOURCE_CODE_URL", sourceCodeUrl.asBuildConfigString())
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("releaseUpload") {
                storeFile = file(uploadStorePath!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "DebugProbesKt.bin"
    }

    sourceSets.getByName("main").apply {
        jniLibs.directories.add(rustJniLibs.get().asFile.absolutePath)
        assets.directories.add(rootProject.file("third_party").absolutePath)
        assets.directories.add(generatedLegalAssets.get().asFile.absolutePath)
        res.directories.add(generatedTerminalFontResources.get().asFile.absolutePath)
    }
}

val validatePlayRelease by tasks.registering {
    group = "distribution"
    description = "Reject incomplete Play Store release metadata or upload signing"
    doLast {
        val sourceUri = runCatching { URI(sourceCodeUrl) }.getOrNull()
        check(!appVersionName.endsWith("-dev")) {
            "NOVASCALE_VERSION_NAME must be a non-development version for Play release."
        }
        check(appVersionCode > 0) {
            "NOVASCALE_VERSION_CODE must be a positive integer."
        }
        check(!deferSourcePublication || releaseTrack == "internal") {
            "Source publication may only be deferred for the internal test track."
        }
        check(
            (deferSourcePublication && sourceCodeUrl.isBlank()) ||
            (sourceUri?.scheme.equals("https", ignoreCase = true) &&
                !sourceUri?.host.isNullOrBlank() &&
                sourceUri?.userInfo == null)
        ) {
            "NOVASCALE_SOURCE_URL must be the public HTTPS corresponding-source URL."
        }
        check(!sourceCodeUrl.contains("example.invalid")) {
            "NOVASCALE_SOURCE_URL must not use the documentation placeholder."
        }
        check(releaseSigningConfigured) {
            "All NOVASCALE_UPLOAD_* environment variables are required for Play release."
        }
        check(file(uploadStorePath!!).isFile) {
            "NOVASCALE_UPLOAD_STORE_FILE must point to the upload keystore."
        }
    }
}

tasks.register("playReleaseBundle") {
    group = "distribution"
    description = "Validate release metadata/signing and build the Play upload AAB"
    dependsOn(validatePlayRelease, "bundleRelease")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    mustRunAfter(validatePlayRelease)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core-api"))
    implementation(project(":terminal-ui"))
    implementation(files(tailnetAar))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.treesitter)
    implementation(libs.android.treesitter.c)
    implementation(libs.android.treesitter.cpp)
    implementation(libs.android.treesitter.java)
    implementation(libs.android.treesitter.json)
    implementation(libs.android.treesitter.kotlin)
    implementation(libs.android.treesitter.properties)
    implementation(libs.android.treesitter.python)
    implementation(libs.android.treesitter.xml)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

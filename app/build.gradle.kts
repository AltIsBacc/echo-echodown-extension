plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    val libVersion: String by project

    implementation(project(":ext"))
    compileOnly("dev.brahmkshatriya.echo:common:$libVersion")
    compileOnly(kotlin("stdlib"))

    implementation(files("libs/ffmpeg-kit.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
}

// --- version ---
val verCode: Integer by project
val verName: String by project

// --- metadata ---
val extType: String by project
val extId: String by project
val extName: String by project
val extAuthor: String by project

val extClass = "AndroidED"

android {
    namespace = "dev.brahmkshatriya.echo.extension"

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.extension.$extId"

        versionCode = verCode
        versionName = verName

        manifestPlaceholders.putAll(
            mapOf(
                "type" to "dev.brahmkshatriya.echo.$extType",
                "id" to extId,
                "class_path" to "dev.brahmkshatriya.echo.extension.$extClass",
                "version" to verName,
                "version_code" to verCode.toString(),
                "app_name" to "Echo : $extName Extension",
                "name" to extName,
                "author" to extAuthor
            )
        )
    }

    buildTypes.all {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "$buildDir/generated/proguard/generated-rules.pro"
        )
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
}

val generatedProguard = layout.buildDirectory.file("generated/proguard/generated-rules.pro")

tasks.register("generateProguardRules") {
    doLast {
        val file = generatedProguard.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            -dontobfuscate
            -keep class dev.brahmkshatriya.echo.extension.$extClass
            """.trimIndent()
        )
    }
}

tasks.named("preBuild") {
    dependsOn("generateProguardRules")
}

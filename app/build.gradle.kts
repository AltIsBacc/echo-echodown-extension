plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("ext-convention")
}

val meta = extensionMetadata()

dependencies {
    implementation(project(":ext"))
    compileOnly("dev.brahmkshatriya.echo:common:${property("libVersion")}")
    compileOnly(kotlin("stdlib"))

    implementation(files("libs/ffmpeg-kit.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
}

android {
    namespace = "dev.brahmkshatriya.echo.extension"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.extension.${meta.id}"
        minSdk = 24
        targetSdk = 36
        
        versionCode = meta.verCode
        versionName = meta.verName

        manifestPlaceholders.putAll(
            mapOf(
                "type"         to "dev.brahmkshatriya.echo.${meta.type}",
                "id"           to meta.id,
                "class_path"   to "dev.brahmkshatriya.echo.extension.${meta.className}",
                "version"      to meta.verName,
                "version_code" to meta.verCode.toString(),
                "app_name"     to "Echo : ${meta.name} Extension",
                "name"         to meta.name,
                "author"       to meta.author,
                "icon_url"     to meta.iconUrl,
                "description"  to meta.description,
                "author_url"   to meta.authorUrl,
                "repo_url"     to meta.repoUrl,
                "update_url"   to meta.updateUrl,
            )
        )
    }

    buildTypes.all {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            layout.buildDirectory.file("generated/proguard/generated-rules.pro").get().asFile.path
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

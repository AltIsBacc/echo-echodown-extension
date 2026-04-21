plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.0.2"
}

dependencies {
    val libVersion: String by project

    compileOnly("dev.brahmkshatriya.echo:common:$libVersion")
    compileOnly(kotlin("stdlib"))
}

// --- version ---
val verCode: Integer by project
val verName: String by project

// --- metadata ---
val extId: String by project
val extType: String by project
val extClass: String by project
val extName: String by project
val extAuthor: String by project

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.brahmkshatriya.echo.extension"
            artifactId = extId
            version = verName
            from(components["java"])
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set(extId)
    archiveVersion.set(verName)

    manifest {
        attributes(
            mapOf(
                "Extension-Id" to extId,
                "Extension-Type" to extType,
                "Extension-Class" to extClass,
                "Extension-Version-Code" to verCode,
                "Extension-Version-Name" to verName,
                "Extension-Name" to extName,
                "Extension-Author" to extAuthor
            )
        )
    }
}

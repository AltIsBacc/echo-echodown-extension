plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    alias(libs.plugins.shadow)
    id("ext-convention")
}

val meta = extensionMetadata()

dependencies {
    compileOnly(libs.echo.common)
    compileOnly(libs.kotlin.stdlib)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.brahmkshatriya.echo.extension"
            artifactId = meta.id
            version = meta.verName
            from(components["java"])
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set(meta.id)
    archiveVersion.set(meta.verName)

    manifest {
        attributes(
            mapOf(
                "Extension-Id"           to meta.id,
                "Extension-Type"         to meta.type,
                "Extension-Class"        to meta.className,
                "Extension-Version-Code" to meta.verCode,
                "Extension-Version-Name" to meta.verName,
                "Extension-Name"         to meta.name,
                "Extension-Author"       to meta.author,
            )
        )
    }
}

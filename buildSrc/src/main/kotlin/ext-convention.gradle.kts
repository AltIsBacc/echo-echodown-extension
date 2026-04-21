val meta = project.extensionMetadata()

val generatedProguard = layout.buildDirectory.file("generated/proguard/generated-rules.pro")

tasks.register("generateProguardRules") {
    doLast {
        val file = generatedProguard.get().asFile
        file.parentFile.mkdirs()
        file.writeText("""
            -dontobfuscate
            -keep class dev.brahmkshatriya.echo.extension.${meta.className}
        """.trimIndent())
    }
}

tasks.named("preBuild") {
    dependsOn("generateProguardRules")
}

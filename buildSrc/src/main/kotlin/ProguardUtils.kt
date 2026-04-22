import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.Task

fun Project.generateProguardRules(meta: ExtensionMetadata): TaskProvider<Task> =
    tasks.register("generateProguardRules") {
        doLast {
            layout.buildDirectory
                .file("generated/proguard/generated-rules.pro")
                .get().asFile
                .apply { parentFile.mkdirs() }
                .writeText("""
                    -dontobfuscate
                    -keep class dev.brahmkshatriya.echo.extension.${meta.className}
                """.trimIndent())
        }
    }

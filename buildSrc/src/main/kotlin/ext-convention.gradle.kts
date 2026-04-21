import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.android.build.gradle.BaseExtension

plugins.withType<JavaPlugin> {
    configure<JavaPluginExtension> {
        sourceCompatibility = ProjectConfig.javaVersion
        targetCompatibility = ProjectConfig.javaVersion
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = ProjectConfig.jvmTarget
    }
}

plugins.withId("com.android.application") { configureAndroid() }
plugins.withId("com.android.library") { configureAndroid() }

fun Project.configureAndroid() {
    extensions.configure<BaseExtension> {
        compileOptions {
            sourceCompatibility = ProjectConfig.javaVersion
            targetCompatibility = ProjectConfig.javaVersion
        }
    }

    val meta = project.extensionMetadata()
    val generatedProguard = layout.buildDirectory.file("generated/proguard/generated-rules.pro")

    val generateTask = tasks.register("generateProguardRules") {
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
        dependsOn(generateTask)
    }
}

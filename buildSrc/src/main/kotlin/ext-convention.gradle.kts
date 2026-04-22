import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = ProjectConfig.javaVersion
        targetCompatibility = ProjectConfig.javaVersion
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(ProjectConfig.jvmTarget)
    }
}

plugins.withId("com.android.application") {
    extensions.configure<ApplicationExtension>("android") {
        compileOptions {
            sourceCompatibility = ProjectConfig.javaVersion
            targetCompatibility = ProjectConfig.javaVersion
        }
    }
}

plugins.withId("com.android.library") {
    extensions.configure<LibraryExtension>("android") {
        compileOptions {
            sourceCompatibility = ProjectConfig.javaVersion
            targetCompatibility = ProjectConfig.javaVersion
        }
    }
}

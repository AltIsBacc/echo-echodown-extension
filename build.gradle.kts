// Top-level build file

plugins {
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    /** PROTOBUF **/
}

subprojects {

    // --- Android config (SAFE VERSION) ---
    plugins.withId("com.android.application") {
        extensions.configure("android") {
            // use Groovy-style access (works in Kotlin DSL via reflection)
            this as com.android.build.gradle.internal.dsl.BaseAppModuleExtension

            compileSdkVersion(36)

            defaultConfig {
                minSdk = 24
                targetSdk = 36
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    // --- Java config ---
    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    // --- Kotlin JVM target (SAFE fallback) ---
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("ext-convention")
}

dependencies {
    implementation(libs.echo.common)
    implementation(libs.kotlin.stdlib)
    implementation(libs.org.json)
}

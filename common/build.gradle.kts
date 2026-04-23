plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("ext-convention")
}

dependencies {
    compileOnly(libs.echo.common)
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.org.json)
}

import org.gradle.api.Project

data class ExtensionMetadata(
    val id: String,
    val type: String,
    val name: String,
    val author: String,
    val className: String,
    val verCode: Int,
    val verName: String,
)

fun Project.extensionMetadata() = ExtensionMetadata(
    id        = property("extId").toString(),
    type      = property("extType").toString(),
    name      = property("extName").toString(),
    author    = property("extAuthor").toString(),
    className = property("extClass").toString(),
    verCode   = property("verCode").toString().toInt(),
    verName   = property("verName").toString(),
)

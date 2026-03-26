package io.gopherglide.intellij.ggideaplugin.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class GopherGlideJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(
            object : JsonSchemaFileProvider {
                override fun isAvailable(file: VirtualFile): Boolean {
                    return file.name.endsWith(".gg.yaml")
                }

                override fun getName(): String = "Gopher-Glide Config"

                override fun getSchemaFile(): VirtualFile? {
                    return JsonSchemaProviderFactory.getResourceFile(
                        GopherGlideJsonSchemaProviderFactory::class.java,
                        "/schema/gg-schema.json"
                    )
                }

                override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
            }
        )
    }
}

package io.gopherglide.intellij.ggideaplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class YamlFileReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val pattern = PlatformPatterns.psiElement(YAMLScalar::class.java)
            .withParent(
                PlatformPatterns.psiElement(YAMLKeyValue::class.java)
                    .withName("httpFile")
            )

        registrar.registerReferenceProvider(pattern, object : PsiReferenceProvider() {
            override fun getReferencesByElement(
                element: PsiElement,
                context: ProcessingContext
            ): Array<PsiReference> {
                val scalar = element as YAMLScalar
                val fileName = scalar.textValue
                
                val text = scalar.text
                val range = if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
                    TextRange(1, text.length - 1)
                } else if (text.startsWith("'") && text.endsWith("'") && text.length >= 2) {
                    TextRange(1, text.length - 1)
                } else {
                    TextRange(0, text.length)
                }

                return arrayOf(YamlFileReference(scalar, range, fileName))
            }
        })
    }
}

class YamlFileReference(
    element: YAMLScalar,
    textRange: TextRange,
    private val fileName: String
) : PsiReferenceBase<YAMLScalar>(element, textRange) {

    override fun resolve(): PsiElement? {
        val fileDir = element.containingFile.originalFile.virtualFile?.parent ?: return null
        val targetVirtualFile = fileDir.findChild(fileName) ?: return null
        return PsiManager.getInstance(element.project).findFile(targetVirtualFile)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

package org.elixir_lang.code_insight.completion.provider

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.ProcessingContext
import org.elixir_lang.psi.CallDefinitionClause.nameArityRange
import org.elixir_lang.psi.ElixirTypes
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.call.macroChildCalls
import org.elixir_lang.psi.impl.maybeModularNameToModular

class CallDefinitionClause : CompletionProvider<CompletionParameters>() {
    private fun callDefinitionClauseLookupElements(scope: Call): Iterable<LookupElement> =
            scope
                    .macroChildCalls()
                    .filter { org.elixir_lang.psi.CallDefinitionClause.`is`(it) }
                    .mapNotNull {
                        nameArityRange(it)?.let { (name, _) ->
                            org.elixir_lang.code_insight.lookup.element.CallDefinitionClause.createWithSmartPointer(
                                    name,
                                    it
                            )
                        }
                    }

    private fun maybeModularName(parameters: CompletionParameters): PsiElement? =
        parameters.originalPosition?.let { originalPosition ->
            originalPosition.parent?.let { originalParent ->
                val grandParent = originalParent.parent

                if (grandParent is org.elixir_lang.psi.qualification.Qualified) {
                    grandParent.qualifier()
                } else if (originalPosition is PsiWhiteSpace) {
                    val originalPositionOffset = originalPosition.textOffset

                    if (originalPositionOffset > 0) {
                        val previousElement = parameters.originalFile.findElementAt(originalPositionOffset - 1)

                        if (previousElement != null && previousElement.node.elementType === ElixirTypes.DOT_OPERATOR) {
                            previousElement.parent.prevSibling
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet) {
        maybeModularName(parameters)?.let { maybeModularName ->
            maybeModularName.maybeModularNameToModular(maxScope = maybeModularName.containingFile, useCall = null)?.let { modular ->
                if (resultSet.prefixMatcher.prefix.endsWith(".")) {
                    resultSet.withPrefixMatcher("")
                } else {
                    resultSet
                }.addAllElements(
                        callDefinitionClauseLookupElements(modular)
                )
            }
        }
    }
}

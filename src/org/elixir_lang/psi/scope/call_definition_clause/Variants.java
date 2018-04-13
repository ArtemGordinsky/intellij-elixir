package org.elixir_lang.psi.scope.call_definition_clause;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashMap;
import org.elixir_lang.annotator.Parameter;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.psi.call.Named;
import org.elixir_lang.psi.scope.CallDefinitionClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE;

public class Variants extends CallDefinitionClause {
    /*
     * CONSTANTS
     */

    private static final Key<Call> ENTRANCE_CALL_DEFINITION_CLAUSE = new Key<Call>("ENTRANCE_CALL_DEFINITION_CLAUSE");

    /*
     * Static Methods
     */

    @Nullable
    public static List<LookupElement> lookupElementList(@NotNull PsiElement entrance) {
        Variants variants = new Variants();

        Parameter parameter = Parameter.putParameterized(new Parameter(entrance));
        Call entranceCallDefinitionClause = null;

        if (parameter.isCallDefinitionClauseName()) {
            entranceCallDefinitionClause = (Call) parameter.parameterized;
        }

        ResolveState resolveState = ResolveState
                .initial()
                .put(ENTRANCE, entrance)
                .put(ENTRANCE_CALL_DEFINITION_CLAUSE, entranceCallDefinitionClause);

        PsiTreeUtil.treeWalkUp(
                variants,
                entrance,
                entrance.getContainingFile(),
                resolveState
        );
        List<LookupElement> lookupElementList = new ArrayList<LookupElement>();
        lookupElementList.addAll(variants.getLookupElementCollection());

        return lookupElementList;
    }

    /*
     * Fields
     */

    @Nullable
    private Map<PsiElement, LookupElement> lookupElementByPsiElement = null;

    /*
     * Protected Instance Methods
     */

    /**
     * Called on every {@link Call} where {@link org.elixir_lang.structure_view.element.CallDefinitionClause#is} is
     * {@code true} when checking tree with {@link #execute(Call, ResolveState)}
     *
     * @return {@code true} to keep searching up tree; {@code false} to stop searching.
     */
    @Override
    protected boolean executeOnCallDefinitionClause(Call element, ResolveState state) {
        Call entranceCallDefinitionClause = state.get(ENTRANCE_CALL_DEFINITION_CLAUSE);

        if (entranceCallDefinitionClause == null || !element.isEquivalentTo(entranceCallDefinitionClause)) {
            addToLookupElementByPsiElement(element);
        }

        return true;
    }

    /**
     * Whether to continue searching after each Module's children have been searched.
     *
     * @return {@code true} to keep searching up the PSI tree; {@code false} to stop searching.
     */
    @Override
    protected boolean keepProcessing() {
        return false;
    }

    /*
     * Private Instance Methods
     */

    private void addToLookupElementByPsiElement(@NotNull Call call) {
        if (call instanceof Named) {
            Named named = (Named) call;
            String name = named.getName();

            if (name != null) {
                if (lookupElementByPsiElement == null || !lookupElementByPsiElement.containsKey(named)) {
                    if (lookupElementByPsiElement == null) {
                        lookupElementByPsiElement = new THashMap<>();
                    }

                    lookupElementByPsiElement.put(
                            named,
                            LookupElementBuilder.createWithSmartPointer(
                                    name,
                                    named
                            ).withRenderer(
                                    new org.elixir_lang.code_insight.lookup.element_renderer.CallDefinitionClause(name)
                            )
                    );
                }
            }
        }
    }

    @NotNull
    private Collection<LookupElement> getLookupElementCollection() {
        Collection<LookupElement> lookupElementCollection;

        if (lookupElementByPsiElement != null) {
            lookupElementCollection = lookupElementByPsiElement.values();
        } else {
            lookupElementCollection = Collections.emptySet();
        }

        return lookupElementCollection;
    }
}

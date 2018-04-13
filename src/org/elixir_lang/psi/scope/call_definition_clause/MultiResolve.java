package org.elixir_lang.psi.scope.call_definition_clause;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashSet;
import org.apache.commons.lang.math.IntRange;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.psi.call.Named;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE;
import static org.elixir_lang.structure_view.element.CallDefinitionClause.nameArityRange;

public class MultiResolve extends org.elixir_lang.psi.scope.CallDefinitionClause {
    /*
     * Public Static Methods
     */

    @Nullable
    public static List<ResolveResult> resolveResultList(@NotNull String name,
                                                        int resolvedFinalArity,
                                                        boolean incompleteCode,
                                                        @NotNull PsiElement entrance) {
        return resolveResultList(name, resolvedFinalArity, incompleteCode, entrance, ResolveState.initial());
    }

    @Nullable
    public static List<ResolveResult> resolveResultList(@NotNull String name,
                                                        int resolvedFinalArity,
                                                        boolean incompleteCode,
                                                        @NotNull PsiElement entrance,
                                                        @NotNull ResolveState resolveState) {
        MultiResolve multiResolve = new MultiResolve(name, resolvedFinalArity, incompleteCode);
        PsiTreeUtil.treeWalkUp(
                multiResolve,
                entrance,
                entrance.getContainingFile(),
                resolveState.put(ENTRANCE, entrance)
        );
        return multiResolve.getResolveResultList();
    }

    /*
     * Fields
     */

    private final boolean incompleteCode;
    @NotNull
    private final String name;
    private final int resolvedFinalArity;
    @Nullable
    private Set<PsiElement> resolvedSet = null;
    @Nullable
    private List<ResolveResult> resolveResultList = null;

    /*
     * Constructors
     */

    private MultiResolve(@NotNull String name, int resolvedFinalArity, boolean incompleteCode) {
        super();
        this.incompleteCode = incompleteCode;
        this.name = name;
        this.resolvedFinalArity = resolvedFinalArity;
    }

    /*
     * Public Instance Methods
     */

    @Nullable
    public List<ResolveResult> getResolveResultList() {
        return resolveResultList;
    }

    /*
     * Private Instance Methods
     */

    /*
     * Private Instance Methods
     */

    private boolean addToResolveResultList(@NotNull Call call, boolean validResult, ResolveState state) {
        boolean keepProcessing = true;

        if (call instanceof  Named) {
            Named named = (Named) call;
            PsiElement nameIdentifier = named.getNameIdentifier();

            if (nameIdentifier != null) {
                if (PsiTreeUtil.isAncestor(state.get(ENTRANCE), nameIdentifier, false)) {
                    addNewToResolveResultList(named, validResult);

                    keepProcessing = false;
                } else {
                /* Doesn't use a Map<PsiElement, ResolveSet> so that MultiResolve's helpers that require a
                   List<ResolveResult> can still work */
                    addNewToResolveResultList(named, validResult);

                    Call importCall = state.get(IMPORT_CALL);

                    if (importCall != null) {
                        addNewToResolveResultList(importCall, validResult);
                    }
                }
            }
        }

        return keepProcessing;
    }

    @Override
    protected boolean executeOnCallDefinitionClause(@NotNull Call element, @NotNull ResolveState state) {
        boolean keepProcessing = true;
        Pair<String, IntRange> nameArityRange = nameArityRange(element);

        if (nameArityRange != null) {
            String name = nameArityRange.first;

            if (name.equals(this.name)) {
                ArityInterval arityInterval = ArityInterval.arityInterval(nameArityRange, state);

                if (arityInterval.containsInteger(resolvedFinalArity)) {
                    keepProcessing = addToResolveResultList(element, true, state);
                } else if (incompleteCode) {
                    keepProcessing = addToResolveResultList(element, false, state);
                }
            } else if (incompleteCode && name.startsWith(this.name)) {
                keepProcessing = addToResolveResultList(element, false, state);
            }

            // Don't check MultiResolve.keepProcessing in case recursive call of function with multiple clauses
        }

        return keepProcessing;
    }

    @Override
    protected boolean keepProcessing() {
        return org.elixir_lang.psi.scope.MultiResolve.keepProcessing(incompleteCode, resolveResultList);
    }

    /*
     * Private Instance Methods
     */

    private void addNewToResolveResultList(@NotNull PsiElement element, boolean validResult) {
        if (resolvedSet == null || !resolvedSet.contains(element)) {
            resolveResultList = org.elixir_lang.psi.scope.MultiResolve.addToResolveResultList(
                    resolveResultList, new PsiElementResolveResult(element, validResult)
            );

            if (resolvedSet == null) {
                resolvedSet = new THashSet<PsiElement>();
            }

            resolvedSet.add(element);
        }
    }
}

package org.elixir_lang.structure_view.element;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewTypeLocation;
import org.apache.commons.lang.math.IntRange;
import org.elixir_lang.Visibility;
import org.elixir_lang.navigation.item_presentation.NameArity;
import org.elixir_lang.navigation.item_presentation.Parent;
import org.elixir_lang.psi.*;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.psi.impl.ElixirPsiImplUtil;
import org.elixir_lang.psi.operation.Type;
import org.elixir_lang.structure_view.element.modular.Modular;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Callback extends Element<AtUnqualifiedNoParenthesesCall> implements Timed {
    /*
     * Fields
     */

    @NotNull
    private final Modular modular;

    /*
     *
     * Static Methods
     *
     */

    /*
     * Public Static Methods
     */

    public static String elementDescription(Call call, ElementDescriptionLocation location) {
        String elementDescription = null;

        if (location == UsageViewTypeLocation.INSTANCE) {
            elementDescription = "callback";
        }

        return elementDescription;
    }

    @Contract(pure = true)
    @Nullable
    public static Call headCall(AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        ElixirNoParenthesesOneArgument noParenthesesOneArgument = atUnqualifiedNoParenthesesCall.getNoParenthesesOneArgument();
        PsiElement[] grandChildren = noParenthesesOneArgument.getChildren();
        Call headCall = null;

        if (grandChildren.length == 1) {
            headCall = specificationHeadCall(grandChildren[0]);
        }

        return headCall;
    }

    @Contract(pure = true)
    public static boolean is(@NotNull final Call call) {
        boolean is = false;

        if (call instanceof AtUnqualifiedNoParenthesesCall) {
            AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall = (AtUnqualifiedNoParenthesesCall) call;
            String moduleAttributeName = ElixirPsiImplUtil.moduleAttributeName(atUnqualifiedNoParenthesesCall);

            if (moduleAttributeName.equals("@callback") || moduleAttributeName.equals("@macrocallback")) {
                is = true;
            }
        }

        return is;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement nameIdentifier(@NotNull Call call) {
        PsiElement nameIdentifier = null;

        if (call instanceof AtUnqualifiedNoParenthesesCall) {
            nameIdentifier = nameIdentifier((AtUnqualifiedNoParenthesesCall) call);
        }

        return nameIdentifier;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement nameIdentifier(@NotNull AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        Call headCall = headCall(atUnqualifiedNoParenthesesCall);
        PsiElement nameIdentifier = null;

        if (headCall != null) {
            nameIdentifier = CallDefinitionHead.nameIdentifier(headCall);
        }

        return nameIdentifier;
    }

    /*
     * Private Static Methods
     */

    @Nullable
    private static Call parameterizedTypeHeadCall(ElixirMatchedWhenOperation whenOperation) {
        PsiElement leftOperand = whenOperation.leftOperand();
        Call headCall = null;

        if (leftOperand instanceof Type) {
            headCall = typeHeadCall((Type) leftOperand);
        }

        return headCall;
    }

    @Nullable
    private static Call specificationHeadCall(PsiElement specification) {
        Call headCall = null;

        if (specification instanceof Type) {
            headCall = typeHeadCall((Type) specification);
        } else if (specification instanceof ElixirMatchedWhenOperation) {
            headCall = parameterizedTypeHeadCall((ElixirMatchedWhenOperation) specification);
        }

        return headCall;
    }

    @Nullable
    private static Call typeHeadCall(Type typeOperation) {
        PsiElement leftOperand = typeOperation.leftOperand();
        Call headCall = null;

        if (leftOperand instanceof Call) {
            headCall = (Call) leftOperand;
        }

        return headCall;
    }

    /*
     * Constructors
     */

    public Callback(@NotNull Modular modular, Call navigationItem) {
        super((AtUnqualifiedNoParenthesesCall) navigationItem);
        this.modular = modular;
    }

    /*
     *
     * Instance Methods
     *
     */

    /*
     * Public Instance Methods
     */

    /**
     * A callback's {@link #getPresentation()} is a {@link NameArity} like a {@link CallDefinition}, so like a call
     * definition, it's children are the specifications and clauses, since the callback has no clauses, the only child
     * is the specification.
     *
     * @return the list of children.
     */
    @NotNull
    @Override
    public TreeElement[] getChildren() {
        // pseudo-named-arguments
        boolean callback = true;

        //noinspection ConstantConditions
        return new TreeElement[]{
                new CallDefinitionSpecification(
                        modular,
                        navigationItem,
                        callback,
                        time()
                )
        };
    }

    /**
     * Returns the presentation of the tree element.
     *
     * @return the element presentation.
     */
    @NotNull
    @Override
    public ItemPresentation getPresentation() {
        Parent parentPresentation = (Parent) modular.getPresentation();
        String location = parentPresentation.getLocatedPresentableText();

        PsiElement[] arguments = ElixirPsiImplUtil.finalArguments(navigationItem);

        assert arguments != null;

        // pseudo-named-arguments
        boolean callback = true;
        Visibility visibility = Visibility.PUBLIC;
        boolean overridable = false;
        boolean override = false;

        String name = "?";
        int arity = -1;
        Call headCall = headCall(navigationItem);

        if (headCall != null) {
            Pair<String, IntRange> nameArityRange = CallDefinitionHead.nameArityRange(headCall);

            if (nameArityRange != null) {
                name = nameArityRange.first;
                IntRange arityRange = nameArityRange.second;

                if (arityRange != null) {
                    int maximumArity = arityRange.getMaximumInteger();

                    if (arityRange.getMinimumInteger() == maximumArity) {
                        arity = maximumArity;
                    }
                }
            }
        }

        //noinspection ConstantConditions
        return new NameArity(
                location,
                callback,
                time(),
                visibility,
                overridable,
                override,
                name,
                arity
        );
    }

    /**
     * When the defined call is usable
     *
     * @return {@link Time#COMPILE} for compile time ({@code defmacro}, {@code defmacrop});
     * {@link Time#RUN} for run time {@code def}, {@code defp})
     */
    @NotNull
    @Override
    public Time time() {
        String moduleAttributeName = ElixirPsiImplUtil.moduleAttributeName(navigationItem);
        Time time = null;

        if (moduleAttributeName.equals("@callback")) {
            time = Time.RUN;
        } else if (moduleAttributeName.equals("@macrocallback")) {
            time = Time.COMPILE;
        }

        assert time != null;

        return time;
    }
}

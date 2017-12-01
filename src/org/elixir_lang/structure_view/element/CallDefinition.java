package org.elixir_lang.structure_view.element;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Pair;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.math.IntRange;
import com.intellij.psi.PsiElement;
import org.elixir_lang.Visibility;
import org.elixir_lang.navigation.item_presentation.NameArity;
import org.elixir_lang.navigation.item_presentation.Parent;
import org.elixir_lang.psi.AtUnqualifiedNoParenthesesCall;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.structure_view.element.modular.Modular;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A definition for a call: either a function or a macro
 */
public class CallDefinition implements StructureViewTreeElement, Timed, Visible, NavigationItem {
    /*
     * Classes
     */

    /**
     * All arguments to the CallDefinition constructor
     */
    public static class Tuple {
        /*
         * Fields
         */

        public final int arity;
        public final Modular modular;
        public final String name;
        public final Time time;

        /*
         * Constructor
         */

        public Tuple(@NotNull Modular modular, @NotNull Time time, @NotNull String name, int arity) {
            this.arity = arity;
            this.modular = modular;
            this.name = name;
            this.time = time;
        }

        /*
         * Instance Methods
         */

        @Override
        public boolean equals(Object other) {
            boolean equals = false;

            if (other != null && Tuple.class.isAssignableFrom(other.getClass())) {
                final Tuple tuple = (Tuple) other;

                equals = new EqualsBuilder()
                        .append(arity, tuple.arity)
                        .append(modular, tuple.modular)
                        .append(name, tuple.name)
                        .append(time, tuple.time).isEquals();
            }

            return equals;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 31)
                    .append(arity)
                    .append(modular)
                    .append(name)
                    .append(time)
                    .toHashCode();
        }
    }

    /*
     * Fields
     */

    private final int arity;
    // keeps track of total order of all children (clauses, heads, and specifications)
    @NotNull
    private final List<TreeElement> childList = new ArrayList<TreeElement>();
    @NotNull
    private final List<CallDefinitionClause> clauseList = new ArrayList<CallDefinitionClause>();
    @NotNull
    private final List<CallDefinitionHead> headList = new ArrayList<CallDefinitionHead>();
    @NotNull
    private final Modular modular;
    @NotNull
    private final String name;
    // is allowed to be overridden by an override function
    private boolean overridable;
    // overrides an overridable function
    private boolean override;
    private List<CallDefinitionSpecification> specificationList = new ArrayList<CallDefinitionSpecification>();
    @NotNull
    private final Time time;

    /*
     * Constructors
     */

    /**
     * @param call a def(macro)?p? call
     */
    @Nullable
    public static CallDefinition fromCall(@NotNull Call call) {
        Modular modular = CallDefinitionClause.enclosingModular(call);

        CallDefinition callDefinition = null;

        if (modular != null) {
            Pair<String, IntRange> nameArityRange = CallDefinitionClause.nameArityRange(call);

            if (nameArityRange != null) {

                String name = nameArityRange.first;
                /* arity is assumed to be max arity in the range because that's how {@code h} and ExDoc treat functions
                   with defaults. */
                int arity = nameArityRange.second.getMaximumInteger();

                Time time = CallDefinitionClause.time(call);
                callDefinition = new CallDefinition(modular, time, name, arity);
            }
        }

        return callDefinition;
    }

    public CallDefinition(@NotNull Modular modular, @NotNull Time time, @NotNull String name, int arity) {
        this.arity = arity;
        this.modular = modular;
        this.name = name;
        this.time = time;
    }

    /*
     * Public Instance Methods
     */

    public int arity() {
        return arity;
    }

    /**
     * Adds clause to macro
     *
     * @param clause the new clause for the macro
     */
    public CallDefinitionClause clause(Call clause) {
        Pair<String, IntRange> nameArityRange = CallDefinitionClause.nameArityRange(clause);

        assert nameArityRange != null;
        assert nameArityRange.first.equals(name);
        assert nameArityRange.second.getMinimumInteger() <= arity && arity <= nameArityRange.second.getMaximumInteger();

        CallDefinitionClause callDefinitionClause = new CallDefinitionClause(this, clause);
        childList.add(callDefinitionClause);
        clauseList.add(callDefinitionClause);

        return callDefinitionClause;
    }

    public List<CallDefinitionClause> clauseList() {
        return clauseList;
    }

    /**
     * Returns the clauses of the macro
     *
     * @return the list of {@link CallDefinitionClause} elements.
     */
    @NotNull
    @Override
    public TreeElement[] getChildren() {
        return childList.toArray(new TreeElement[childList.size()]);
    }

    /**
     * The scoping module or quote
     *
     * @return The scoping module or quote
     */
    @NotNull
    public Modular getModular() {
        return modular;
    }

    /**
     * Returns the data object (usually a PSI element) corresponding to the
     * structure view element.
     *
     * @return the data object instance.
     */
    @Nullable
    @Override
    public Object getValue() {
        Object value = null;

        if (headList.size() > 0) {
            value = headList.get(0);
        } else if (clauseList.size() > 0) {
            value = clauseList.get(0);
        }

        return value;
    }

    /**
     * A macro groups together one or more {@link CallDefinitionClause} elements, so it can navigate if it has clauses.
     *
     * @return {@code true} if {@link #clauseList} size is greater than 0; otherwise, {@code false}.
     */
    @Override
    public boolean canNavigate() {
        return clauseList.size() > 0;
    }

    /**
     * A macro groups together one or more {@link CallDefinitionClause} elements, so it can navigate if it has clauses.
     *
     * @return {@code true} if {@link #clauseList} size is greater than 0; otherwise, {@code false}.
     */
    @Override
    public boolean canNavigateToSource() {
        return clauseList.size() > 0;
    }

    @NotNull
    @Override
    public String getName() {
        return name + "/" + arity;
    }

    /**
     * Returns the presentation of the tree element.
     *
     * @return the element presentation.
     */
    @NotNull
    @Override
    public ItemPresentation getPresentation() {
        ItemPresentation itemPresentation = modular.getPresentation();
        String location = null;

        if (itemPresentation instanceof Parent) {
            Parent parentPresentation = (Parent) itemPresentation;
            location = parentPresentation.getLocatedPresentableText();
        }

        // pseudo-named-arguments
        boolean callback = false;

        //noinspection ConstantConditions
        return new NameArity(
                location,
                callback,
                time,
                visibility(),
                overridable,
                override,
                name,
                arity
        );
    }

    /**
     * Unlike a clause, a head is just the name and arguments, without the outer macro calls.  Heads occur in
     * {@code Kernel.defdelegate/2}.
     *
     * @param call
     */
    public void head(Call head) {
        Pair<String, IntRange> nameArityRange = CallDefinitionHead.nameArityRange(head);

        assert nameArityRange != null;
        assert nameArityRange.first.equals(name);
        assert nameArityRange.second.getMinimumInteger() <= arity && arity <= nameArityRange.second.getMaximumInteger();

        CallDefinitionHead callDefinitionHead = new CallDefinitionHead(this, Visibility.PUBLIC, head);
        childList.add(callDefinitionHead);
        headList.add(callDefinitionHead);
    }

    /**
     * @return {@code true} if this function can be overridden when the outer quote is {@code use}d
     */
    public boolean isOverridable() {
        return overridable;
    }

    /**
     * The clause that matches the {@code arguments}.
     *
     * @param arguments the arguments the clause's arguments must match
     * @return {@code null} if no clauses match or if more than one clause match
     */
    @Nullable
    public CallDefinitionClause matchingClause(PsiElement[] arguments) {
        CallDefinitionClause clause = null;
        List<CallDefinitionClause> clauseList = matchingClauseList(arguments);

        if (clauseList != null && clauseList.size() == 1) {
            clause = clauseList.get(0);
        }

        return clause;
    }

    /**
     * All clauses that match the {@code arguments}.
     *
     * @param arguments the arguments the clauses' arguments must match
     * @return {@code null} if no clauses match; multiple clauses if the types of arguments cannot be inferred and
     * simpler, relaxed matching has to be used.
     */
    @Nullable
    public List<CallDefinitionClause> matchingClauseList(PsiElement[] arguments) {
        List<CallDefinitionClause> clauseList = null;

        for (CallDefinitionClause clause : this.clauseList) {
            if (clause.isMatch(arguments)) {
                if (clauseList == null) {
                    clauseList = new ArrayList<CallDefinitionClause>(1);
                }

                clauseList.add(clause);
            }
        }

        return clauseList;
    }

    @NotNull
    public String name() {
        return name;
    }

    /**
     * Navigates to first clause in {@link #clauseList}.
     *
     * @param requestFocus <code>true</code> if focus requesting is necessary
     */
    @Override
    public void navigate(@SuppressWarnings("unused") boolean requestFocus) {
        if (canNavigate()) {
            clauseList.get(0).navigate(requestFocus);
        }
    }

    /**
     * When the defined call is usable
     *
     * @return {@link Timed.Time#COMPILE} for compile time ({@code defmacro}, {@code defmacrop});
     * {@link Timed.Time#RUN} for run time {@code def}, {@code defp})
     */
    @Override
    @NotNull
    public Time time() {
        return time;
    }

    /**
     * Set that this function overrides an overridable function
     *
     * @param override {@code true} to mark as an override of another function; {@code false} to mark as an independent
     *                 function
     */
    public void setOverride(boolean override) {
        this.override = override;
    }

    /**
     * Set that this function can be overridden by another function of the same name and arity.
     *
     * @param overridable {@code true} to mark as overridable by another function of the same name and arity;
     *                    {@code false} to make as non-overridable.
     */
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    /**
     * @param moduleAttributeDefinition
     */
    public void specification(AtUnqualifiedNoParenthesesCall moduleAttributeDefinition) {
        Pair<String, Integer> nameArity = CallDefinitionSpecification.moduleAttributeNameArity(
                moduleAttributeDefinition
        );

        assert nameArity != null;
        assert nameArity.first.equals(name);
        assert nameArity.second == arity;

        // pseudo-named-arguments
        boolean callback = false;
        Timed.Time time = Time.RUN;

        //noinspection ConstantConditions
        CallDefinitionSpecification callDefinitionSpecification = new CallDefinitionSpecification(
                modular,
                moduleAttributeDefinition,
                callback,
                time
        );
        childList.add(callDefinitionSpecification);
        specificationList.add(callDefinitionSpecification);
    }

    /**
     * The visibility of the element.
     *
     * @return {@link Visibility#PUBLIC} for public call definitions ({@code def} and {@code defmacro});
     * {@link Visibility#PRIVATE} for private call definitions ({@code defp} and {@code defmacrop}); {@code null} for
     * a mix of visibilities, such as when a call definition has a mix of call definition clause visibilities, which
     * is invalid code, but can occur temporarily while code is being edited.
     */
    @Nullable
    @Override
    public Visibility visibility() {

        int privateCount = 0;
        int publicCount = 0;

        for (CallDefinitionHead callDefinitionHead : headList) {
            switch (callDefinitionHead.visibility()) {
                case PRIVATE:
                    privateCount++;
                    break;
                case PUBLIC:
                    publicCount++;
                    break;
            }
        }

        for (CallDefinitionClause callDefinitionClause : clauseList) {
            switch (callDefinitionClause.visibility()) {
                case PRIVATE:
                    privateCount++;
                    break;
                case PUBLIC:
                    publicCount++;
                    break;
            }
        }

        Visibility callDefinitionVisibility;

        if (privateCount > 0 && publicCount == 0) {
            callDefinitionVisibility = Visibility.PRIVATE;
        } else if (privateCount == 0 && publicCount > 0) {
            callDefinitionVisibility = Visibility.PUBLIC;
        } else {
            callDefinitionVisibility = null;
        }

        return callDefinitionVisibility;
    }

}

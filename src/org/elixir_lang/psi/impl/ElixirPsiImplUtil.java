package org.elixir_lang.psi.impl;

import com.ericsson.otp.erlang.*;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.SmartHashSet;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.math.IntRange;
import org.elixir_lang.ElixirLanguage;
import org.elixir_lang.Level;
import org.elixir_lang.Macro;
import org.elixir_lang.annotator.Parameter;
import org.elixir_lang.psi.*;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.psi.call.MaybeExported;
import org.elixir_lang.psi.call.StubBased;
import org.elixir_lang.psi.call.arguments.None;
import org.elixir_lang.psi.call.arguments.star.NoParentheses;
import org.elixir_lang.psi.call.arguments.star.NoParenthesesOneArgument;
import org.elixir_lang.psi.call.arguments.star.Parentheses;
import org.elixir_lang.psi.call.name.Function;
import org.elixir_lang.psi.operation.*;
import org.elixir_lang.psi.operation.infix.Position;
import org.elixir_lang.psi.operation.infix.Triple;
import org.elixir_lang.psi.qualification.Qualified;
import org.elixir_lang.psi.qualification.Unqualified;
import org.elixir_lang.psi.stub.call.Stub;
import org.elixir_lang.reference.Callable;
import org.elixir_lang.sdk.elixir.Release;
import org.elixir_lang.structure_view.element.CallDefinitionClause;
import org.elixir_lang.structure_view.element.CallDefinitionSpecification;
import org.elixir_lang.structure_view.element.Callback;
import org.elixir_lang.structure_view.element.Delegation;
import org.elixir_lang.structure_view.element.modular.Implementation;
import org.elixir_lang.structure_view.element.modular.Module;
import org.elixir_lang.structure_view.element.modular.Protocol;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.elixir_lang.Level.V_1_3;
import static org.elixir_lang.errorreport.Logger.error;
import static org.elixir_lang.mix.importWizard.ImportedOtpAppKt.computeReadAction;
import static org.elixir_lang.psi.call.name.Function.*;
import static org.elixir_lang.psi.call.name.Module.*;
import static org.elixir_lang.psi.impl.ParentImpl.addChildTextCodePoints;
import static org.elixir_lang.psi.impl.ParentImpl.elixirCharList;
import static org.elixir_lang.psi.impl.ParentImpl.elixirString;
import static org.elixir_lang.psi.stub.type.call.Stub.isModular;
import static org.elixir_lang.reference.Callable.*;
import static org.elixir_lang.reference.ModuleAttribute.isNonReferencing;
import static org.elixir_lang.sdk.elixir.Type.getNonNullRelease;
import static org.elixir_lang.structure_view.element.CallDefinitionClause.enclosingModularMacroCall;
import static org.elixir_lang.structure_view.element.modular.Implementation.forNameCollection;

/**
 * Created by luke.imhoff on 12/29/14.
 */
public class ElixirPsiImplUtil {

    public static final Class[] UNQUOTED_TYPES = new Class[]{
            ElixirEndOfExpression.class,
            PsiComment.class,
            PsiWhiteSpace.class
    };
    public static final OtpErlangObject ALIASES = new OtpErlangAtom("__aliases__");
    public static final OtpErlangAtom AMBIGUOUS_OP = new OtpErlangAtom("ambiguous_op");
    // Must be before AMBIGUOUS_OP_KEYWORD_PAIR where NIL is used
    public static final OtpErlangAtom NIL = new OtpErlangAtom("nil");
    public static final OtpErlangTuple AMBIGUOUS_OP_KEYWORD_PAIR = new OtpErlangTuple(
            new OtpErlangObject[]{
                    AMBIGUOUS_OP,
                    NIL
            }
    );
    public static final OtpErlangAtom BLOCK = new OtpErlangAtom("__block__");
    public static final Key<Boolean> DECLARING_SCOPE = new Key<Boolean>("DECLARING_SCOPE");
    public static final String DEFAULT_OPERATOR = "\\\\";
    public static final OtpErlangAtom DO = new OtpErlangAtom("do");
    public static final Key<PsiElement> ENTRANCE = new Key<PsiElement>("ENTRANCE");
    public static final OtpErlangAtom EXCLAMATION_POINT = new OtpErlangAtom("!");
    public static final OtpErlangAtom FALSE = new OtpErlangAtom("false");
    public static final OtpErlangAtom FN = new OtpErlangAtom("fn");
    public static final OtpErlangAtom MINUS = new OtpErlangAtom("-");
    public static final OtpErlangAtom MULTIPLE_ALIASES = new OtpErlangAtom("{}");
    public static final OtpErlangAtom NOT = new OtpErlangAtom("not");
    public static final OtpErlangAtom PLUS = new OtpErlangAtom("+");
    public static final OtpErlangAtom TRUE = new OtpErlangAtom("true");
    public static final TokenSet ADDITION_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.DUAL_OPERATOR);
    public static final TokenSet AND_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.AND_SYMBOL_OPERATOR, ElixirTypes.AND_WORD_OPERATOR);
    public static final TokenSet AT_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.AT_OPERATOR);
    public static final TokenSet CAPTURE_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.CAPTURE_OPERATOR);
    public static final TokenSet COMPARISON_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.COMPARISON_OPERATOR);
    public static final TokenSet DOT_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.DOT_OPERATOR);
    public static final TokenSet IN_MATCH_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.IN_MATCH_OPERATOR);
    public static final TokenSet IN_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.IN_OPERATOR);
    public static final TokenSet MATCH_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.MATCH_OPERATOR);
    public static final TokenSet NOT_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.NOT_OPERATOR);
    public static final TokenSet MULTIPLICATIVE_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.DIVISION_OPERATOR, ElixirTypes.MULTIPLICATION_OPERATOR);
    public static final TokenSet OR_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.OR_SYMBOL_OPERATOR, ElixirTypes.OR_WORD_OPERATOR);
    public static final TokenSet PIPE_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.PIPE_OPERATOR);
    public static final TokenSet RELATIONAL_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.RELATIONAL_OPERATOR);
    public static final TokenSet STAB_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.STAB_OPERATOR);
    public static final TokenSet STRUCT_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.STRUCT_OPERATOR);
    public static final TokenSet THREE_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.THREE_OPERATOR);
    public static final TokenSet TWO_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.RANGE_OPERATOR, ElixirTypes.TWO_OPERATOR);
    public static final TokenSet TYPE_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.TYPE_OPERATOR);
    public static final TokenSet UNARY_OPERATOR_TOKEN_SET = TokenSet.create(
            ElixirTypes.DUAL_OPERATOR, ElixirTypes.NOT_OPERATOR, ElixirTypes.SIGN_OPERATOR, ElixirTypes.UNARY_OPERATOR
    );
    private static final OtpErlangAtom WHEN = new OtpErlangAtom("when");
    public static final OtpErlangAtom UNQUOTE_SPLICING = new OtpErlangAtom("unquote_splicing");
    public static final OtpErlangAtom[] ATOM_KEYWORDS = new OtpErlangAtom[]{
            FALSE,
            TRUE,
            NIL
    };
    public static final OtpErlangAtom[] REARRANGED_UNARY_OPERATORS = new OtpErlangAtom[]{
            EXCLAMATION_POINT,
            NOT
    };
    public static final OtpErlangAtom UTF_8 = new OtpErlangAtom("utf8");
    public static final int BINARY_BASE = 2;
    public static final int DECIMAL_BASE = 10;
    public static final int HEXADECIMAL_BASE = 16;
    public static final int OCTAL_BASE = 8;
    // NOTE: Unknown is all bases not 2, 8, 10, or 16, but 36 is used because all digits and letters are parsed.
    public static final int UNKNOWN_BASE = 36;
    public static final TokenSet IDENTIFIER_TOKEN_SET = TokenSet.create(ElixirTypes.IDENTIFIER_TOKEN);
    public static final com.intellij.util.Function<PsiElement, PsiElement> NEXT_SIBLING = new com.intellij.util.Function<PsiElement, PsiElement>() {
                @Override
                public PsiElement fun(PsiElement element) {
                    return element.getNextSibling();
                }
            };
    public static final com.intellij.util.Function<PsiElement, PsiElement> PREVIOUS_SIBLING = new com.intellij.util.Function<PsiElement, PsiElement>() {
                @Override
                public PsiElement fun(PsiElement element) {
                    return element.getPrevSibling();
                }
            };
    public static final TokenSet ARROW_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.ARROW_OPERATOR);
    public static final TokenSet WHEN_OPERATOR_TOKEN_SET = TokenSet.create(ElixirTypes.WHEN_OPERATOR);

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] arguments(@NotNull final ElixirMapConstructionArguments mapConstructionArguments) {
        return mapConstructionArguments.getChildren();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] arguments(@NotNull final ElixirNoParenthesesOneArgument noParenthesesOneArgument) {
        PsiElement[] children = noParenthesesOneArgument.getChildren();
        PsiElement[] arguments = children;

        if (children.length == 1) {
            PsiElement child = children[0];

            if (child instanceof Arguments) {
                Arguments childArguments = (Arguments) child;
                arguments = childArguments.arguments();
            }
        }

        return arguments;
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] arguments(@NotNull final ElixirNoParenthesesStrict noParenthesesStrict) {
        return noParenthesesStrict.getChildren();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] arguments(@NotNull final ElixirParenthesesArguments parenthesesArguments) {
        return parenthesesArguments.getChildren();
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@SuppressWarnings("unused") @NotNull final ElixirBinaryDigits binaryDigits) {
        return BINARY_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@SuppressWarnings("unused") @NotNull final ElixirBinaryWholeNumber binaryWholeNumber) {
        return BINARY_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirDecimalDigits decimalDigits) {
        return DECIMAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirDecimalWholeNumber decimalWholeNumber) {
        return DECIMAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirHexadecimalDigits hexadecimalDigits) {
        return HEXADECIMAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirHexadecimalWholeNumber hexadecimalWholeNumber) {
        return HEXADECIMAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirOctalDigits octalDigits) {
        return OCTAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirOctalWholeNumber octalWholeNumber) {
        return OCTAL_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirUnknownBaseDigits unknownBaseDigits) {
        return UNKNOWN_BASE;
    }

    @Contract(pure = true)
    @NotNull
    public static int base(@NotNull @SuppressWarnings("unused") final ElixirUnknownBaseWholeNumber unknownBaseWholeNumber) {
        return UNKNOWN_BASE;
    }

    @NotNull
    public static String javaString(@NotNull OtpErlangBinary elixirString) {
        byte[] bytes = elixirString.binaryValue();
        return new String(bytes, Charset.forName("UTF-8"));
    }

    /**
     * Converts group of separated expressions into a block or returns the single expression.
     *
     * See https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L724-L725
     */
    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject toBlock(@NotNull final Deque<OtpErlangObject> quotedChildren) {
        OtpErlangObject asBlock;
        final int size = quotedChildren.size();

        if (size == 0) {
            asBlock = NIL;
        } else if (size == 1) {
            asBlock = quotedChildren.getFirst();
        } else {
            asBlock = blockFunctionCall(quotedChildren);
        }

        return asBlock;
    }

    /**
     * Builds a block for stab bodies.  Unlike `toBlock`, handles rearranging unary operations `not` and `!` and putting
     * solitary `unquote_splicing` calls in blocks.
     *
     * @param quotedChildren
     * @return
     */
    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject buildBlock(@NotNull final Deque<OtpErlangObject> quotedChildren) {
        final int size = quotedChildren.size();
        OtpErlangObject builtBlock;

        if (size == 0) {
            builtBlock = NIL;
        } else if (size == 1) {
            OtpErlangObject quotedChild = quotedChildren.getFirst();
            builtBlock = quotedChild;

            // @see https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L588
            if (Macro.INSTANCE.isLocalCall(quotedChild)) {
                OtpErlangTuple childTuple = (OtpErlangTuple) quotedChild;
                OtpErlangObject quotedIdentifier = childTuple.elementAt(0);

                // @see https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L547
                if (quotedIdentifier.equals(NOT) || quotedIdentifier.equals(EXCLAMATION_POINT) || quotedIdentifier.equals(UNQUOTE_SPLICING)) {
                    builtBlock = blockFunctionCall(quotedChildren);
                }
            }
        } else {
           builtBlock = blockFunctionCall(quotedChildren);
        }

        return builtBlock;
    }

    @Contract(pure = true)
    @NotNull
    private static OtpErlangTuple blockFunctionCall(@NotNull final Deque<OtpErlangObject> quotedChildren) {
        OtpErlangObject[] quotedArray = new OtpErlangObject[quotedChildren.size()];
        OtpErlangList blockMetadata = new OtpErlangList();
        quotedArray = quotedChildren.toArray(quotedArray);

        return quotedFunctionCall(
                BLOCK,
                blockMetadata,
                quotedArray
        );
    }

    @Nullable
    public static String canonicalName(@NotNull StubBased stubBased) {
        String canonicalName;

        if (isModular(stubBased)) {
            String canonicalNameSuffix = null;

            if (Implementation.is(stubBased)) {
                String protocolName = Implementation.protocolName(stubBased);
                PsiElement forNameElement = Implementation.forNameElement(stubBased);
                String forName = null;

                if (forNameElement != null) {
                    forName = forNameElement.getText();
                }

                canonicalNameSuffix = StringUtil.notNullize(protocolName, "?") +
                        "." +
                        StringUtil.notNullize(forName, "?");
            } else if (Module.is(stubBased) || Protocol.is(stubBased)) {
                canonicalNameSuffix = org.elixir_lang.navigation.item_presentation.modular.Module.presentableText(
                        stubBased
                );
            }

            Call enclosing = enclosingModularMacroCall(stubBased);

            if (enclosing instanceof StubBased) {
                StubBased enclosingStubBased = (StubBased) enclosing;
                String canonicalNamePrefix = enclosingStubBased.canonicalName();

                canonicalName = StringUtil.notNullize(canonicalNamePrefix, "?") +
                        "." +
                        StringUtil.notNullize(canonicalNameSuffix, "?");
            } else {
                canonicalName = StringUtil.notNullize(canonicalNameSuffix, "?");
            }
        } else {
            canonicalName = stubBased.getName();
        }

        return canonicalName;
    }

    @NotNull
    public static Set<String> canonicalNameSet(@NotNull StubBased stubBased) {
        Set<String> canonicalNameSet;

        if (isModular(stubBased)) {
            Set<String> canonicalNameSuffixSet;

            if (Implementation.is(stubBased)) {
                String maybeProtocolName = Implementation.protocolName(stubBased);
                PsiElement forNameElement = Implementation.forNameElement(stubBased);
                Collection<String> maybeForNameCollection = null;

                if (forNameElement != null) {
                    maybeForNameCollection = forNameCollection(forNameElement);
                }

                String protocolName = StringUtil.notNullize(maybeProtocolName, "?");

                if (maybeForNameCollection == null) {
                    canonicalNameSuffixSet = Collections.singleton(protocolName + ".?");
                } else {
                    canonicalNameSuffixSet = new SmartHashSet<String>(maybeForNameCollection.size());

                    for (@Nullable String maybeForName : maybeForNameCollection) {
                        String canonicalName = protocolName + "." + StringUtil.notNullize(maybeForName, "?");
                        canonicalNameSuffixSet.add(canonicalName);
                    }
                }
            } else if (Module.is(stubBased) || Protocol.is(stubBased)) {
                canonicalNameSuffixSet = Collections.singleton(
                        org.elixir_lang.navigation.item_presentation.modular.Module.presentableText(stubBased)
                );
            } else {
                canonicalNameSuffixSet = Collections.singleton("?");
            }

            Call enclosing = enclosingModularMacroCall(stubBased);

            if (enclosing instanceof StubBased) {
                StubBased enclosingStubBased = (StubBased) enclosing;
                Set<String> canonicalNamePrefixSet = enclosingStubBased.canonicalNameSet();

                if (canonicalNamePrefixSet == null) {
                    canonicalNamePrefixSet = Collections.singleton("?");
                }

                canonicalNameSet = new SmartHashSet<String>(
                        canonicalNamePrefixSet.size() * canonicalNameSuffixSet.size()
                );

                for (String canonicalNamePrefix : canonicalNamePrefixSet) {
                    for (String canonicalNameSuffix : canonicalNameSuffixSet) {
                        canonicalNameSet.add(
                                canonicalNamePrefix + "." + canonicalNameSuffix
                        );
                    }
                }
            } else {
                canonicalNameSet = canonicalNameSuffixSet;
            }
        } else {
            String canonicalName = stubBased.getName();

            if (canonicalName != null) {
                canonicalNameSet = Collections.singleton(canonicalName);
            } else {
                canonicalNameSet = Collections.emptySet();
            }
        }

        return canonicalNameSet;
    }

    // @return -1 if codePoint cannot be parsed.
    public static int codePoint(@NotNull ElixirEscapedCharacter escapedCharacter) {
        return EscapeSequenceImpl.codePoint(escapedCharacter);
    }

    @Contract(pure = true)
    public static int codePoint(ElixirEscapedEOL escapedEOL) {
        return EscapeSequenceImpl.codePoint(escapedEOL);
    }

    // @return -1 if codePoint cannot be parsed.
    public static int codePoint(@NotNull EscapedHexadecimalDigits hexadecimalEscapeSequence) {
        return EscapeSequenceImpl.codePoint(hexadecimalEscapeSequence);
    }

    // @return -1 if codePoint cannot be parsed.
    public static int codePoint(@NotNull ElixirQuoteHexadecimalEscapeSequence quoteHexadecimalEscapeSequence) {
        return EscapeSequenceImpl.codePoint(quoteHexadecimalEscapeSequence);
    }

    public static int codePoint(@NotNull ElixirSigilHexadecimalEscapeSequence sigilHexadecimalEscapeSequence) {
        return EscapeSequenceImpl.codePoint(sigilHexadecimalEscapeSequence);
    }

    public enum UseScopeSelector {
        PARENT,
        SELF,
        SELF_AND_FOLLOWING_SIBLINGS,
    }

    public static UseScopeSelector useScopeSelector(@NotNull PsiElement element) {
        UseScopeSelector useScopeSelector = UseScopeSelector.PARENT;

        if (element instanceof AtUnqualifiedNoParenthesesCall) {
            /* Module Attribute declarations can't declare variables, so this is a variable usage without declaration,
               so limit to SELF */
            useScopeSelector = UseScopeSelector.SELF;
        } else if (element instanceof ElixirAnonymousFunction) {
            useScopeSelector = UseScopeSelector.SELF;
        } else if (element instanceof Call) {
            Call call = (Call) element;

            if (call.isCalling(KERNEL, CASE) ||
                    call.isCalling(KERNEL, COND) ||
                    call.isCalling(KERNEL, IF) ||
                    call.isCalling(KERNEL, RECEIVE) ||
                    call.isCalling(KERNEL, UNLESS) ||
                    call.isCalling(KERNEL, VAR_BANG)
                    ) {
               useScopeSelector = UseScopeSelector.SELF_AND_FOLLOWING_SIBLINGS;
            } else if (CallDefinitionClause.is(call) || isModular(call) || hasDoBlockOrKeyword(call)) {
                useScopeSelector = UseScopeSelector.SELF;
            }
        }

        return useScopeSelector;
    }

    /**
     * @see <a href="https://elixir-lang.readthedocs.io/en/latest/technical/scoping.html">Elixir Scoping</a>
     * @see <a href="https://github.com/alco/elixir/wiki/Scoping-Rules-in-Elixir-(and-Erlang)#scopes-that-change-the-existing-binding">Scoping Rules in Elixir (and Erlang)</a>
     */
    public static boolean createsNewScope(@NotNull PsiElement element) {
        return useScopeSelector(element) == UseScopeSelector.SELF;
    }

    /**
     * The number of arguments that have defaults.
     * @param arguments arguments to a definition call
     * @return
     */
    public static int defaultArgumentCount(@NotNull PsiElement[] arguments) {
        int count = 0;

        for (PsiElement argument : arguments) {
            if (isDefaultArgument(argument)) {
                count++;
            }
        }

        return count;
    }

    @Nullable
    public static String definedModuleName(@NotNull final ElixirUnmatchedUnqualifiedNoParenthesesCall unmatchedUnqualifiedNoParenthesesCall) {
        PsiElement[] arguments = unmatchedUnqualifiedNoParenthesesCall.primaryArguments();
        String definedModuleName = null;

        if (arguments.length > 1) {
            PsiElement argument = arguments[0];


        }

        return definedModuleName;
    }

    @NotNull
    public static List<Digits> digitsList(@NotNull ElixirBinaryWholeNumber binaryWholeNumber) {
        List<Digits> digitsList = new LinkedList<Digits>();

        digitsList.addAll(binaryWholeNumber.getBinaryDigitsList());

        return digitsList;
    }

    @NotNull
    public static List<Digits> digitsList(@NotNull ElixirDecimalWholeNumber decimalWholeNumber) {
        List<Digits> digitsList = new LinkedList<Digits>();

        digitsList.addAll(decimalWholeNumber.getDecimalDigitsList());

        return digitsList;
    }

    @NotNull static List<Digits> digitsList(@NotNull ElixirHexadecimalWholeNumber hexadecimalWholeNumber) {
        List<Digits> digitsList = new LinkedList<Digits>();

        digitsList.addAll(hexadecimalWholeNumber.getHexadecimalDigitsList());

        return digitsList;
    }

    @NotNull
    public static List<Digits> digitsList(@NotNull ElixirOctalWholeNumber octalWholeNumber) {
        List<Digits> digitsList = new LinkedList<Digits>();

        digitsList.addAll(octalWholeNumber.getOctalDigitsList());

        return digitsList;
    }

    @NotNull
    public static List<Digits> digitsList(@NotNull ElixirUnknownBaseWholeNumber unknownBaseWholeNumber) {
        List<Digits> digitsList = new LinkedList<Digits>();

        digitsList.addAll(unknownBaseWholeNumber.getUnknownBaseDigitsList());

        return digitsList;
    }

    public static Document document(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        FileViewProvider fileViewProvider = containingFile.getViewProvider();

        return fileViewProvider.getDocument();
    }

    @NotNull
    public static String identifierName(ElixirAtIdentifier atIdentifier) {
        ASTNode node = atIdentifier.getNode();
        ASTNode[] identifierNodes = node.getChildren(ElixirPsiImplUtil.IDENTIFIER_TOKEN_SET);

        assert identifierNodes.length == 1;

        ASTNode identifierNode = identifierNodes[0];
        return identifierNode.getText();
    }

    /**
     * The keyword arguments for {@code call}.
     * @param call call to search for keyword arguments.
     * @return the final element of the {@link ElixirPsiImplUtil#finalArguments(Call)} of {@code} if they are a
     *   {@link QuotableKeywordList}; otherwise, {@code null}.
     */
    @Nullable
    public static QuotableKeywordList keywordArguments(@NotNull final Call call) {
        PsiElement[] finalArguments = finalArguments(call);
        QuotableKeywordList keywordArguments = null;

        if (finalArguments != null) {
            int finalArgumentCount = finalArguments.length;

            if (finalArgumentCount > 0) {
                PsiElement potentialKeywords = finalArguments[finalArgumentCount - 1];

                if (potentialKeywords instanceof QuotableKeywordList) {
                    keywordArguments = (QuotableKeywordList) potentialKeywords;
                } else if (potentialKeywords instanceof ElixirAccessExpression) {
                    PsiElement accessExpressionChild = stripAccessExpression(potentialKeywords);

                    if (accessExpressionChild instanceof ElixirList) {
                        ElixirList list = (ElixirList) accessExpressionChild;
                        PsiElement[] listChildren = list.getChildren();

                        if (listChildren.length == 1) {
                            PsiElement listChild = listChildren[0];

                            if (listChild instanceof QuotableKeywordList) {
                                keywordArguments = (QuotableKeywordList) listChild;
                            }
                        }
                    }
                }
            }
        }

        return keywordArguments;
    }

    /**
     * The value of the keyword argument with the given keywordKeyText.
     *
     * @param call call to seach for the keyword argument.
     * @param keywordKeyText the text of the key, such as {@code "do"}
     * @return the keyword value {@code PsiElement} if {@code call} has {@link ElixirPsiImplUtil#keywordArguments(Call)}
     *   and there is a {@link ElixirPsiImplUtil.keywordValue(QuotableKeywordList)} for {@code keywordKeyText}.
     */
    @Nullable
    public static PsiElement keywordArgument(@NotNull final Call call, String keywordKeyText) {
        QuotableKeywordList keywordArguments = keywordArguments(call);
        PsiElement keywordValue = null;

        if (keywordArguments != null) {
            keywordValue = keywordValue(keywordArguments, keywordKeyText);
        }

        return keywordValue;
    }

    public static OtpErlangTuple keywordTuple(String key, int value) {
        final OtpErlangAtom keyAtom = new OtpErlangAtom(key);
        final OtpErlangInt valueInt = new OtpErlangInt(value);
        final OtpErlangObject[] elements = {
                keyAtom,
                valueInt
        };

        return new OtpErlangTuple(elements);
    }

    /**
     * The value associated with the keyword value.
     *
     * @param keywordList The keyword list to search for {@code keywordKeyText}.
     * @param keywordKeyText the text of the keyword value.
     * @return the {@code PsiElement} associated with {@code keywordKeyText}.
     */
    @Nullable
    public static Quotable keywordValue(QuotableKeywordList keywordList, String keywordKeyText) {
        Quotable keywordValue = null;

        for (QuotableKeywordPair quotableKeywordPair : keywordList.quotableKeywordPairList()) {
            if (hasKeywordKey(quotableKeywordPair, keywordKeyText)) {
                keywordValue = quotableKeywordPair.getKeywordValue();
            }
        }

        return keywordValue;
    }

    public static boolean inBase(@NotNull final Digits digits) {
        ASTNode child = digits.getNode().getFirstChildNode();
        boolean inBase = false;

        if (child.getElementType() == digits.validElementType()) {
            inBase = true;
        }

        return inBase;
    }

    public static boolean inBase(@NotNull final List<Digits> digitsList) {
        int validDigitsCount = 0;
        int invalidDigitsCount = 0;

        for (Digits digits : digitsList) {
            if (digits.inBase()) {
                validDigitsCount++;
            } else {
                invalidDigitsCount++;
            }
        }

        boolean valid = false;

        if (invalidDigitsCount < 1 && validDigitsCount > 0) {
            valid = true;
        }

        return valid;
    }

    /**
     * Whether the {@code call} is calling the given `functionName` in the `resolvedModuleName` with any arity
     * @param call the call element
     * @param resolvedModuleName the expected {@link Call#resolvedModuleName()}
     * @param functionName the expected {@link Call#functionName()}
     * @return {@code true} if the {@code call} has non-{@code null} {@link Call#resolvedModuleName()} that equals
     *   {@code resolvedModuleName} and has non-{@code null} {@link Call#functionName()} that equals
     *   {@code functionName}; otherwise, {@code false}.
     */
    public static boolean isCalling(@NotNull final Call call,
                                    @NotNull final String resolvedModuleName,
                                    @NotNull final String functionName) {
        String callResolvedModuleName = call.resolvedModuleName();
        String callFunctionName = call.functionName();

        return callResolvedModuleName != null && callResolvedModuleName.equals(resolvedModuleName) &&
                callFunctionName != null && callFunctionName.equals(functionName);
    }

    /**
     * Whether the {@code call} is calling the given `functionName` in the `resolvedModuleName` with the
     * `resolvedFinalArity`
     *
     * @param call the call element
     * @param resolvedModuleName  the expected {@link Call#resolvedModuleName()}
     * @param functionName the expected {@link Call#functionName()}
     * @param resolvedFinalArity the expected {@link Call#resolvedFinalArity()}
     * @return {@code true} if the {@code call} has non-{@code null} {@link Call#resolvedModuleName()} that equals
     *   {@code resolvedModuleName} and has non-{@code null} {@link Call#functionName()} that equals
     *   {@code functionName} and the {@link Call#resolvedFinalArity()}; otherwise, {@code false}.
     */
    public static boolean isCalling(@NotNull final Call call,
                                    @NotNull final String resolvedModuleName,
                                    @NotNull final String functionName,
                                    final int resolvedFinalArity) {
        return call.isCalling(resolvedModuleName, functionName) &&
                call.resolvedFinalArity() == resolvedFinalArity;
    }

    /**
     * Whether {@code call} is of the named macro.
     *
     * Differs from {@link ElixirPsiImplUtil#isCallingMacro(Call, String, String, int)} because no arity is necessary,
     * which is useful for special forms, which don't have a set arity.  (That's actually why they need to be special
     * forms since Erlang/Elixir doesn't support variable arity functions otherwise.)
     *
     * @param call the call element
     * @param resolvedModuleName the expected {@link Call#resolvedModuleName()}
     * @param functionName the expected {@link Call#functionName()}
     * @return {@code true} if all arguments match and {@link Call#getDoBlock()} is not {@code null}; {@code false}.
     */
    public static boolean isCallingMacro(@NotNull final Call call,
                                         @NotNull final String resolvedModuleName,
                                         @NotNull final String functionName) {
        return call.isCalling(resolvedModuleName, functionName) && call.hasDoBlockOrKeyword();
    }

    /**
     * Whether {@code call} is of the named macro.
     *
     * Differs from {@link ElixirPsiImplUtil#isCalling(Call, String, String, int)} because this function ensures there
     * is a {@code do} block.  If the macro can be called without a {@code do} block, then
     * {@link ElixirPsiImplUtil#isCalling(Call, String, String, int)} should be called instead.
     *
     * @param call the call element
     * @param resolvedModuleName the expected {@link Call#resolvedModuleName()}
     * @param functionName the expected {@link Call#functionName()}
     * @param resolvedFinalArity the expected {@link Call#resolvedFinalArity()}
     * @return {@code true} if all arguments match and {@link Call#getDoBlock()} is not {@code null}; {@code false}.
     */
    @Contract(pure = true)
    public static boolean isCallingMacro(@NotNull final Call call,
                                         @NotNull final String resolvedModuleName,
                                         @NotNull final  String functionName,
                                         final int resolvedFinalArity) {
        return call.isCalling(resolvedModuleName, functionName, resolvedFinalArity) &&
                call.hasDoBlockOrKeyword();
    }

    public static boolean isDeclaringScope(@NotNull final ElixirStabOperation stabOperation) {
        boolean declaringScope = true;
        PsiElement parent = stabOperation.getParent();

        if (parent instanceof ElixirStab) {
            PsiElement grandParent = parent.getParent();

            if (grandParent instanceof ElixirBlockItem) {
                ElixirBlockItem blockItem = (ElixirBlockItem) grandParent;
                ElixirBlockIdentifier blockIdentifier = blockItem.getBlockIdentifier();

                String blockIdentifierText = blockIdentifier.getText();

                /* `after` is not a declaring scope because the timeout value does not need to be pinned even though it
                    must be a literal or declared in an outer scope */
                if (blockIdentifierText.equals("after")) {
                    declaringScope = false;
                }
            } else if (grandParent instanceof ElixirDoBlock) {
                Call call = (Call) grandParent.getParent();

                if (call.isCalling(KERNEL, COND)) {
                    declaringScope = false;
                }
            }
        }

        return declaringScope;
    }

    /**
     * Whether the given element presents a default argument (with {@code \\} in it.
     * @param argument an argument to a {@link Call}
     * @return {@code true} if in match operation with {@code \\} operator; otherwise, {@code false}.
     */
    private static boolean isDefaultArgument(PsiElement argument) {
        boolean defaultArgument = false;

        if (argument instanceof InMatch) {
            Operation operation = (Operation) argument;

            if (operation.operator().getText().trim().equals(DEFAULT_OPERATOR)) {
                defaultArgument = true;
            }
        }

        return defaultArgument;
    }

    public static boolean isExported(@NotNull final UnqualifiedNoParenthesesCall unqualifiedNoParenthesesCall) {
        return CallDefinitionClause.isPublicFunction(unqualifiedNoParenthesesCall) ||
                CallDefinitionClause.isPublicMacro(unqualifiedNoParenthesesCall);
    }

    /**
     * Whether the {@code arrow} is a pipe operation.
     *
     * @param arrow the parent (or futher ancestor of a {@link Call} that may be piped.
     * @return {@code} true if {@code arrow} is using the {@code "|>"} operator token.
     */
    private static boolean isPipe(@NotNull Arrow arrow) {
        Operator operator = arrow.operator();
        ASTNode[] arrowOperatorChildren = operator.getNode().getChildren(ARROW_OPERATOR_TOKEN_SET);
        boolean isPipe = false;

        if (arrowOperatorChildren.length == 1) {
            ASTNode arrowOperatorChild = arrowOperatorChildren[0];

            if (arrowOperatorChild.getText().equals("|>")) {
                isPipe = true;
            }
        }

        return isPipe;
    }

    /**
     * Whether the {@code callAncestor} is a pipe operation.
     *
     * @param callAncestor the parent (or further ancestor) of a {@link Call} that may be piped
     * @return {@code} true if {@code callAncestor} is an {@link Arrow} using the {@code "|>"} operator token.
     */
    private static boolean isPipe(@NotNull PsiElement callAncestor) {
        boolean isPipe = false;

        if (callAncestor instanceof Arrow) {
            isPipe = isPipe((Arrow) callAncestor);
        }

        return isPipe;
    }

    /*
     * Whether this is an argument in `defmodule <argument> do end` call.
     */
    public static boolean isModuleName(@NotNull final ElixirAccessExpression accessExpression) {
        PsiElement parent = accessExpression.getParent();
        boolean isModuleName = false;

        if (parent instanceof ElixirNoParenthesesOneArgument) {
            ElixirNoParenthesesOneArgument noParenthesesOneArgument = (ElixirNoParenthesesOneArgument) parent;

            isModuleName = noParenthesesOneArgument.isModuleName();
        }

        return isModuleName;
    }

    public static boolean isModuleName(@NotNull final QualifiableAlias qualifiableAlias) {
        PsiElement parent = qualifiableAlias.getParent();
        int siblingCount = parent.getChildren().length - 1;
        boolean isModuleName = false;

        /* check that this qualifiableAlias is the only child so subsections of alias chains don't say they are module
           names. */
        if (siblingCount == 0 && parent instanceof MaybeModuleName) {
            MaybeModuleName maybeModuleName = (MaybeModuleName) parent;

            isModuleName = maybeModuleName.isModuleName();
        }

        return isModuleName;
    }

    /*
     * Whether this is an argument in `defmodule <argument> do end` call.
     */
    public static boolean isModuleName(@NotNull final ElixirNoParenthesesOneArgument noParenthesesOneArgument) {
        PsiElement parent = noParenthesesOneArgument.getParent();
        boolean isModuleName = false;

        if (parent instanceof ElixirUnmatchedUnqualifiedNoParenthesesCall) {
            ElixirUnmatchedUnqualifiedNoParenthesesCall unmatchedUnqualifiedNoParenthesesCall = (ElixirUnmatchedUnqualifiedNoParenthesesCall) parent;

            isModuleName = unmatchedUnqualifiedNoParenthesesCall.isCallingMacro(KERNEL, DEFMODULE, 2);
        }

        return isModuleName;
    }

    @Contract(pure = true)
    public static boolean isOutermostQualifiableAlias(@NotNull QualifiableAlias qualifiableAlias) {
        PsiElement parent = qualifiableAlias.getParent();
        boolean outermost = false;

        /* prevents individual Aliases or tail qualified aliases of qualified chain from having reference separate
           reference from overall chain */
        if (!(parent instanceof QualifiableAlias)) {
            PsiElement grandParent = parent.getParent();

            // prevents first Alias of a qualified chain from having a separate reference from overall chain
            if (!(grandParent instanceof QualifiableAlias)) {
                outermost = true;
            }
        }

        return outermost;
    }

    /*
     * @return {@code true} if {@code element} should not have {@code quote} called on it because Elixir natively
     *   ignores such tokens.  {@code false} if {@code element} should have {@code quote} called on it.
     */
    public static boolean isUnquoted(PsiElement element) {
        boolean unquoted = false;

        for (Class unquotedType : UNQUOTED_TYPES) {
            if (unquotedType.isInstance(element)) {
                unquoted = true;
                break;
            }
        }

        return unquoted;
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable leftOperand(@NotNull final ElixirStabOperation stabOperation) {
        Quotable leftOperand = stabOperation.getStabParenthesesSignature();

        if (leftOperand == null) {
            leftOperand = stabOperation.getStabNoParenthesesSignature();
        }

        return leftOperand;
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable leftOperand(Infix infix) {
        return org.elixir_lang.psi.operation.infix.Normalized.leftOperand(infix);
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable leftOperand(@NotNull NotIn notIn) {
        return org.elixir_lang.psi.operation.not_in.Normalized.leftOperand(notIn);
    }

    /* Returns the 0-indexed line number for the element */
    public static int lineNumber(ASTNode node) {
        return document(node.getPsi()).getLineNumber(node.getStartOffset());
    }

    public static OtpErlangTuple lineNumberKeywordTuple(ASTNode node) {
        return keywordTuple(
                "line",
                lineNumber(node) + 1
        );
    }

    @NotNull
    public static Call[] macroChildCalls(Call macro) {
        List<Call> childCallList = macroChildCallList(macro);

        return childCallList.toArray(new Call[childCallList.size()]);
    }

    @NotNull
    public static List<Call> macroChildCallList(@NotNull Call macro) {
        List<Call> childCallList = null;
        ElixirDoBlock doBlock = macro.getDoBlock();

        if (doBlock != null) {
            ElixirStab stab = doBlock.getStab();

            if (stab != null) {
                PsiElement[] stabChildren = stab.getChildren();

                if (stabChildren.length == 1) {
                    PsiElement stabChild = stabChildren[0];

                    if (stabChild instanceof ElixirStabBody) {
                        childCallList = macroChildCallList(stabChild);
                    }
                }
            }
        } else { // one liner version with `do:` keyword argument
            PsiElement[] finalArguments = ElixirPsiImplUtil.finalArguments(macro);

            assert finalArguments != null;
            assert finalArguments.length > 0;

            PsiElement potentialKeywords = finalArguments[finalArguments.length - 1];

            if (potentialKeywords instanceof QuotableKeywordList) {
                QuotableKeywordList quotableKeywordList = (QuotableKeywordList) potentialKeywords;
                List<QuotableKeywordPair> quotableKeywordPairList = quotableKeywordList.quotableKeywordPairList();
                QuotableKeywordPair firstQuotableKeywordPair = quotableKeywordPairList.get(0);
                Quotable keywordKey = firstQuotableKeywordPair.getKeywordKey();

                if (keywordKey.getText().equals("do")) {
                    Quotable keywordValue = firstQuotableKeywordPair.getKeywordValue();

                    if (keywordValue instanceof Call) {
                        Call childCall = (Call) keywordValue;
                        childCallList = Collections.singletonList(childCall);
                    }
                }
            }
        }

        if (childCallList == null) {
            childCallList = Collections.emptyList();
        }

        return childCallList;
    }

    @NotNull
    private static List<Call> macroChildCallList(@NotNull PsiElement element) {
        List<Call> callList;

        if (element instanceof ElixirAccessExpression) {
            callList = macroChildCallList(element.getFirstChild());
        } else if (element instanceof ElixirList || element instanceof ElixirStabBody) {
            callList = new ArrayList<>();

            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Call) {
                    Call childCall = (Call) child;
                    callList.add(childCall);
                } else if (child instanceof ElixirAccessExpression) {
                    callList.addAll(macroChildCallList(child));
                }
            }
        } else {
            callList = Collections.emptyList();
        }

        return callList;
    }

    public static OtpErlangList metadata(ASTNode node) {
        OtpErlangObject[] keywordListElements = {
                lineNumberKeywordTuple(node)
        };

        return new OtpErlangList(keywordListElements);
    }

    public static OtpErlangList metadata(Operator operator) {
        return metadata(operatorTokenNode(operator));
    }

    public static OtpErlangList metadata(PsiElement element) {
        return metadata(element.getNode());
    }

    /* TODO determine what counter means in Code.string_to_quoted("Foo")
       {:ok, {:__aliases__, [counter: 0, line: 1], [:Foo]}} */
    public static OtpErlangList metadata(PsiElement element, int counter) {
        /* QuotableKeywordList should be compared by sorting keys, but Elixir does counter first, so it's simpler to just use
           same order than detect a OtpErlangList is a QuotableKeywordList */
        final OtpErlangObject[] keywordListElements = {
                keywordTuple("counter", counter),
                lineNumberKeywordTuple(element.getNode())
        };

        return new OtpErlangList(keywordListElements);
    }

    @Contract(pure = true)
    @NotNull
    public static String moduleAttributeName(@NotNull final AtNonNumericOperation atNonNumericOperation) {
        return atNonNumericOperation.getText();
    }

    @Contract(pure = true)
    @NotNull
    public static String moduleAttributeName(@NotNull final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        return atUnqualifiedNoParenthesesCall.getAtIdentifier().getText();
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String moduleName(@NotNull @SuppressWarnings("unused") final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        // Always null because it's unqualified.
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String moduleName(@NotNull @SuppressWarnings("unused") final DotCall dotCall) {
        // Always null because anonymous
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String moduleName(@NotNull @SuppressWarnings("unused") final NotIn notIn) {
        // Always null because it's unqualified.
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String moduleName(@NotNull @SuppressWarnings("unused") final Operation operation) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String moduleName(@NotNull @SuppressWarnings("unused") final Unqualified unqualified) {
        // Always null because it's unqualified.
        return null;
    }

    @Contract(pure = true)
    @NotNull
    public static String moduleName(@NotNull final Qualified qualified) {
        // TODO handle more complex qualifiers besides Aliases
        return computeReadAction(qualified.getFirstChild()::getText);
    }

    private static GlobalSearchScope moduleWithDependentsScope(PsiElement element) {
        VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
        Project project = element.getProject();
        com.intellij.openapi.module.Module module = ModuleUtilCore.findModuleForFile(
                virtualFile,
                project
        );

        GlobalSearchScope globalSearchScope;

        // module can be null for scratch files
        if (module != null) {
            globalSearchScope = GlobalSearchScope.moduleWithDependentsScope(module);
        } else {
            globalSearchScope = GlobalSearchScope.allScope(project);
        }

        return globalSearchScope;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement nextSiblingExpression(@NotNull PsiElement element) {
        return siblingExpression(element, NEXT_SIBLING);
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable operand(Prefix prefix) {
        return org.elixir_lang.psi.operation.prefix.Normalized.operand(prefix);
    }

    @Contract(pure = true)
    @NotNull
    public static Operator operator(@NotNull final ElixirStabOperation stabOperation) {
        return stabOperation.getStabInfixOperator();
    }

    @Contract(pure = true)
    @NotNull
    public static Operator operator(Infix infix) {
        return Normalized.operator(infix);
    }

    @Contract(pure = true)
    @NotNull
    public static Operator operator(Prefix prefix) {
        return Normalized.operator(prefix);
    }

    @Contract(pure = true)
    @NotNull
    public static ASTNode operatorTokenNode(@NotNull Operator operator) {
        return operator
                    .getNode()
                    .getChildren(
                            operator.operatorTokenSet()
                    )[0];
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirAdditionInfixOperator additionInfixOperator) {
        return ADDITION_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirAndInfixOperator andInfixOperator) {
        return AND_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirArrowInfixOperator arrowInfixOperator) {
        return ARROW_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirAtPrefixOperator atPrefixOperator) {
        return AT_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirCapturePrefixOperator capturePrefixOperator) {
        return CAPTURE_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirComparisonInfixOperator comparisonInfixOperator) {
        return COMPARISON_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirDotInfixOperator dotInfixOperator) {
        return DOT_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirMultiplicationInfixOperator multiplicationInfixOperator) {
        return MULTIPLICATIVE_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirInInfixOperator inInfixOperator) {
        return IN_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirInMatchInfixOperator inMatchInfixOperator) {
        return IN_MATCH_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirMapPrefixOperator mapPrefixOperator) {
        return STRUCT_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirMatchInfixOperator matchInfixOperator) {
        return MATCH_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirNotInfixOperator notInfixOperator) {
        return NOT_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirOrInfixOperator orInfixOperator) {
        return OR_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirPipeInfixOperator pipeInfixOperator) {
        return PIPE_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirRelationalInfixOperator relationalInfixOperator) {
        return RELATIONAL_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirStabInfixOperator stabInfixOperator) {
        return STAB_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirThreeInfixOperator threeInfixOperator) {
        return THREE_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirTwoInfixOperator twoInfixOperator) {
        return TWO_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirTypeInfixOperator typeInfixOperator) {
        return TYPE_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirUnaryPrefixOperator unaryPrefixOperator) {
        return UNARY_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @NotNull
    public static TokenSet operatorTokenSet(@SuppressWarnings("unused") final ElixirWhenInfixOperator whenInfixOperator) {
        return WHEN_OPERATOR_TOKEN_SET;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement previousSiblingExpression(@NotNull PsiElement element) {
        return siblingExpression(element, PREVIOUS_SIBLING);
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final DotCall dotCall) {
        List<ElixirParenthesesArguments> parenthesesArgumentsList = dotCall.getParenthesesArgumentsList();

        ElixirParenthesesArguments primaryParenthesesArguments = parenthesesArgumentsList.get(0);
        return primaryParenthesesArguments.arguments();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final Infix infix) {
        PsiElement[] children = infix.getChildren();
        int operatorIndex = Normalized.operatorIndex(children);
        Quotable leftOperand = org.elixir_lang.psi.operation.infix.Normalized.leftOperand(children, operatorIndex);
        Quotable rightOperand = org.elixir_lang.psi.operation.infix.Normalized.rightOperand(children, operatorIndex);

        return new PsiElement[]{
                leftOperand,
                rightOperand
        };
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final ElixirUnqualifiedNoParenthesesManyArgumentsCall unqualifiedNoParenthesesManyArgumentsCall) {
        Arguments arguments = unqualifiedNoParenthesesManyArgumentsCall.getNoParenthesesStrict();
        PsiElement[] primaryArguments;

        if (arguments != null) {
            primaryArguments = arguments.arguments();
        } else {
            /* noParenthesesManyArguments is a private rule, so when noParenthesesStrict is not present, then the
               noParenthesesManyArguments are direct children, but so is he identifier, so the identifier needs to be
               ignored  */
            PsiElement[] children = unqualifiedNoParenthesesManyArgumentsCall.getChildren();

            assert children[0] instanceof ElixirIdentifier;

            primaryArguments = Arrays.copyOfRange(children, 1, children.length);
        }

        return primaryArguments;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement[] primaryArguments(@NotNull @SuppressWarnings("unused") final None none) {
        return null;
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final NotIn notIn) {
        PsiElement[] children = notIn.getChildren();
        Quotable leftOperand = org.elixir_lang.psi.operation.not_in.Normalized.leftOperand(children);
        Quotable rightOperand = org.elixir_lang.psi.operation.not_in.Normalized.rightOperand(children);

        return new PsiElement[]{
                leftOperand,
                rightOperand
        };
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final NoParenthesesOneArgument noParenthesesOneArgument) {
        return noParenthesesOneArgument.getNoParenthesesOneArgument().arguments();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final Parentheses parentheses) {
        ElixirMatchedParenthesesArguments matchedParenthesesArguments = parentheses.getMatchedParenthesesArguments();
        List<ElixirParenthesesArguments> parenthesesArgumentsList = matchedParenthesesArguments.getParenthesesArgumentsList();

        ElixirParenthesesArguments primaryParenthesesArguments = parenthesesArgumentsList.get(0);
        return primaryParenthesesArguments.arguments();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement[] primaryArguments(@NotNull final Prefix prefix) {
        return new PsiElement[]{
            prefix.operand()
        };
    }

    @Contract(pure = true)
    @Nullable
    public static Integer primaryArity(@NotNull final Call call) {
        PsiElement[] primaryArguments = call.primaryArguments();
        Integer primaryArity = null;

        if (primaryArguments != null) {
            primaryArity = primaryArguments.length;
        }

        return primaryArity;
    }

    /**
     * {@code {:ok, value} = func() && value == literal}
     */
    public static boolean processDeclarations(@NotNull final And and,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        boolean keepProcessing = true;

        if (Normalized.operator(and).getText().equals("&&")) {
            PsiElement leftOperand = org.elixir_lang.psi.operation.infix.Normalized.leftOperand(and);

            if (leftOperand != null && !PsiTreeUtil.isAncestor(leftOperand, lastParent, false)) {
                // the left operand is not inherently declaring, it should only be if a match is in the left operand
                keepProcessing = processor.execute(leftOperand, state.put(DECLARING_SCOPE, false));
            }
        }

        return keepProcessing;
    }

    /**
     * {@code def(macro)?p?}, {@code for}, or {@code with} can declare variables
     */
    public static boolean processDeclarations(@NotNull final Call call,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        boolean keepProcessing = true;

        // need to check if call is place because lastParent is set to place at start of treeWalkUp
        if (!call.isEquivalentTo(lastParent) || call.isEquivalentTo(place)) {
            if (call.isCalling(KERNEL, ALIAS)) {
                keepProcessing = processor.execute(call, state);
            } else if (CallDefinitionClause.is(call) || // call parameters
                    Delegation.is(call) || // delegation call parameters
                    Module.is(call) || // module Alias
                    call.isCalling(KERNEL, DESTRUCTURE) || // left operand
                    call.isCallingMacro(KERNEL, IF) || // match in condition
                    call.isCallingMacro(KERNEL, Function.FOR) || // comprehension match variable
                    call.isCallingMacro(KERNEL, UNLESS) || // match in condition
                    call.isCallingMacro(KERNEL, "with") // <- or = variable
                    ) {
                keepProcessing = processor.execute(call, state);
            } else if (org.elixir_lang.structure_view.element.Quote.is(call)) { // quote :bind_quoted keys{
                PsiElement bindQuoted = ElixirPsiImplUtil.keywordArgument(call, "bind_quoted");
                /* the bind_quoted keys declare variable only valid inside the do block, so any place in the
                   bindQuoted already must be the bind_quoted values that must be declared before the quote */
                if (bindQuoted != null && !PsiTreeUtil.isAncestor(bindQuoted, place, false)) {
                    keepProcessing = processor.execute(call, state);
                }
            } else if (hasDoBlockOrKeyword(call)) {
                // unknown macros that take do blocks often allow variables to be declared in their arguments
                keepProcessing = processor.execute(call, state);
            }
        }

        return keepProcessing;
    }

    public static boolean processDeclarations(@NotNull final ElixirAlias alias,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull PsiElement place) {
        return processDeclarationsRecursively(
                alias,
                processor,
                state,
                lastParent,
                place
        );
    }

    public static boolean processDeclarations(@NotNull final ElixirMultipleAliases multipleAliases,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull PsiElement entrance) {
        return processDeclarationsRecursively(
                multipleAliases,
                processor,
                state,
                lastParent,
                entrance
        );
    }

    public static boolean processDeclarations(@NotNull final ElixirStabBody scope,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        return processDeclarationsInPreviousSibling(scope, processor, state, lastParent, place);
    }

    public static boolean processDeclarations(@NotNull final ElixirStabOperation stabOperation,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              @SuppressWarnings("unused") PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        boolean keepProcessing = true;
        PsiElement signature = stabOperation.leftOperand();

        if (signature != null) {
            boolean declaringScope = isDeclaringScope(stabOperation);

            keepProcessing = processor.execute(signature, state.put(DECLARING_SCOPE, declaringScope));
        }

        return keepProcessing;
    }

    public static boolean processDeclarations(@NotNull final Match match,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        PsiElement rightOperand = match.rightOperand();
        PsiElement leftOperand = match.leftOperand();
        boolean checkRight = false;
        boolean checkLeft = false;

        Triple triple = new Triple(match.getChildren());
        Position position = triple.ancestorPosition(lastParent);

        if (position != null) {
            switch (position) {
                case LEFT:
                    checkLeft  = true;
                    checkRight = false;
                    break;
                case OPERATOR:
                    checkLeft  = true;
                    checkRight = true;
                    break;
                case RIGHT:
                    checkLeft  = false;
                    checkRight = true;
                    break;
            }
        } else if (PsiTreeUtil.isAncestor(match, lastParent, false)) {
            checkLeft  = true;
            checkRight = true;
        }

        boolean keepProcessing = true;

        if (checkRight || checkLeft) {
            // check right-operand first if both sides need to be checked because only left-side can do rebinding
            if (checkRight && rightOperand != null) {
                keepProcessing = processor.execute(rightOperand, state);
            }

            if (checkLeft && leftOperand != null && keepProcessing) {
                keepProcessing = processor.execute(leftOperand, state);
            }
        } else {
            error(
                    Match.class,
                    "Could not determine whether to check left operand, right operand, or both of match, " +
                            "so checking none when processing declarations",
                    match
            );
        }

        return keepProcessing;
    }

    public static boolean processDeclarations(@NotNull final QualifiedAlias qualifiedAlias,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              @SuppressWarnings("unused") PsiElement lastParent,
                                              @NotNull @SuppressWarnings("unused") PsiElement place) {
        return processor.execute(qualifiedAlias, state);
    }

    /**
     * Processes declarations in siblings of {@code lastParent} backwards from {@code lastParent}.
     *
     * @param scope an {@link ElixirStabBody} or {@link ElixirFile} that has a sequence of expressions as children
     */
    public static boolean processDeclarationsInPreviousSibling(@NotNull final PsiElement scope,
                                                               @NotNull PsiScopeProcessor processor,
                                                               @NotNull ResolveState state,
                                                               PsiElement lastParent,
                                                               @NotNull @SuppressWarnings("unused") PsiElement entrance) {
        boolean keepProcessing = true;

        if (scope.isEquivalentTo(lastParent.getParent())) {
            PsiElement previousSibling = lastParent.getPrevSibling();

            while (previousSibling != null) {
                if (!(previousSibling instanceof ElixirEndOfExpression ||
                        previousSibling instanceof PsiComment ||
                        previousSibling instanceof PsiWhiteSpace)) {
                    if (!createsNewScope(previousSibling)) {
                        keepProcessing = processor.execute(previousSibling, state);

                        if (!keepProcessing) {
                            break;
                        }
                    }
                }

                previousSibling = previousSibling.getPrevSibling();
            }
        } else if (!(lastParent instanceof ElixirFile)) {
            error(
                    PsiElement.class,
                    "Scope is not lastParent's parent\nlastParent:\n" + lastParent.getText(),
                    scope
            );
        }

        return keepProcessing;
    }

    public static boolean processDeclarationsRecursively(@NotNull final PsiElement psiElement,
                                                         @NotNull PsiScopeProcessor processor,
                                                         @NotNull ResolveState state,
                                                         PsiElement lastParent,
                                                         @NotNull PsiElement place) {
        boolean keepProcessing = processor.execute(psiElement, state);

        if (keepProcessing) {
            @Nullable
            PsiElement child = psiElement.getFirstChild();

            while (child != null && child != lastParent) {
                if (!child.processDeclarations(processor, state, lastParent, place)) {
                    keepProcessing = false;

                    break;
                }

                child = child.getNextSibling();
            }
        }

        return keepProcessing;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final AssociationOperation associationOperation) {
        PsiElement[] children = associationOperation.getChildren();

        // associationInfixOperator is private so not a PsiElement
        assert children.length == 2;

        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];

        int i = 0;
        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;

            quotedChildren[i++] = quotableChild.quote();
        }

        return new OtpErlangTuple(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final Infix infix) {
        Quotable leftOperand = infix.leftOperand();
        OtpErlangObject quotedLeftOperand = leftOperand.quote();

        Operator operator = infix.operator();
        OtpErlangObject quotedOperator = operator.quote();

        PsiElement rightOperand = infix.rightOperand();
        OtpErlangObject quotedRightOperand;

        if (rightOperand != null && rightOperand instanceof Quotable) {
            Quotable quotableRightOperand = (Quotable) rightOperand;
            quotedRightOperand = quotableRightOperand.quote();
        } else {
            // this is not valid Elixir quoting, but something needs to be there for quoting to work
            quotedRightOperand = NIL;
        }

        return quotedFunctionCall(
                quotedOperator,
                metadata(operator),
                quotedLeftOperand,
                quotedRightOperand
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final NotIn notIn) {
        Quotable leftOperand = notIn.leftOperand();
        OtpErlangObject quotedLeftOperand = leftOperand.quote();

        Operator notOperator = notIn.getNotInfixOperator();
        OtpErlangObject quotedNotOperator = notOperator.quote();

        Operator inOperator = notIn.getInInfixOperator();
        OtpErlangObject quotedInOperator = inOperator.quote();

        PsiElement rightOperand = notIn.rightOperand();
        OtpErlangObject quotedRightOperand;

        if (rightOperand != null) {
            Quotable quotableRightOperand = (Quotable) rightOperand;
            quotedRightOperand = quotableRightOperand.quote();
        } else {
            // this is not valid Elixir quoting, but something needs to be there for quoting to work
            quotedRightOperand = NIL;
        }

        return quotedFunctionCall(
                quotedNotOperator,
                metadata(notOperator),
                quotedFunctionCall(
                        quotedInOperator,
                        metadata(inOperator),
                        quotedLeftOperand,
                        quotedRightOperand
                )
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirBitString bitString) {
        ASTNode node = bitString.getNode();
        ASTNode[] openingBits = node.getChildren(TokenSet.create(ElixirTypes.OPENING_BIT));

        assert openingBits.length == 1;

        ASTNode openingBit = openingBits[0];

        PsiElement[] children = bitString.getChildren();
        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];

        int i = 0;
        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;
            quotedChildren[i++] = quotableChild.quote();
        }

        return quotedFunctionCall(
                "<<>>",
                metadata(openingBit),
                quotedChildren
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirBlockIdentifier blockIdentifier) {
        return new OtpErlangAtom(blockIdentifier.getNode().getText());
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirBlockItem blockItem) {
        Quotable blockIdentifier = blockItem.getBlockIdentifier();
        Quotable stab = blockItem.getStab();
        OtpErlangObject quotedValue = NIL;

        if (stab != null) {
            quotedValue = stab.quote();
        }

        return new OtpErlangTuple(
                new OtpErlangObject[]{
                        blockIdentifier.quote(),
                        quotedValue
                }
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirBracketArguments bracketArguments) {
        PsiElement[] children = bracketArguments.getChildren();

        assert children.length == 1;

        Quotable child = (Quotable) children[0];

        return child.quote();
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final Digits digits) {
        final String text = digits.getText();
        final int base = digits.base();
        OtpErlangLong quoted;

        try {
            long value = Long.parseLong(text, base);
            quoted = new OtpErlangLong(value);
        } catch (NumberFormatException exception) {
            BigInteger value = new BigInteger(text, base);
            quoted = new OtpErlangLong(value);
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAccessExpression accessExpression) {
        PsiElement[] children = accessExpression.getChildren();

        if (children.length != 1) {
            throw new NotImplementedException("Expecting 1 child in accessExpression");
        }

        Quotable child = (Quotable) children[0];

        return child.quote();
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAlias alias) {
        return quotedFunctionCall(
                ALIASES,
                metadata(alias, 0),
                new OtpErlangAtom(alias.getText())
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAnonymousFunction anonymousFunction) {
        Quotable stabOperations = anonymousFunction.getStab();
        OtpErlangObject quotedStab = stabOperations.quote();

        OtpErlangList metadata = metadata(anonymousFunction);

        return new OtpErlangTuple(
                new OtpErlangObject[] {
                        FN,
                        metadata,
                        quotedStab
                }
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAssociations associations) {
        Quotable associationBase = associations.getAssociationsBase();

        return associationBase.quote();
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAssociationsBase associationsBase) {
        PsiElement[] children = associationsBase.getChildren();
        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];
        int i = 0;

        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;

            quotedChildren[i++] = quotableChild.quote();
        }

        return new OtpErlangList(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAtom atom) {
        OtpErlangObject quoted;
        ElixirCharListLine charListLine = atom.getCharListLine();

        if (charListLine != null) {
            quoted = charListLine.quoteAsAtom();
        } else {
            ElixirStringLine stringLine = atom.getStringLine();

            if (stringLine != null) {
                quoted = stringLine.quoteAsAtom();
            } else {
                ASTNode atomNode = atom.getNode();
                ASTNode atomFragmentNode = atomNode.getLastChildNode();

                assert atomFragmentNode.getElementType() == ElixirTypes.ATOM_FRAGMENT;

                quoted = new OtpErlangAtom(atomFragmentNode.getText());
            }
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirAtomKeyword atomKeyword) {
        return new OtpErlangAtom(atomKeyword.getText());
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirCharListLine charListLine) {
        ElixirQuoteCharListBody quoteCharListBody = charListLine.getQuoteCharListBody();

        return quotedChildNodes(charListLine, childNodes(quoteCharListBody));
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirCharToken charToken) {
        ASTNode[] children = charToken.getNode().getChildren(null);

        if (children.length != 2) {
            throw new NotImplementedException("CharToken expected to be ?(<character>|<escape sequence>)");
        }

        final ASTNode tokenized = children[1];
        IElementType tokenizedElementType = tokenized.getElementType();
        int codePoint;

        if (tokenizedElementType == ElixirTypes.CHAR_LIST_FRAGMENT) {
            if (tokenized.getTextLength() != 1) {
                throw new NotImplementedException("Tokenized character expected to only be one character long");
            }

            String tokenizedString = tokenized.getText();
            codePoint = tokenizedString.codePointAt(0);
        } else {
            EscapeSequence escapeSequence = (EscapeSequence) tokenized.getPsi();
            codePoint = escapeSequence.codePoint();
        }

        return new OtpErlangLong(codePoint);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirContainerAssociationOperation containerAssociationOperation) {
        PsiElement[] children = containerAssociationOperation.getChildren();

        // associationInfixOperator is private so not a PsiElement
        assert children.length == 2;

        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];

        int i = 0;
        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;

            quotedChildren[i++] = quotableChild.quote();
        }

        return new OtpErlangTuple(quotedChildren);
    }

    /**
     *
     * @param call
     * @return {@code null} if call is at top-level
     */
    @Contract(pure = true)
    @Nullable
    public static Call enclosingMacroCall(PsiElement element) {
        Call enclosingMacroCall = null;
        PsiElement parent = element.getParent();

        if (parent instanceof ElixirDoBlock) {
            PsiElement grandParent = parent.getParent();

            if (grandParent instanceof Call) {
                enclosingMacroCall = (Call) grandParent;
            }
        } else if (parent instanceof ElixirStabBody) {
            PsiElement grandParent = parent.getParent();

            if (grandParent instanceof ElixirStab) {
                PsiElement greatGrandParent = grandParent.getParent();

                if (greatGrandParent instanceof ElixirBlockItem) {
                    PsiElement greatGreatGrandParent = greatGrandParent.getParent();

                    if (greatGreatGrandParent instanceof ElixirBlockList) {
                        PsiElement greatGreatGreatGrandParent = greatGreatGrandParent.getParent();

                        if (greatGreatGreatGrandParent instanceof ElixirDoBlock) {
                            PsiElement greatGreatGreatGreatGrandParent = greatGreatGreatGrandParent.getParent();

                            if (greatGreatGreatGreatGrandParent instanceof Call) {
                                enclosingMacroCall = (Call) greatGreatGreatGreatGrandParent;
                            }
                        }
                    }
                } else if (greatGrandParent instanceof ElixirDoBlock) {
                    PsiElement greatGreatGrandParent = greatGrandParent.getParent();

                    if (greatGreatGrandParent instanceof Call) {
                        enclosingMacroCall = (Call) greatGreatGrandParent;
                    }
                } else if (greatGrandParent instanceof ElixirParentheticalStab) {
                    PsiElement greatGreatGrandParent = greatGrandParent.getParent();

                    if (greatGreatGrandParent instanceof ElixirAccessExpression) {
                        enclosingMacroCall = enclosingMacroCall(greatGreatGrandParent);
                    }
                }
            } else if (grandParent instanceof ElixirStabOperation) {
                PsiElement stabOperationParent = grandParent.getParent();

                if (stabOperationParent instanceof ElixirStab) {
                    PsiElement stabParent = stabOperationParent.getParent();

                    if (stabParent instanceof ElixirAnonymousFunction) {
                        PsiElement anonymousFunctionParent = stabParent.getParent();

                        if (anonymousFunctionParent instanceof ElixirAccessExpression) {
                            PsiElement accessExpressionParent = anonymousFunctionParent.getParent();

                            if (accessExpressionParent instanceof Arguments) {
                                PsiElement argumentsParent = accessExpressionParent.getParent();

                                if (argumentsParent instanceof ElixirMatchedParenthesesArguments) {
                                    PsiElement matchedParenthesesArgumentsParent = argumentsParent.getParent();

                                    if (matchedParenthesesArgumentsParent instanceof Call) {
                                        Call matchedParenthesesArgumentsParentCall = (Call) matchedParenthesesArgumentsParent;

                                        if (matchedParenthesesArgumentsParentCall.isCalling("Enum", "map") ||
                                                matchedParenthesesArgumentsParentCall.isCalling("Enum", "each")) {
                                            enclosingMacroCall = enclosingMacroCall(matchedParenthesesArgumentsParentCall);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (parent instanceof Arguments ||
                // See https://github.com/elixir-lang/elixir/blob/v1.5/lib/elixir/lib/protocol.ex#L633
                parent instanceof AtUnqualifiedNoParenthesesCall ||
                // See https://github.com/phoenixframework/phoenix/blob/v1.2.4/lib/phoenix/template.ex#L380-L392
                parent instanceof ElixirAccessExpression ||
                // See https://github.com/absinthe-graphql/absinthe/blob/v1.3.0/lib/absinthe/schema/notation/writer.ex#L24-L44
                parent instanceof ElixirList ||
                parent instanceof ElixirMatchedParenthesesArguments ||
                // See https://github.com/absinthe-graphql/absinthe/blob/v1.3.0/lib/absinthe/schema/notation/writer.ex#L96
                parent instanceof ElixirNoParenthesesManyStrictNoParenthesesExpression ||
                parent instanceof ElixirTuple ||
                parent instanceof Match ||
                parent instanceof QualifiedAlias ||
                parent instanceof QualifiedMultipleAliases) {
            enclosingMacroCall = enclosingMacroCall(parent);
        } else if (parent instanceof Call) {
            Call parentCall = (Call) parent;

            if (parentCall.isCalling(KERNEL, ALIAS)) {
                enclosingMacroCall = parentCall;
            } else if (parentCall.isCalling(org.elixir_lang.psi.call.name.Module.MODULE, CREATE, 3)) {
                enclosingMacroCall = parentCall;
            }
        } else if (parent instanceof QuotableKeywordPair) {
            QuotableKeywordPair parentKeywordPair = (QuotableKeywordPair) parent;

            if (hasKeywordKey(parentKeywordPair, "do")) {
                PsiElement grandParent = parent.getParent();

                if (grandParent instanceof QuotableKeywordList) {
                    PsiElement greatGrandParent = grandParent.getParent();

                    if (greatGrandParent instanceof ElixirNoParenthesesOneArgument) {
                        PsiElement greatGreatGrandParent = greatGrandParent.getParent();

                        if (greatGreatGrandParent instanceof Call) {
                            enclosingMacroCall = (Call) greatGreatGrandParent;
                        }
                    }
                }
            }
        }

        return enclosingMacroCall;
    }

    /* Returns a virtual PsiElement representing the spaces at the end of charListHeredocLineWhitespace that are not
     * consumed by prefixLength.
     *
     * @return null if prefixLength is greater than or equal to text length of charListHeredcoLineWhitespace.
     */
    @Contract(pure = true)
    @Nullable
    public static ASTNode excessWhitespace(@NotNull final ElixirHeredocLinePrefix heredocLinePrefix, @NotNull final IElementType fragmentType, final int prefixLength) {
        int availableLength = heredocLinePrefix.getTextLength();
        int excessLength = availableLength - prefixLength;
        ASTNode excessWhitespaceASTNode = null;

        if (excessLength > 0) {
            char[] excessWhitespaceChars = new char[excessLength];
            Arrays.fill(excessWhitespaceChars, ' ');
            String excessWhitespaceString = new String(excessWhitespaceChars);
            excessWhitespaceASTNode = Factory.createSingleLeafElement(
                    fragmentType,
                    excessWhitespaceString,
                    0,
                    excessLength,
                    null,
                    heredocLinePrefix.getManager()
            );
        }

        return excessWhitespaceASTNode;
    }

    @Contract(pure = true)
    public static int exportedArity(@NotNull final UnqualifiedNoParenthesesCall unqualifiedNoParenthesesCall) {
        int arity = MaybeExported.UNEXPORTED_ARITY;

        if (isExported(unqualifiedNoParenthesesCall)) {
            Pair<String, IntRange> nameArityRange = CallDefinitionClause.nameArityRange(unqualifiedNoParenthesesCall);

            if (nameArityRange != null) {
                IntRange arityRange = nameArityRange.second;

                if (arityRange != null) {
                    int minimumArity = arityRange.getMinimumInteger();
                    int maximumArity = arityRange.getMaximumInteger();

                    if (minimumArity == maximumArity) {
                        arity = minimumArity;
                    }
                }
            }
        }

        return arity;
    }

    @Contract(pure = true)
    @Nullable
    public static String exportedName(@NotNull final UnqualifiedNoParenthesesCall unqualifiedNoParenthesesCall) {
        String name = null;

        if (isExported(unqualifiedNoParenthesesCall)) {
            Pair<String, IntRange> nameArityRange = CallDefinitionClause.nameArityRange(unqualifiedNoParenthesesCall);

            if (nameArityRange != null) {
                name = nameArityRange.first;
            }
        }

        return name;
    }

    /**
     * The outer most arguments
     *
     * @return {@link Call#primaryArguments}
     */
    @Nullable
    public static PsiElement[] finalArguments(@NotNull final Call call) {
        PsiElement[] finalArguments = call.secondaryArguments();

        if (finalArguments == null) {
            finalArguments = call.primaryArguments();
        }

        return finalArguments;
    }

    public static LocalSearchScope followingSiblingsSearchScope(PsiElement element) {
        List<PsiElement> followingSiblingList = new ArrayList<PsiElement>();
        PsiElement previousSibling = element;
        PsiElement followingSibling = previousSibling.getNextSibling();

        while (followingSibling != null) {
            followingSiblingList.add(followingSibling);

            previousSibling = followingSibling;
            followingSibling = previousSibling.getNextSibling();
        }

        return new LocalSearchScope(followingSiblingList.toArray(new PsiElement[followingSiblingList.size()]));
    }

    @Contract(pure = true)
    @NotNull
    public static String fullyQualifiedName(@NotNull final ElixirAlias alias) {
        return alias.getName();
    }

    @Contract(pure = true)
    @Nullable
    public static String fullyQualifiedName(@NotNull final QualifiableAlias qualifiableAlias) {
        String fullyQualifiedName = null;
        PsiElement[] children = qualifiableAlias.getChildren();
        int operatorIndex = Normalized.operatorIndex(children);
        Quotable qualifier = org.elixir_lang.psi.operation.infix.Normalized.leftOperand(children, operatorIndex);

        String qualifierName = null;

        if (qualifier instanceof Call) {
            Call qualifierCall = (Call) qualifier;

            if (qualifierCall.isCalling(KERNEL, __MODULE__, 0)) {
                Call enclosingCall = enclosingModularMacroCall(qualifierCall);

                if (enclosingCall != null && enclosingCall instanceof StubBased) {
                    StubBased enclosingStubBasedCall = (StubBased) enclosingCall;
                    qualifierName = enclosingStubBasedCall.canonicalName();
                }
            }
        } else if (qualifier instanceof QualifiableAlias) {
            QualifiableAlias qualifiableQualifier = (QualifiableAlias) qualifier;

            qualifierName = qualifiableQualifier.fullyQualifiedName();
        } else if (qualifier instanceof ElixirAccessExpression) {
            PsiElement qualifierChild = stripAccessExpression(qualifier);

            if (qualifierChild instanceof ElixirAlias) {
                ElixirAlias childAlias = (ElixirAlias) qualifierChild;

                qualifierName = childAlias.getName();
            }
        }

        if (qualifierName != null) {
            Quotable rightOperand = org.elixir_lang.psi.operation.infix.Normalized.rightOperand(
                    children,
                    operatorIndex
            );

            if (rightOperand instanceof ElixirAlias) {
                ElixirAlias relativeAlias = (ElixirAlias) rightOperand;

                fullyQualifiedName = qualifierName + "." + relativeAlias.getName();
            }
        }

        return fullyQualifiedName;
    }

    @NotNull
    public static PsiElement fullyResolveAlias(@NotNull QualifiableAlias alias,
                                               @Nullable PsiReference startingReference) {
        PsiElement fullyResolved;
        PsiElement currentResolved = alias;
        PsiReference reference = startingReference;

        do {
            if (reference == null) {
                reference = currentResolved.getReference();
            }

            if (reference != null) {
                if (reference instanceof PsiPolyVariantReference) {
                    PsiPolyVariantReference polyVariantReference = (PsiPolyVariantReference) reference;
                    ResolveResult[] resolveResults = polyVariantReference.multiResolve(false);
                    int resolveResultCount = resolveResults.length;

                    if (resolveResultCount == 0) {
                        fullyResolved = currentResolved;

                        break;
                    } else if (resolveResultCount == 1) {
                        ResolveResult resolveResult = resolveResults[0];

                        PsiElement nextResolved = resolveResult.getElement();

                        if (nextResolved != null && nextResolved instanceof Call && isModular((Call) nextResolved)) {
                            fullyResolved = nextResolved;
                            break;
                        }

                        if (nextResolved == null || nextResolved.isEquivalentTo(currentResolved)) {
                            fullyResolved = currentResolved;
                            break;
                        } else {
                            currentResolved = nextResolved;
                        }
                    } else {
                        PsiElement nextResolved = null;

                        for (ResolveResult resolveResult : resolveResults) {
                            PsiElement resolveResultElement = resolveResult.getElement();

                            if (resolveResultElement != null &&
                                    resolveResultElement instanceof Call &&
                                    isModular((Call) resolveResultElement)) {
                                nextResolved = resolveResultElement;

                                break;
                            }
                        }

                        if (nextResolved == null) {
                            fullyResolved = currentResolved;
                        } else {
                            fullyResolved = nextResolved;
                        }

                        break;
                    }
                } else {
                    PsiElement nextResolved = reference.resolve();

                    if (nextResolved == null || nextResolved.isEquivalentTo(currentResolved)) {
                        fullyResolved = currentResolved;
                        break;
                    } else {
                        currentResolved = nextResolved;
                    }
                }
            } else {
                fullyResolved = currentResolved;

                break;
            }

            reference = null;
        } while (true);

        return fullyResolved;
    }

    @Contract(pure = true)
    @Nullable
    public static String functionName(@NotNull final Call call) {
        String functionName = null;
        PsiElement element = call.functionNameElement();

        if (element != null) {
            functionName = computeReadAction(element::getText);
        }

        return functionName;
    }

    /**
     *
     * @param atUnqualifiedNoParenthesesCall
     * @return `null` because the `IDENTIFIER`, `foo` in `@foo 1` is not the local name of a function, but the name of a
     *   Module attribute.
     */
    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement functionNameElement(@NotNull @SuppressWarnings("unused") final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        return null;
    }

    /**
     *
     * @param dotCall
     * @return `null` because the expression before the `.` is a variable name and not a function name.
     */
    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement functionNameElement(@NotNull @SuppressWarnings("unused") final DotCall dotCall) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement functionNameElement(@NotNull final NotIn notIn) {
        return null;
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement functionNameElement(@NotNull final Operation operation) {
        return operation.operator();
    }

    public static PsiElement functionNameElement(@NotNull final Qualified qualified) {
        return qualified.getRelativeIdentifier();
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement functionNameElement(@NotNull final Unqualified unqualified) {
        return unqualified.getFirstChild();
    }

    public static Body getBody(ElixirCharListHeredocLine charListHeredocLine) {
        return charListHeredocLine.getQuoteCharListBody();
    }

    @NotNull
    public static Body getBody(@NotNull final ElixirCharListLine charListLine) {
        return charListLine.getQuoteCharListBody();
    }

    public static Body getBody(ElixirInterpolatedCharListHeredocLine interpolatedCharListHeredocLine) {
        return interpolatedCharListHeredocLine.getInterpolatedCharListBody();
    }

    public static Body getBody(ElixirInterpolatedCharListSigilLine interpolatedCharListSigilLine) {
        return interpolatedCharListSigilLine.getInterpolatedCharListBody();
    }

    public static Body getBody(ElixirInterpolatedRegexHeredocLine interpolatedRegexHeredocLine) {
        return interpolatedRegexHeredocLine.getInterpolatedRegexBody();
    }

    public static Body getBody(ElixirInterpolatedRegexLine interpolatedRegexLine) {
        return interpolatedRegexLine.getInterpolatedRegexBody();
    }

    public static Body getBody(ElixirInterpolatedSigilHeredocLine sigilHeredocLine) {
        return sigilHeredocLine.getInterpolatedSigilBody();
    }

    public static Body getBody(ElixirInterpolatedSigilLine interpolatedSigilLine) {
        return interpolatedSigilLine.getInterpolatedSigilBody();
    }

    public static Body getBody(ElixirInterpolatedStringHeredocLine stringHeredocLine) {
        return stringHeredocLine.getInterpolatedStringBody();
    }

    public static Body getBody(ElixirInterpolatedStringSigilLine interpolatedStringSigilLine) {
        return interpolatedStringSigilLine.getInterpolatedStringBody();
    }

    public static Body getBody(ElixirInterpolatedWordsHeredocLine wordsHeredocLine) {
        return wordsHeredocLine.getInterpolatedWordsBody();
    }

    public static Body getBody(ElixirInterpolatedWordsLine interpolatedWordsLine) {
        return interpolatedWordsLine.getInterpolatedWordsBody();
    }

    public static Body getBody(ElixirLiteralCharListHeredocLine charListHeredocLine) {
        return charListHeredocLine.getLiteralCharListBody();
    }

    public static Body getBody(ElixirLiteralCharListSigilLine literalCharListLine) {
        return literalCharListLine.getLiteralCharListBody();
    }

    public static Body getBody(ElixirLiteralRegexHeredocLine literalRegexHeredocLine) {
        return literalRegexHeredocLine.getLiteralRegexBody();
    }

    public static Body getBody(ElixirLiteralRegexLine literalRegexLine) {
        return literalRegexLine.getLiteralRegexBody();
    }

    public static Body getBody(ElixirLiteralSigilHeredocLine literalSigilHeredocLine) {
        return literalSigilHeredocLine.getLiteralSigilBody();
    }

    public static Body getBody(ElixirLiteralSigilLine literalSigilLine) {
        return literalSigilLine.getLiteralSigilBody();
    }

    public static Body getBody(ElixirLiteralStringHeredocLine literalStringSigilHeredocLine) {
        return literalStringSigilHeredocLine.getLiteralStringBody();
    }

    public static Body getBody(ElixirLiteralStringSigilLine literalStringSigilLine) {
        return literalStringSigilLine.getLiteralStringBody();
    }

    public static Body getBody(ElixirLiteralWordsHeredocLine literalWordsHeredocLine) {
        return literalWordsHeredocLine.getLiteralWordsBody();
    }

    public static Body getBody(ElixirLiteralWordsLine literalWordsLine) {
        return literalWordsLine.getLiteralWordsBody();
    }

    public static Body getBody(ElixirStringHeredocLine stringHeredocLine) {
        return stringHeredocLine.getQuoteStringBody();
    }

    public static Body getBody(@NotNull final ElixirStringLine stringLine) {
        return stringLine.getQuoteStringBody();
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static ElixirDoBlock getDoBlock(@NotNull @SuppressWarnings("unused") final ElixirUnqualifiedNoParenthesesManyArgumentsCall unqualifiedNoParenthesesManyArgumentsCall) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static ElixirDoBlock getDoBlock(@NotNull @SuppressWarnings("unused") final NotIn notIn) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static ElixirDoBlock getDoBlock(@NotNull @SuppressWarnings("unused") final Operation operation) {
        return null;
    }

    @Nullable
    public static ElixirDoBlock getDoBlock(@SuppressWarnings("unused") MatchedCall matchedCall) {
        return null;
    }

    public static IElementType getFragmentType(@SuppressWarnings("unused") CharListFragmented charListFragmented) {
        return ElixirTypes.CHAR_LIST_FRAGMENT;
    }

    public static IElementType getFragmentType(@SuppressWarnings("unused") RegexFragmented regexFragmented) {
        return ElixirTypes.REGEX_FRAGMENT;
    }

    public static IElementType getFragmentType(@SuppressWarnings("unused") SigilFragmented sigilFragmented) {
        return ElixirTypes.SIGIL_FRAGMENT;
    }

    public static IElementType getFragmentType(@SuppressWarnings("unused") StringFragmented stringFragmented) {
        return ElixirTypes.STRING_FRAGMENT;
    }

    public static IElementType getFragmentType(@SuppressWarnings("unused") WordsFragmented wordsFragmented) {
        return ElixirTypes.WORDS_FRAGMENT;
    }

    /**
     * Returns the scope in which references to this element are searched.
     *
     * @return the search scope instance.
     * @see {@link com.intellij.psi.search.PsiSearchHelper#getUseScope(PsiElement)}
     */
    @NotNull
    @Contract(pure=true)
    static SearchScope getUseScope(AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        SearchScope useScope;

        if (isNonReferencing(atUnqualifiedNoParenthesesCall.getAtIdentifier())) {
            useScope = moduleWithDependentsScope(atUnqualifiedNoParenthesesCall);
        } else {
            useScope = followingSiblingsSearchScope(atUnqualifiedNoParenthesesCall);
        }

        return useScope;
    }

    /**
     * Returns the scope in which references to this element are searched.
     *
     * @return the search scope instance.
     * @see {@link com.intellij.psi.search.PsiSearchHelper#getUseScope(PsiElement)}
     */
    @NotNull
    @Contract(pure=true)
    static SearchScope getUseScope(UnqualifiedNoArgumentsCall unqualifiedNoArgumentsCall) {
        SearchScope useScope;

        if (isBitStreamSegmentOption(unqualifiedNoArgumentsCall)) {
            // Bit Stream Segment Options aren't variables or even real functions, so no use scope
            useScope = LocalSearchScope.EMPTY;
        } else if (isParameter(unqualifiedNoArgumentsCall) || isParameterWithDefault(unqualifiedNoArgumentsCall)) {
            PsiElement ancestor = unqualifiedNoArgumentsCall.getParent();

            while (true) {
                if (ancestor instanceof Call) {
                    Call ancestorCall = (Call) ancestor;

                    if (CallDefinitionClause.is(ancestorCall)) {
                        PsiElement macroDefinitionClause = macroDefinitionClauseForArgument(ancestorCall);

                        if (macroDefinitionClause != null) {
                            ancestor = macroDefinitionClause;
                        }

                        break;
                    } else if (Delegation.is(ancestorCall)) {
                        break;
                    } else if (ancestorCall.hasDoBlockOrKeyword()) {
                        break;
                    }
                } else if (ancestor instanceof ElixirStabOperation) {
                    break;
                } else if (ancestor instanceof PsiFile) {
                    error(
                            UnqualifiedNoArgumentsCall.class,
                            "Use scope for parameter not found before reaching file scope",
                            unqualifiedNoArgumentsCall
                    );
                    break;
                }

                ancestor = ancestor.getParent();
            }

            useScope = new LocalSearchScope(ancestor);
        } else if (isVariable(unqualifiedNoArgumentsCall)) {
            useScope = variableUseScope(unqualifiedNoArgumentsCall);
        } else {
            // if the type of callable isn't known, fallback to default scope
            useScope = moduleWithDependentsScope(unqualifiedNoArgumentsCall);
        }

        return useScope;
    }

    public static List<HeredocLine> getHeredocLineList(InterpolatedCharListHeredocLined interpolatedCharListHeredocLined) {
        List<ElixirInterpolatedCharListHeredocLine> interpolatedCharListHeredocLineList = interpolatedCharListHeredocLined.getInterpolatedCharListHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(interpolatedCharListHeredocLineList.size());

        for (HeredocLine heredocLine : interpolatedCharListHeredocLineList) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(InterpolatedStringHeredocLined interpolatedStringHeredocLined) {
        List<ElixirInterpolatedStringHeredocLine> interpolatedStringHeredocLineList = interpolatedStringHeredocLined.getInterpolatedStringHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(interpolatedStringHeredocLineList.size());

        for (HeredocLine heredocLine : interpolatedStringHeredocLineList) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirCharListHeredoc charListHeredoc) {
        List<ElixirCharListHeredocLine> charListHeredocLines = charListHeredoc.getCharListHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(charListHeredocLines.size());

        for (HeredocLine heredocLine : charListHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirInterpolatedRegexHeredoc interpolatedRegexHeredoc) {
        List<ElixirInterpolatedRegexHeredocLine> interpolatedRegexHeredocLines = interpolatedRegexHeredoc.getInterpolatedRegexHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(interpolatedRegexHeredocLines.size());

        for (HeredocLine heredocLine : interpolatedRegexHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirInterpolatedSigilHeredoc interpolatedSigilHeredoc) {
        List<ElixirInterpolatedSigilHeredocLine> interpolatedSigilHeredocLines = interpolatedSigilHeredoc.getInterpolatedSigilHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(interpolatedSigilHeredocLines.size());

        for (HeredocLine heredocLine : interpolatedSigilHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirInterpolatedWordsHeredoc interpolatedWordsHeredoc) {
        List<ElixirInterpolatedWordsHeredocLine> interpolatedWordsHeredocLines = interpolatedWordsHeredoc.getInterpolatedWordsHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(interpolatedWordsHeredocLines.size());

        for (HeredocLine heredocLine : interpolatedWordsHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirLiteralCharListSigilHeredoc literalCharListSigilHeredoc) {
        List<ElixirLiteralCharListHeredocLine> literalCharListHeredocLines = literalCharListSigilHeredoc.getLiteralCharListHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(literalCharListHeredocLines.size());

        for (HeredocLine heredocLine : literalCharListHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirLiteralRegexHeredoc literalRegexSigilHeredoc) {
        List<ElixirLiteralRegexHeredocLine> literalRegexHeredocLines = literalRegexSigilHeredoc.getLiteralRegexHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(literalRegexHeredocLines.size());

        for (HeredocLine heredocLine : literalRegexHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirLiteralSigilHeredoc literalSigilHeredoc) {
        List<ElixirLiteralSigilHeredocLine> literalSigilHeredocLines = literalSigilHeredoc.getLiteralSigilHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(literalSigilHeredocLines.size());

        for (HeredocLine heredocLine : literalSigilHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirLiteralStringSigilHeredoc literalStringSigilHeredoc) {
        List<ElixirLiteralStringHeredocLine> literalStringHeredocLines = literalStringSigilHeredoc.getLiteralStringHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(literalStringHeredocLines.size());

        for (HeredocLine heredocLine : literalStringHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirLiteralWordsHeredoc literalWordsSigilHeredoc) {
        List<ElixirLiteralWordsHeredocLine> literalWordsHeredocLines = literalWordsSigilHeredoc.getLiteralWordsHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(literalWordsHeredocLines.size());

        for (HeredocLine heredocLine : literalWordsHeredocLines) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static List<HeredocLine> getHeredocLineList(ElixirStringHeredoc stringHeredoc) {
        List<ElixirStringHeredocLine> stringHeredocLineList = stringHeredoc.getStringHeredocLineList();
        List<HeredocLine> heredocLineList = new ArrayList<HeredocLine>(stringHeredocLineList.size());

        for (HeredocLine heredocLine : stringHeredocLineList) {
            heredocLineList.add(heredocLine);
        }

        return heredocLineList;
    }

    public static Quotable getKeywordValue(ElixirKeywordPair keywordPair) {
        PsiElement[] children = keywordPair.getChildren();

        assert children.length == 2;

        return (Quotable) children[1];
    }

    public static Quotable getKeywordValue(ElixirNoParenthesesKeywordPair noParenthesesKeywordPair) {
        PsiElement[] children = noParenthesesKeywordPair.getChildren();

        assert children.length == 2;

        return (Quotable) children[1];
    }

    @NotNull
    public static String getName(@NotNull ElixirAlias alias) {
        return alias.getText();
    }

    @NotNull
    public static String getName(@NotNull QualifiedAlias qualifiedAlias) {
        return qualifiedAlias.getText();
    }

    @Contract(pure = true)
    @Nullable
    public static String getName(@NotNull NamedElement namedElement) {
        PsiElement nameIdentifier = namedElement.getNameIdentifier();
        String name = null;

        if (nameIdentifier != null) {
            name = unquoteName(namedElement, nameIdentifier.getText());
        } else {
            if (namedElement instanceof Call) {
                Call call = (Call) namedElement;

                /* The name of the module defined by {@code defimpl PROTOCOL[ for: MODULE]} is derived by combining the
                   PROTOCOL and MODULE name into PROTOCOL.MODULE.  Neither piece is really the "name" or
                   "nameIdentifier" element of the implementation because changing the PROTOCOL make the implementation
                   just for that different Protocol and changing the MODULE makes the implementation for a different
                   MODULE.  If `for:` isn't given, it's really the enclosing {@code defmodule MODULE} whose name should
                   be changed. */
                if (Implementation.is(call)) {
                    name = Implementation.name(call);
                }
            }
        }

        return name;
    }

    @Nullable
    public static PsiElement getNameIdentifier(@NotNull ElixirAlias alias) {
        PsiElement parent = alias.getParent();
        PsiElement nameIdentifier = null;

        if (parent instanceof ElixirAccessExpression) {
            PsiElement grandParent = parent.getParent();

            // alias is first alias in chain of qualifiers
            if (grandParent instanceof QualifiableAlias) {
                QualifiableAlias grandParentQualifiableAlias = (QualifiableAlias) grandParent;
                nameIdentifier = grandParentQualifiableAlias.getNameIdentifier();
            } else {
                /* NamedStubbedPsiElementBase#getTextOffset assumes getNameIdentifier is null when the NameIdentifier is
                   the element itself. */
                // alias is single, unqualified alias
                nameIdentifier = null;
            }
        } else if (parent instanceof QualifiableAlias) {
            // alias is last in chain of qualifiers
            QualifiableAlias parentQualifiableAlias = (QualifiableAlias) parent;
            nameIdentifier = parentQualifiableAlias.getNameIdentifier();
        }

        return nameIdentifier;
    }

    @Nullable
    public static PsiElement getNameIdentifier(@NotNull ElixirKeywordKey keywordKey) {
        ElixirCharListLine charListLine = keywordKey.getCharListLine();
        PsiElement nameIdentifier;

        if (charListLine != null) {
            nameIdentifier = null;
        } else {
            ElixirStringLine stringLine = keywordKey.getStringLine();

            if (stringLine != null) {
                nameIdentifier = null;
            } else {
                nameIdentifier = keywordKey;
            }
        }

        return nameIdentifier;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement getNameIdentifier(@NotNull ElixirVariable variable) {
        return variable;
    }

    public static PsiElement getNameIdentifier(
            @NotNull org.elixir_lang.psi.call.Named named
    ) {
        PsiElement nameIdentifier;

        /* can't be a {@code public static PsiElement getNameIdentifier(@NotNull Operation operation)} because it leads
           to "reference to getNameIdentifier is ambiguous" */
        if (named instanceof Operation) {
            Operation operation = (Operation) named;
            nameIdentifier = operation.operator();
        } else if (CallDefinitionClause.is(named)) {
            nameIdentifier = CallDefinitionClause.nameIdentifier(named);
        } else if (CallDefinitionSpecification.is(named)) {
            nameIdentifier = CallDefinitionSpecification.nameIdentifier(named);
        } else if (Callback.is(named)) {
            nameIdentifier = Callback.nameIdentifier(named);
        } else if (Implementation.is(named)) {
            /* have to set to null so that {@code else} clause doesn't return the {@code defimpl} element as the name
               identifier */
            nameIdentifier = null;
        } else if (Module.is(named)) {
            nameIdentifier = Module.nameIdentifier(named);
        } else if (Protocol.is(named)) {
            nameIdentifier = Protocol.nameIdentifier(named);
        } else if (named instanceof AtUnqualifiedNoParenthesesCall) { // module attribute
            AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall = (AtUnqualifiedNoParenthesesCall) named;
            nameIdentifier = atUnqualifiedNoParenthesesCall.getAtIdentifier();
        } else {
            nameIdentifier = named.functionNameElement();
        }

        return nameIdentifier;
    }

    /**
     *
     * @param qualifiableAlias
     * @return null if qualifiableAlias is its own name identifier
     * @return PsiElement if qualifiableAlias is a subalias of a bigger qualified alias
     */
    @Nullable
    public static PsiElement getNameIdentifier(@NotNull QualifiableAlias qualifiableAlias) {
        PsiElement parent = qualifiableAlias.getParent();
        PsiElement nameIdentifier;

        if (parent instanceof QualifiableAlias) {
            QualifiableAlias parentQualifiedAlias = (QualifiableAlias) parent;

            nameIdentifier = parentQualifiedAlias.getNameIdentifier();
        } else {
            nameIdentifier = null;
        }

        return nameIdentifier;
    }

    @Contract(pure = true)
    @NotNull
    public static ItemPresentation getPresentation(@NotNull final Call call) {
        final String text = UsageViewUtil.createNodeText(call);

        return new ItemPresentation() {
            @Nullable
            public String getPresentableText() {
                return text;
            }

            @Nullable
            public String getLocationString() {
                return call.getContainingFile().getName();
            }

            @Nullable
            public Icon getIcon(boolean b) {
                return call.getIcon(0);
            }
        };
    }

    @Contract(pure = true)
    @Nullable
    public static ItemPresentation getPresentation(@NotNull final ElixirIdentifier identifier) {
        Parameter parameter = new Parameter(identifier);
        Parameter parameterizedParameter = Parameter.putParameterized(parameter);
        ItemPresentation itemPresentation = null;

        if ((parameterizedParameter.type == Parameter.Type.FUNCTION_NAME ||
                parameterizedParameter.type == Parameter.Type.MACRO_NAME) &&
                parameterizedParameter.parameterized != null) {
            final NavigatablePsiElement parameterized = parameterizedParameter.parameterized;

            if (parameterized instanceof Call) {
                CallDefinitionClause callDefinitionClause = CallDefinitionClause.fromCall((Call) parameterized);

                if (callDefinitionClause != null) {
                    itemPresentation = callDefinitionClause.getPresentation();
                }
            }
        }

        return itemPresentation;
    }

    @Nullable
    private static PsiReference computeReference(@NotNull Call call) {
        PsiReference reference = null;

        /* if the call is just the identifier for a module attribute reference, then don't return a Callable reference,
           and instead let {@link #getReference(AtNonNumbericOperation) handle it */
        if (!(call instanceof UnqualifiedNoArgumentsCall && call.getParent() instanceof AtNonNumericOperation) &&
                // if a bitstring segment option then the option is a pseudo-function
                !isBitStreamSegmentOption(call)) {
            PsiElement parent = call.getParent();

            if (parent instanceof Type) {
                PsiElement grandParent = parent.getParent();
                AtUnqualifiedNoParenthesesCall moduleAttribute = null;
                PsiElement maybeArgument = grandParent;

                if (grandParent instanceof When) {
                    maybeArgument = grandParent.getParent();
                }

                if (maybeArgument instanceof ElixirNoParenthesesOneArgument) {
                    PsiElement maybeModuleAttribute = maybeArgument.getParent();

                    if (maybeModuleAttribute instanceof AtUnqualifiedNoParenthesesCall) {
                        moduleAttribute = (AtUnqualifiedNoParenthesesCall) maybeModuleAttribute;
                    }

                    if (moduleAttribute != null) {
                        String name = moduleAttributeName(moduleAttribute);

                        if (name.equals("@spec")) {
                            reference = new org.elixir_lang.reference.CallDefinitionClause(call, moduleAttribute);
                        }
                    }
                }
            }

            if (reference == null) {
                if (CallDefinitionClause.is(call) || Implementation.is(call) || Module.is(call) || Protocol.is(call)) {
                    reference = Callable.definer(call);
                } else {
                    reference = new Callable(call);
                }
            }
        }

        return reference;
    }

    @Nullable
    public static PsiReference getReference(@NotNull Call call) {
        return CachedValuesManager.getCachedValue(
                call,
                () -> CachedValueProvider.Result.create(computeReference(call), call)
        );
    }

    @Nullable
    private static PsiPolyVariantReference computeReference(@NotNull QualifiableAlias qualifiableAlias,
                                                            @NotNull PsiElement maxScope) {
        PsiPolyVariantReference reference = null;

        if (isOutermostQualifiableAlias(qualifiableAlias)) {
            reference = new org.elixir_lang.reference.Module(qualifiableAlias, maxScope);
        }

        return reference;
    }

    @Nullable
    public static PsiReference getReference(@NotNull ElixirAtom atom) {
        return CachedValuesManager.getCachedValue(
                atom,
                () -> CachedValueProvider.Result.create(computeReference(atom), atom)
        );
    }

    @NotNull
    private static PsiReference computeReference(@NotNull ElixirAtom atom) {
        return new org.elixir_lang.reference.Atom(atom);
    }

    @Nullable
    public static PsiReference getReference(@NotNull QualifiableAlias qualifiableAlias) {
        return getReference(qualifiableAlias, qualifiableAlias.getContainingFile());
    }

    @Nullable
    public static PsiPolyVariantReference getReference(@NotNull QualifiableAlias qualifiableAlias,
                                                       @NotNull PsiElement maxScope) {
        return CachedValuesManager.getCachedValue(
                qualifiableAlias,
                () -> CachedValueProvider.Result.create(computeReference(qualifiableAlias, maxScope), qualifiableAlias)
        );
    }

    @NotNull
    private static PsiReference computeReference(@NotNull final ElixirAtIdentifier atIdentifier) {
        return new org.elixir_lang.reference.ModuleAttribute(atIdentifier);
    }

    /**
     * <blockquote>
     *     The PSI element at the cursor (the direct tree parent of the token at the cursor position) must be either a
     *     PsiNamedElement or <em>a PsiReference which resolves to a PsiNamedElement.</em>
     * </blockquote>
     * @param atIdentifier
     * @return
     * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support/find_usages.html?search=PsiNameIdentifierOwner">IntelliJ Platform SDK DevGuide | Find Usages</a>
     */
    @Contract(pure = true)
    @NotNull
    public static PsiReference getReference(@NotNull final ElixirAtIdentifier atIdentifier) {
        return CachedValuesManager.getCachedValue(
                atIdentifier,
                () -> CachedValueProvider.Result.create(computeReference(atIdentifier), atIdentifier)
        );
    }

    @Nullable
    private static PsiReference computeReference(@NotNull final AtNonNumericOperation atNonNumericOperation) {
        PsiReference reference = null;

        if (!isNonReferencing(atNonNumericOperation)) {
            reference = new org.elixir_lang.reference.ModuleAttribute(atNonNumericOperation);
        }

        return reference;
    }

    @Nullable
    public static PsiReference getReference(@NotNull final AtNonNumericOperation atNonNumericOperation) {
        return CachedValuesManager.getCachedValue(
                atNonNumericOperation,
                () -> CachedValueProvider.Result.create(computeReference(atNonNumericOperation), atNonNumericOperation)
        );
    }

    public static boolean hasDoBlockOrKeyword(@NotNull final Call call) {
        return call.getDoBlock() != null || keywordArgument(call, "do") != null;
    }

    public static boolean hasDoBlockOrKeyword(@NotNull final StubBased<Stub> stubBased) {
        Stub stub = stubBased.getStub();
        boolean has;

        if (stub != null) {
            has = stub.hasDoBlockOrKeyword();
        } else {
            has = hasDoBlockOrKeyword((Call) stubBased);
        }

        return has;
    }

    public static boolean hasKeywordKey(@NotNull QuotableKeywordPair quotableKeywordPair, @NotNull String keywordKeyText) {
        Quotable keywordKey = quotableKeywordPair.getKeywordKey();
        boolean has = false;

        if (computeReadAction(keywordKey::getText).equals(keywordKeyText)) {
            has = true;
        } else {
            OtpErlangObject quotedKeywordKey = keywordKey.quote();

            if (quotedKeywordKey instanceof OtpErlangAtom) {
                OtpErlangAtom keywordKeyAtom = (OtpErlangAtom) quotedKeywordKey;

                if (keywordKeyAtom.atomValue().equals(keywordKeyText)) {
                    has = true;
                }
            }
        }

        return has;
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement qualifier(@NotNull final Qualified qualified) {
        return qualified.getFirstChild();
    }

    public static List<QuotableKeywordPair> quotableKeywordPairList(ElixirKeywords keywords) {
        return new ArrayList<QuotableKeywordPair>(keywords.getKeywordPairList());
    }

    public static List<QuotableKeywordPair> quotableKeywordPairList(ElixirNoParenthesesKeywords noParenthesesKeywords) {
        return new ArrayList<QuotableKeywordPair>(noParenthesesKeywords.getNoParenthesesKeywordPairList());
    }

    @NotNull
    public static OtpErlangObject quote(@NotNull ElixirDecimalFloat decimalFloat) {
        OtpErlangObject quoted;

        ElixirDecimalFloatIntegral decimalFloatIntegral = decimalFloat.getDecimalFloatIntegral();
        ElixirDecimalWholeNumber integralDecimalWholeNumber = decimalFloatIntegral.getDecimalWholeNumber();
        List<Digits> integralDigitsList = integralDecimalWholeNumber.digitsList();
        String integralString = compactDigits(integralDigitsList);

        ElixirDecimalFloatFractional decimalFloatFractional = decimalFloat.getDecimalFloatFractional();
        ElixirDecimalWholeNumber fractionalDecimalWholeNumber = decimalFloatFractional.getDecimalWholeNumber();
        List<Digits> fractionalDigitsList = fractionalDecimalWholeNumber.digitsList();
        String fractionalString = compactDigits(fractionalDigitsList);

        ElixirDecimalFloatExponent decimalFloatExponent = decimalFloat.getDecimalFloatExponent();

        if (decimalFloatExponent != null) {
            ElixirDecimalWholeNumber exponentDecimalWholeNumber = decimalFloatExponent.getDecimalWholeNumber();
            List<Digits> exponentDigitsList = exponentDecimalWholeNumber.digitsList();
            String exponentSignString = decimalFloatExponent.getDecimalFloatExponentSign().getText();
            String exponentString = compactDigits(exponentDigitsList);

            String floatString = String.format(
                    "%s.%se%s%s",
                    integralString,
                    fractionalString,
                    exponentSignString,
                    exponentString
            );

            if (inBase(integralDigitsList) && inBase(fractionalDigitsList) && inBase(exponentDigitsList)) {
                Double parsedDouble = Double.parseDouble(floatString);
                quoted = new OtpErlangDouble(parsedDouble);
            } else {
                // Convert parser error to runtime ArgumentError
                quoted = quotedFunctionCall(
                        "String",
                        "to_float",
                        metadata(decimalFloat),
                        elixirString(floatString)
                );
            }
        } else {
            String floatString = String.format(
                    "%s.%s",
                    integralString,
                    fractionalString
            );

            if (inBase(integralDigitsList) && inBase(fractionalDigitsList)) {
                Double parsedDouble = Double.parseDouble(floatString);
                quoted = new OtpErlangDouble(parsedDouble);
            } else {
                // Convert parser error to runtime ArgumentError
                quoted = quotedFunctionCall(
                        "String",
                        "to_float",
                        metadata(decimalFloat),
                        elixirString(floatString)
                );
            }
        }

        return quoted;
    }

    public static OtpErlangObject quote(@SuppressWarnings("unused") ElixirEmptyParentheses emptyParentheses) {
        return new OtpErlangAtom("nil");
    }

    public static OtpErlangObject quote(ElixirFile file) {
        final Deque<OtpErlangObject> quotedChildren = new LinkedList<OtpErlangObject>();

        file.acceptChildren(
                new PsiElementVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {
                        if (element instanceof Quotable) {
                            visitQuotable((Quotable) element);
                        } else if (!isUnquoted(element)) {
                            throw new NotImplementedException("Don't know how to visit " + element);
                        }

                        super.visitElement(element);
                    }

                    public void visitQuotable(@NotNull Quotable child) {
                        final OtpErlangObject quotedChild = child.quote();
                        quotedChildren.add(quotedChild);
                    }
                }
        );

        // @see https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L76-L79
        return toBlock(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final WholeNumber wholeNumber) {
        List<Digits> digitsList = wholeNumber.digitsList();

        OtpErlangObject quoted;

        if (inBase(digitsList)) {
            StringBuilder stringBuilder = new StringBuilder();

            for (Digits digits : digitsList) {
                stringBuilder.append(digits.getText());
            }

            quoted = quoteInBase(stringBuilder.toString(), wholeNumber.base());
        } else {
            /* 0 elements is invalid in native Elixir and can be emulated as `String.to_integer("", base)` while
               2 elements implies at least one element is invalidDigitsElementType which is invalid in native Elixir and
               can be emulated as String.to_integer(<all-digits>, base) so that it raises an ArgumentError on the invalid
               digits */
            StringBuilder stringBuilder = new StringBuilder();

            for (Digits digits : digitsList) {
                stringBuilder.append(digits.getText());
            }

            quoted = quotedFunctionCall(
                    "String",
                    "to_integer",
                    metadata(wholeNumber),
                    elixirString(
                            stringBuilder.toString()
                    ),
                    new OtpErlangLong(wholeNumber.base())
            );
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteAsAtom(@NotNull final ElixirCharListLine charListLine) {
        OtpErlangObject quoted = charListLine.quote();
        OtpErlangObject quotedAsAtom;

        if (quoted instanceof OtpErlangString) {
            final String atomText = ((OtpErlangString) quoted).stringValue();
            quotedAsAtom = new OtpErlangAtom(atomText);
        } else {
            final OtpErlangTuple quotedStringToCharListCall = (OtpErlangTuple) quoted;
            final OtpErlangList quotedStringToCharListArguments = (OtpErlangList) quotedStringToCharListCall.elementAt(2);
            final OtpErlangObject binaryConstruction = quotedStringToCharListArguments.getHead();

            quotedAsAtom = quotedFunctionCall(
                    "erlang",
                    "binary_to_atom",
                    (OtpErlangList) quotedStringToCharListCall.elementAt(1),
                    binaryConstruction,
                    UTF_8
            );
        }

        return quotedAsAtom;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteAsAtom(@NotNull final ElixirStringLine stringLine) {
        OtpErlangObject quoted = stringLine.quote();
        OtpErlangObject quotedAsAtom;

        if (quoted instanceof OtpErlangBinary) {
            String atomText = javaString((OtpErlangBinary) quoted);
            quotedAsAtom = new OtpErlangAtom(atomText);
        } else if (quoted instanceof OtpErlangString) {
            String atomText = ((OtpErlangString) quoted).stringValue();
            quotedAsAtom = new OtpErlangAtom(atomText);
        } else {
            quotedAsAtom = quotedFunctionCall(
                    "erlang",
                    "binary_to_atom",
                    metadata(stringLine),
                    quoted,
                    UTF_8
            );
        }

        return quotedAsAtom;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirIdentifier identifier) {
        return new OtpErlangAtom(identifier.getText());
    }

    /* "#{a}" is transformed to "<<Kernel.to_string(a) :: binary>>" in
     * `"\"\#{a}\"" |> Code.string_to_quoted |> Macro.to_string`, so interpolation has to be represented as a type call
     * (`:::`) to binary of a call of `Kernel.to_string`
     */
    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirInterpolation interpolation) {
        OtpErlangObject quotedChildren = quote(interpolation.getChildren());
        OtpErlangList interpolationMetadata = metadata(interpolation);

        OtpErlangObject quotedKernelToStringCall = quotedFunctionCall(
                prependElixirPrefix(KERNEL),
                "to_string",
                interpolationMetadata,
                quotedChildren
        );
        OtpErlangObject quotedBinaryCall = quotedVariable(
                "binary",
                interpolationMetadata
        );
        OtpErlangObject quotedType = quotedFunctionCall(
                "::",
                interpolationMetadata,
                quotedKernelToStringCall,
                quotedBinaryCall
        );

        return quotedType;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(ElixirRelativeIdentifier relativeIdentifier) {
        PsiElement[] children = relativeIdentifier.getChildren();
        OtpErlangObject quotedRelativeIdentifier;

        // Only tokens
        if (children.length == 0) {
            ASTNode relativeIdentifierNode = relativeIdentifier.getNode();
            // take first node to avoid SIGNIFICANT_WHITE_SPACE after DUAL_OPERATOR
            ASTNode operatorNode = relativeIdentifierNode.getFirstChildNode();
            quotedRelativeIdentifier = new OtpErlangAtom(operatorNode.getText());
        } else {
            assert children.length == 1;

            PsiElement child = children[0];

            if (child instanceof Atomable) {
                Atomable atomable = (Atomable) child;
                quotedRelativeIdentifier = atomable.quoteAsAtom();
            } else {
                Quotable quotable = (Quotable) child;
                quotedRelativeIdentifier = quotable.quote();
            }
        }

        return quotedRelativeIdentifier;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(ElixirSigilModifiers sigilModifiers) {
        String sigilModifiersText = sigilModifiers.getText();
        List<Integer> codePoints = new ArrayList<Integer>(sigilModifiersText.length());
        OtpErlangObject quoted;

        for (int i = 0; i < sigilModifiersText.length(); i++) {
            int codePoint = sigilModifiersText.codePointAt(i);
            codePoints.add(codePoint);
        }

        if (codePoints.size() == 0) {
            quoted = new OtpErlangList();
        } else {
            quoted = elixirCharList(codePoints);
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(ElixirStab stab) {
        Deque<OtpErlangObject> quotedChildren = new ArrayDeque<OtpErlangObject>();

        ElixirStabBody stabBody = stab.getStabBody();
        OtpErlangObject quoted;

        if (stabBody != null) {
            // TODO port quote(ElixirStabExpression)'s unary handling
            quoted = stabBody.quote();
        } else {
            List<ElixirStabOperation> stabOperationList = stab.getStabOperationList();

            for (ElixirStabOperation stabOperation : stabOperationList) {
                quotedChildren.add(stabOperation.quote());
            }

            int size = quotedChildren.size();

            OtpErlangObject[] quotedArray = new OtpErlangObject[size];
            quotedArray = quotedChildren.toArray(quotedArray);
            quoted = new OtpErlangList(quotedArray);

        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirStabBody stabBody) {
        PsiElement[] children = stabBody.getChildren();
        Deque<OtpErlangObject> quotedChildren = new ArrayDeque<OtpErlangObject>();

        for (PsiElement child : children) {
            // skip endOfExpression
            if (isUnquoted(child)) {
                continue;
            }

            Quotable quotableChild = (Quotable) child;
            OtpErlangObject quotedChild = quotableChild.quote();
            quotedChildren.add(quotedChild);
        }

        return buildBlock(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirStabNoParenthesesSignature stabNoParenthesesSignature) {
        QuotableArguments noParenthesesArguments = stabNoParenthesesSignature.getNoParenthesesArguments();
        OtpErlangObject[] quotedArguments = noParenthesesArguments.quoteArguments();

        // https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L277
        OtpErlangObject[] unwrappedWhen = unwrapWhen(quotedArguments);
        OtpErlangList quotedArgumentList = new OtpErlangList(unwrappedWhen);

        return elixirCharList(quotedArgumentList);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(ElixirStabOperation stabOperation) {
        Operator operator = stabOperation.operator();
        OtpErlangObject quotedOperator = operator.quote();

        return quotedFunctionCall(
                quotedOperator,
                metadata(operator),
                quotedLeftOperand(stabOperation),
                quotedRightOperand(stabOperation)
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirStabParenthesesSignature stabParenthesesSignature) {
        PsiElement[] children = stabParenthesesSignature.getChildren();
        OtpErlangObject[] quotedListElements;

        assert children.length >= 1;

        // stabParenthesesManyArguments ...
        QuotableArguments quotableArguments = (QuotableArguments) children[0];
        OtpErlangObject[] quotedArguments = quotableArguments.quoteArguments();

        // stabParenthesesManyArguments WHEN_OPERATOR expression
        if (children.length > 1) {
            assert children.length == 3;

            Operator operator = (Operator) children[1];
            OtpErlangObject quotedOperator = operator.quote();

            Quotable guard = (Quotable) children[2];
            OtpErlangObject quotedGuard = guard.quote();

            OtpErlangObject[] quotedWhenArguments = new OtpErlangObject[quotedArguments.length + 1];

            System.arraycopy(quotedArguments, 0, quotedWhenArguments, 0, quotedArguments.length);
            quotedWhenArguments[quotedWhenArguments.length - 1] = quotedGuard;

            OtpErlangObject quotedWhenOperation = quotedFunctionCall(
                    quotedOperator,
                    metadata(operator),
                    quotedWhenArguments
            );
            quotedListElements = new OtpErlangObject[]{
                    quotedWhenOperation
            };
        } else {
            quotedListElements = quotedArguments;
        }

        return new OtpErlangList(quotedListElements);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirStringLine stringLine) {
        ElixirQuoteStringBody quoteStringBody = stringLine.getQuoteStringBody();
        return quotedChildNodes(stringLine, childNodes(quoteStringBody));
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirStructOperation structOperation) {
        PsiElement[] children = structOperation.getChildren();

        assert children.length == 3;

        Operator operator = (Operator) children[0];
        OtpErlangObject quotedOperator = operator.quote();

        Quotable name = (Quotable) children[1];
        OtpErlangObject quotedName = name.quote();

        Quotable mapArguments = (Quotable) children[2];
        OtpErlangObject quotedMapArguments = mapArguments.quote();

        return quotedFunctionCall(
                quotedOperator,
                metadata(operator),
                quotedName,
                quotedMapArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirTuple tuple) {
        ASTNode node = tuple.getNode();
        ASTNode[] openingCurlies = node.getChildren(TokenSet.create(ElixirTypes.OPENING_CURLY));

        assert openingCurlies.length == 1;

        ASTNode openingCurly = openingCurlies[0];

        PsiElement[] children = tuple.getChildren();
        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];

        int i = 0;
        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;
            quotedChildren[i++] = quotableChild.quote();
        }

        OtpErlangObject quoted;

        // 2-tuples are literals in quoted form
        if (quotedChildren.length == 2) {
            quoted = new OtpErlangTuple(quotedChildren);
        } else {
            quoted = quotedFunctionCall(
                    "{}",
                    metadata(openingCurly),
                    quotedChildren
            );
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final Heredoc heredoc) {
        ElixirHeredocPrefix heredocPrefix = heredoc.getHeredocPrefix();
        int prefixLength = heredocPrefix.getTextLength();
        Deque<ASTNode> alignedNodeQueue = new LinkedList<ASTNode>();
        List<HeredocLine> heredocLineList = heredoc.getHeredocLineList();
        IElementType fragmentType = heredoc.getFragmentType();

        for (HeredocLine line  : heredocLineList) {
            queueChildNodes(line, fragmentType, prefixLength, alignedNodeQueue);
        }

        Queue<ASTNode> mergedNodeQueue = mergeFragments(
                alignedNodeQueue,
                heredoc.getFragmentType(),
                heredoc.getManager()
        );
        ASTNode[] mergedNodes = new ASTNode[mergedNodeQueue.size()];
        mergedNodeQueue.toArray(mergedNodes);

        return quotedChildNodes(heredoc, mergedNodes);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final HeredocLine heredocLine, @NotNull final Heredoc heredoc, int prefixLength) {
        ElixirHeredocLinePrefix heredocLinePrefix = heredocLine.getHeredocLinePrefix();
        ASTNode excessWhitespace = heredocLinePrefix.excessWhitespace(heredoc.getFragmentType(), prefixLength);
        Body body = heredocLine.getBody();
        ASTNode[] directChildNodes = childNodes(body);
        ASTNode[] accumulatedChildNodes;

        if (excessWhitespace != null) {
            accumulatedChildNodes = new ASTNode[directChildNodes.length + 1];
            accumulatedChildNodes[0] = excessWhitespace;

            for (int i = 0; i < directChildNodes.length; i++) {
                accumulatedChildNodes[i + 1] = directChildNodes[i];
            }
        } else {
            accumulatedChildNodes = directChildNodes;
        }

        return quotedChildNodes(
                heredoc,
                metadata(heredocLine),
                accumulatedChildNodes
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QuotableKeywordList quotableKeywordList) {
        List<QuotableKeywordPair> keywordPairList = quotableKeywordList.quotableKeywordPairList();
        List<OtpErlangObject> quotedKeywordPairList = new ArrayList<OtpErlangObject>(keywordPairList.size());

        for (QuotableKeywordPair quotableKeywordPair : keywordPairList) {
            quotedKeywordPairList.add(
                    quotableKeywordPair.quote()
            );
        }

        OtpErlangObject[] quotedKeywordPairs = new OtpErlangObject[quotedKeywordPairList.size()];
        quotedKeywordPairList.toArray(quotedKeywordPairs);

        return new OtpErlangList(quotedKeywordPairs);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QuotableKeywordPair quotableKeywordPair) {
        Quotable keywordKey = quotableKeywordPair.getKeywordKey();
        OtpErlangObject quotedKeywordKey = keywordKey.quote();

        Quotable keywordValue = quotableKeywordPair.getKeywordValue();
        OtpErlangObject quotedKeywordValue = keywordValue.quote();

        OtpErlangObject[] elements = {
                quotedKeywordKey,
                quotedKeywordValue
        };

        return new OtpErlangTuple(elements);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirKeywordKey keywordKey) {
        ElixirCharListLine charListLine = keywordKey.getCharListLine();
        OtpErlangObject quoted;

        if (charListLine != null) {
            quoted = charListLine.quoteAsAtom();
        } else {
            ElixirStringLine stringLine = keywordKey.getStringLine();

            if (stringLine != null) {
                quoted = stringLine.quoteAsAtom();
            } else {
                quoted = new OtpErlangAtom(computeReadAction(keywordKey::getText));
            }
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirList list) {
        PsiElement[] listArguments = list.getChildren();
        List<OtpErlangObject> quotedListArgumentList = new ArrayList<OtpErlangObject>(listArguments.length);

        if (listArguments.length > 0) {
            for (int i = 0; i < listArguments.length - 1; i++) {
                Quotable listArgument = (Quotable) listArguments[i];
                quotedListArgumentList.add(listArgument.quote());
            }

            PsiElement lastListArgument = listArguments[listArguments.length - 1];

            if (lastListArgument instanceof ElixirKeywords) {
                QuotableKeywordList quotableKeywordList = (QuotableKeywordList) lastListArgument;

                for (Quotable keywordPair : quotableKeywordList.quotableKeywordPairList()) {
                    quotedListArgumentList.add(keywordPair.quote());
                }
            } else {
                Quotable quotable = (Quotable) lastListArgument;
                quotedListArgumentList.add(quotable.quote());
            }
        }

        OtpErlangObject[] quotedListArguments = new OtpErlangObject[quotedListArgumentList.size()];
        quotedListArgumentList.toArray(quotedListArguments);

        return elixirCharList(new OtpErlangList(quotedListArguments));
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirMapArguments mapArguments) {
        ASTNode node = mapArguments.getNode();
        ASTNode[] openingCurlies = node.getChildren(TokenSet.create(ElixirTypes.OPENING_CURLY));

        assert openingCurlies.length == 1;

        ASTNode openingCurly = openingCurlies[0];

        OtpErlangObject[] quotedArguments;

        ElixirMapUpdateArguments mapUpdateArguments = mapArguments.getMapUpdateArguments();

        if (mapUpdateArguments != null) {
            quotedArguments = new OtpErlangObject[]{
                    mapUpdateArguments.quote()
            };
        } else {
            ElixirMapConstructionArguments mapConstructionArguments = mapArguments.getMapConstructionArguments();

            if (mapConstructionArguments != null) {
                quotedArguments = mapConstructionArguments.quoteArguments();
            } else {
                quotedArguments = new OtpErlangObject[0];
            }
        }

        return quotedFunctionCall(
                "%{}",
                metadata(openingCurly),
                quotedArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirMapOperation mapOperation) {
        Quotable mapArguments = mapOperation.getMapArguments();

        return mapArguments.quote();
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirMapUpdateArguments mapUpdateArguments) {
        PsiElement[] children = mapUpdateArguments.getChildren();

        assert children.length >= 3;

        Quotable currentMap = (Quotable) children[0];
        OtpErlangObject quotedCurrentMap = currentMap.quote();

        Operator pipeOperator = (Operator) children[1];
        OtpErlangObject quotedPipeOperator = pipeOperator.quote();

        List<OtpErlangObject> quotedRightOperandList = new ArrayList<OtpErlangObject>();

        for (int i = 2; i < children.length; i++) {
            Quotable child = (Quotable) children[i];
            OtpErlangObject quotedChild = child.quote();

            if (quotedChild instanceof OtpErlangList) {
                OtpErlangList quotedList = (OtpErlangList) quotedChild;

                for (OtpErlangObject quotedElement : quotedList.elements()) {
                    quotedRightOperandList.add(quotedElement);
                }
            } else {
                quotedRightOperandList.add(quotedChild);
            }
        }

        OtpErlangObject[] quotedRightOperands = new OtpErlangObject[quotedRightOperandList.size()];
        quotedRightOperands = quotedRightOperandList.toArray(quotedRightOperands);
        OtpErlangObject quotedMapUpdates = new OtpErlangList(quotedRightOperands);

        return quotedFunctionCall(
                quotedPipeOperator,
                metadata(pipeOperator),
                quotedCurrentMap,
                quotedMapUpdates
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirMultipleAliases multipleAliases) {
        PsiElement[] children = multipleAliases.getChildren();
        OtpErlangObject[] quotedChildren = new OtpErlangObject[children.length];

        int i = 0;
        for (PsiElement child : children) {
            Quotable quotableChild = (Quotable) child;
            quotedChildren[i++] = quotableChild.quote();
        }

        return quotedFunctionArguments(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirNoParenthesesManyStrictNoParenthesesExpression noParenthesesManyStrictNoParenthesesExpression) {
        PsiElement[] children = noParenthesesManyStrictNoParenthesesExpression.getChildren();

        assert children.length == 1;

        Quotable child = (Quotable) children[0];

        return child.quote();
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final AtNumericBracketOperation atUnqualifiedBracketOperation) {
        Quotable operator = atUnqualifiedBracketOperation.getAtPrefixOperator();
        OtpErlangObject quotedOperator = operator.quote();

        PsiElement[] children = atUnqualifiedBracketOperation.getChildren();

        assert children.length == 3;

        Quotable numeric = (Quotable) children[1];

        OtpErlangObject quotedOperand = numeric.quote();

        OtpErlangList metadata = metadata(atUnqualifiedBracketOperation);

        OtpErlangTuple quotedContainer = quotedFunctionCall(quotedOperator, metadata, quotedOperand);

        Quotable bracketArguments = atUnqualifiedBracketOperation.getBracketArguments();
        OtpErlangObject quotedBracketArguments = bracketArguments.quote();

        return quotedFunctionCall(
                "Elixir.Access",
                "get",
                metadata(bracketArguments),
                quotedContainer,
                quotedBracketArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final AtUnqualifiedBracketOperation atUnqualifiedBracketOperation) {
        Quotable operator = atUnqualifiedBracketOperation.getAtPrefixOperator();
        OtpErlangObject quotedOperator = operator.quote();

        ASTNode node = atUnqualifiedBracketOperation.getNode();
        ASTNode[] identifierNodes = node.getChildren(IDENTIFIER_TOKEN_SET);

        assert identifierNodes.length == 1;

        ASTNode identifierNode = identifierNodes[0];
        String identifier = identifierNode.getText();
        OtpErlangList metadata = metadata(atUnqualifiedBracketOperation);

        OtpErlangObject quotedOperand = quotedVariable(identifier, metadata);
        OtpErlangTuple quotedContainer = quotedFunctionCall(quotedOperator, metadata, quotedOperand);

        Quotable bracketArguments = atUnqualifiedBracketOperation.getBracketArguments();
        OtpErlangObject quotedBracketArguments = bracketArguments.quote();

        return quotedFunctionCall(
                "Elixir.Access",
                "get",
                metadata(bracketArguments),
                quotedContainer,
                quotedBracketArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        ElixirAtIdentifier atIdentifier = atUnqualifiedNoParenthesesCall.getAtIdentifier();
        Quotable operator = atIdentifier.getAtPrefixOperator();
        OtpErlangObject quotedOperator = operator.quote();

        ASTNode node = atIdentifier.getNode();
        ASTNode[] identifierNodes = node.getChildren(IDENTIFIER_TOKEN_SET);

        assert identifierNodes.length == 1;

        ASTNode identifierNode = identifierNodes[0];
        String identifier = identifierNode.getText();
        OtpErlangObject quotedIdentifier = new OtpErlangAtom(identifier);

        QuotableArguments noParenthesesOneArgument = atUnqualifiedNoParenthesesCall.getNoParenthesesOneArgument();
        OtpErlangObject[] quotedArguments = noParenthesesOneArgument.quoteArguments();
        ElixirDoBlock doBlock = atUnqualifiedNoParenthesesCall.getDoBlock();

        OtpErlangObject quotedOperand =  quotedBlockCall(
                quotedIdentifier,
                metadata(operator),
                quotedArguments,
                doBlock
        );

        return quotedFunctionCall(
                quotedOperator,
                metadata(operator),
                quotedOperand
        );
    }

    @NotNull
    public static OtpErlangObject quote(@NotNull final BracketOperation bracketOperation) {
        PsiElement[] children = bracketOperation.getChildren();

        assert children.length == 2;

        Quotable matchedExpression = (Quotable) children[0];
        OtpErlangObject quotedMatchedExpression = matchedExpression.quote();
        Quotable bracketArguments = (Quotable) children[1];
        OtpErlangObject quotedBracketArguments = bracketArguments.quote();

        return quotedFunctionCall(
                "Elixir.Access",
                "get",
                metadata(bracketArguments),
                quotedMatchedExpression,
                quotedBracketArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final DotCall dotCall) {
        Quotable leftOperand = (Quotable) dotCall.getFirstChild();
        OtpErlangObject quotedLeftOperand = leftOperand.quote();

        Quotable operator = dotCall.getDotInfixOperator();
        OtpErlangObject quotedOperator = operator.quote();
        OtpErlangList operatorMetadata = metadata(operator);

        OtpErlangTuple quotedIdentifier = quotedFunctionCall(
                quotedOperator,
                operatorMetadata,
                quotedLeftOperand
        );

        List<ElixirParenthesesArguments> parenthesesArgumentsList = dotCall.getParenthesesArgumentsList();
        ElixirDoBlock doBlock = dotCall.getDoBlock();

        return quotedParenthesesCall(
                quotedIdentifier,
                operatorMetadata,
                parenthesesArgumentsList,
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final In in) {
        PsiElement[] children = in.getChildren();

        if (children.length != 3) {
            throw new NotImplementedException("BinaryOperation expected to have 3 children (left operand, operator, right operand");
        }

        Quotable leftOperand = (Quotable) children[0];
        OtpErlangObject quotedLeftOperand = leftOperand.quote();

        Quotable operator = (Quotable) children[1];
        OtpErlangObject quotedOperator = operator.quote();

        Quotable rightOperand = (Quotable) children[2];
        OtpErlangObject quotedRightOperand = rightOperand.quote();

        OtpErlangObject quoted = null;

        // @see https://github.com/elixir-lang/elixir/blob/6c288be7300509ff7b809002a3563c6a02dc13fa/lib/elixir/src/elixir_parser.yrl#L596-L597
        // @see https://github.com/elixir-lang/elixir/blob/6c288be7300509ff7b809002a3563c6a02dc13fa/lib/elixir/src/elixir_parser.yrl#L583
        if (Macro.INSTANCE.isExpression(quotedLeftOperand)) {
            OtpErlangTuple leftExpression = (OtpErlangTuple) quotedLeftOperand;
            OtpErlangObject leftOperator = leftExpression.elementAt(0);

            for (OtpErlangAtom rearrangedUnaryOperator : REARRANGED_UNARY_OPERATORS) {
                /* build_op({_Kind, Line, 'in'}, {UOp, _, [Left]}, Right) when ?rearrange_uop(UOp) ->
                     {UOp, meta(Line), [{'in', meta(Line), [Left, Right]}]}; */
                if (leftOperator.equals(rearrangedUnaryOperator)) {
                    OtpErlangObject unaryOperatorArguments = leftExpression.elementAt(2);
                    OtpErlangObject originalUnaryOperand;


                    if (unaryOperatorArguments instanceof OtpErlangString) {
                        OtpErlangString unaryOperatorPrintableArguments = (OtpErlangString) unaryOperatorArguments;
                        int codePoint = unaryOperatorPrintableArguments.stringValue().codePointAt(0);
                        originalUnaryOperand = new OtpErlangLong(codePoint);
                    } else if (unaryOperatorArguments instanceof OtpErlangList) {
                        OtpErlangList unaryOperatorArgumentList = (OtpErlangList) unaryOperatorArguments;
                        originalUnaryOperand = unaryOperatorArgumentList.elementAt(0);
                    } else {
                        throw new NotImplementedException("Expected REARRANGED_UNARY_OPERATORS operand to be quoted as an OtpErlangString or OtpErlangList");
                    }

                    OtpErlangList operatorMetadata = metadata(operator);

                    quoted = quotedFunctionCall(
                            leftOperator,
                            operatorMetadata,
                            quotedFunctionCall(
                                    quotedOperator,
                                    operatorMetadata,
                                    originalUnaryOperand,
                                    quotedRightOperand
                            )
                    );
                }
            }
        }
if (quoted == null) {
            quoted = quotedFunctionCall(
                    quotedOperator,
                    metadata(operator),
                    quotedLeftOperand,
                    quotedRightOperand
            );
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedAlias qualifiedAlias) {
        PsiElement[] children = qualifiedAlias.getChildren();

        assert children.length == 3;

        Quotable alias = (Quotable) children[2];
        OtpErlangTuple quotedAlias = (OtpErlangTuple) alias.quote();

        Quotable matchedExpression = (Quotable) children[0];
        OtpErlangObject quotedMatchedExpression = matchedExpression.quote();

        /*
         * Use line from last alias, but drop `counter: 0`
         */
        OtpErlangList aliasMetadata = Macro.INSTANCE.metadata(quotedAlias);
        OtpErlangTuple lineTuple = (OtpErlangTuple) org.elixir_lang.List.keyfind(
                aliasMetadata,
                new OtpErlangAtom("line"),
                0
        );
        OtpErlangList qualifiedAliasMetdata = new OtpErlangList(
                new OtpErlangObject[] {
                        lineTuple
                }
        );

        OtpErlangList lastAliasList = Macro.INSTANCE.callArguments(quotedAlias);
        OtpErlangObject[] mergedArguments;
        int i = 0;

        /* if both aliases, then the counter: 0 needs to be removed from the metadata data and the arguments for
           each __aliases__ need to be combined */
        if (Macro.INSTANCE.isAliases(quotedMatchedExpression)) {
            OtpErlangList firstAliasList = Macro.INSTANCE.callArguments((OtpErlangTuple) quotedMatchedExpression);
            mergedArguments = new OtpErlangObject[firstAliasList.arity() + lastAliasList.arity()];

            for (OtpErlangObject firstAliasElement : firstAliasList) {
                mergedArguments[i++] = firstAliasElement;
            }
        } else {
            mergedArguments = new OtpErlangObject[1 + lastAliasList.arity()];

            mergedArguments[i++] = quotedMatchedExpression;
        }

        for (OtpErlangObject lastAliasElement : lastAliasList) {
            mergedArguments[i++] = lastAliasElement;
        }

        return quotedFunctionCall(
                ALIASES,
                qualifiedAliasMetdata,
                mergedArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedMultipleAliases qualifiedMultipleAliases) {
        PsiElement[] children = qualifiedMultipleAliases.getChildren();

        assert children.length == 3;

        Quotable multipleAliases = (Quotable) children[2];
        OtpErlangObject quotedMultipleAliases = multipleAliases.quote();

        Quotable expression = (Quotable) children[0];
        OtpErlangObject quotedExpression = expression.quote();

        OtpErlangList metadata = metadata((Operator) children[1]);

        // See https://github.com/lexmag/elixir/blob/8c57c9110301c1ee02d84b574c59feff00e14ba3/lib/elixir/src/elixir_parser.yrl#L644
        OtpErlangObject head = quotedFunctionCall(
                ".",
                metadata,
                quotedExpression,
                MULTIPLE_ALIASES
        );

        return new OtpErlangTuple(
                new OtpErlangObject[] {
                        head,
                        metadata,
                        quotedMultipleAliases
                }
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedBracketOperation qualifiedBracketOperation) {
        Quotable matchedExpression = (Quotable) qualifiedBracketOperation.getFirstChild();
        OtpErlangObject quotedIdentifier = matchedExpression.quote();

        ElixirRelativeIdentifier relativeIdentifier = qualifiedBracketOperation.getRelativeIdentifier();
        OtpErlangObject quotedRelativeIdentifier = relativeIdentifier.quote();

        quotedIdentifier = quotedFunctionCall(
                ".",
                metadata(relativeIdentifier),
                quotedIdentifier,
                quotedRelativeIdentifier
        );

        OtpErlangList callMetadata = Macro.INSTANCE.metadata((OtpErlangTuple) quotedIdentifier);

        OtpErlangObject quotedContainer = quotedFunctionCall(
                quotedIdentifier,
                callMetadata
        );

        Quotable bracketArguments = qualifiedBracketOperation.getBracketArguments();
        OtpErlangObject quotedBracketArguments = bracketArguments.quote();

        return quotedFunctionCall(
                "Elixir.Access",
                "get",
                metadata(bracketArguments),
                quotedContainer,
                quotedBracketArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedNoArgumentsCall qualifiedNoArgumentsCall) {
        Quotable qualifier = (Quotable) qualifiedNoArgumentsCall.getFirstChild();
        OtpErlangObject quotedQualifier = qualifier.quote();

        Quotable relativeIdentifier = qualifiedNoArgumentsCall.getRelativeIdentifier();
        OtpErlangObject quotedRelativeIdentifier = relativeIdentifier.quote();

        OtpErlangTuple quotedIdentifier = quotedFunctionCall(
                ".",
                metadata(relativeIdentifier),
                quotedQualifier,
                quotedRelativeIdentifier
        );

        ElixirDoBlock doBlock = qualifiedNoArgumentsCall.getDoBlock();

        return quotedBlockCall(
                quotedIdentifier,
                metadata(relativeIdentifier),
                new OtpErlangObject[0],
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedNoParenthesesCall qualifiedNoParenthesesCall) {
        Quotable qualifier = (Quotable) qualifiedNoParenthesesCall.getFirstChild();
        OtpErlangObject quotedQualifier = qualifier.quote();

        Quotable relativeIdentifier = qualifiedNoParenthesesCall.getRelativeIdentifier();
        OtpErlangObject quotedRelativeIdentifier = relativeIdentifier.quote();

        OtpErlangTuple quotedIdentifier = quotedFunctionCall(
                ".",
                metadata(relativeIdentifier),
                quotedQualifier,
                quotedRelativeIdentifier
        );

        ElixirNoParenthesesOneArgument noParenthesesOneArgument = qualifiedNoParenthesesCall.getNoParenthesesOneArgument();
        OtpErlangObject[] quotedArguments = noParenthesesOneArgument.quoteArguments();
        ElixirDoBlock doBlock = qualifiedNoParenthesesCall.getDoBlock();

        return quotedBlockCall(
                quotedIdentifier,
                metadata(relativeIdentifier),
                quotedArguments,
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final QualifiedParenthesesCall qualifiedParenthesesCall) {
        Quotable qualifier = (Quotable) qualifiedParenthesesCall.getFirstChild();
        OtpErlangObject quotedQualifier = qualifier.quote();

        Quotable relativeIdentifier = qualifiedParenthesesCall.getRelativeIdentifier();
        OtpErlangObject quotedRelativeIdentifier = relativeIdentifier.quote();

        OtpErlangList metadata = metadata(relativeIdentifier);
        OtpErlangObject quotedIdentifier = quotedFunctionCall(
                ".",
                metadata,
                quotedQualifier,
                quotedRelativeIdentifier
        );

        ElixirMatchedParenthesesArguments matchedParenthesesArguments = qualifiedParenthesesCall.getMatchedParenthesesArguments();
        List<ElixirParenthesesArguments> parenthesesArgumentsList = matchedParenthesesArguments.getParenthesesArgumentsList();
        ElixirDoBlock doBlock = qualifiedParenthesesCall.getDoBlock();

        return quotedParenthesesCall(
                quotedIdentifier,
                metadata,
                parenthesesArgumentsList,
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final UnqualifiedBracketOperation unqualifiedBracketOperation) {
        ASTNode node = unqualifiedBracketOperation.getNode();
        OtpErlangObject quotedIdentifier = new OtpErlangAtom(node.getFirstChildNode().getText());
        OtpErlangObject quotedContainer = quotedVariable(quotedIdentifier, metadata(unqualifiedBracketOperation));

        Quotable bracketArguments = unqualifiedBracketOperation.getBracketArguments();
        OtpErlangObject quotedBrackArguments = bracketArguments.quote();

        return quotedFunctionCall(
                "Elixir.Access",
                "get",
                metadata(bracketArguments),
                quotedContainer,
                quotedBrackArguments
        );
    }

    /* Replaces `nil` argument in variables with the quoted ElixirMatchdNotParenthesesArguments.
     *
     */
    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final UnqualifiedNoParenthesesCall unqualifiedNoParenthesesCall) {
        String identifier = unqualifiedNoParenthesesCall.functionName();
        OtpErlangObject quotedIdentifer = new OtpErlangAtom(identifier);

        ElixirNoParenthesesOneArgument noParenthesesOneArgument = unqualifiedNoParenthesesCall.getNoParenthesesOneArgument();
        OtpErlangObject[] quotedArguments = noParenthesesOneArgument.quoteArguments();

        OtpErlangList blockCallMetadata = metadata(unqualifiedNoParenthesesCall);

        // see https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b//lib/elixir/src/elixir_parser.yrl#L627-L628
        if (quotedArguments.length == 1) {
            OtpErlangObject quotedArgument = quotedArguments[0];

            if (Macro.INSTANCE.isExpression(quotedArgument)) {
                OtpErlangTuple expression = (OtpErlangTuple) quotedArgument;

                OtpErlangObject receiver = expression.elementAt(0);

                if (receiver.equals(MINUS) || receiver.equals(PLUS)) {
                    OtpErlangList dualCallArguments = Macro.INSTANCE.callArguments(expression);

                    // [Arg]
                    if (dualCallArguments.arity() == 1) {
                        /* @note getChildren[0] is NOT the same as getFirstChild().  getFirstChild() will get the
                             leaf node for identifier instead of the first compound, rule node for the argument. */
                        PsiElement argument = unqualifiedNoParenthesesCall.getChildren()[1].getFirstChild();

                        if (!(argument instanceof ElixirAccessExpression && argument.getFirstChild() instanceof ElixirParentheticalStab)) {
                            blockCallMetadata = new OtpErlangList(
                                    new OtpErlangObject[]{
                                            AMBIGUOUS_OP_KEYWORD_PAIR,
                                            blockCallMetadata.elementAt(0)
                                    }
                            );
                        }
                    }
                }
            }
        }

        ElixirDoBlock doBlock = unqualifiedNoParenthesesCall.getDoBlock();

        return quotedBlockCall(
                quotedIdentifer,
                blockCallMetadata,
                quotedArguments,
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final UnqualifiedNoArgumentsCall unqualifiedNoArgumentsCall) {
        ElixirDoBlock doBlock = unqualifiedNoArgumentsCall.getDoBlock();
        OtpErlangObject quoted;
        ElixirIdentifier identifier = unqualifiedNoArgumentsCall.getIdentifier();

        String identifierText = identifier.getText();
        OtpErlangList callMetadata = metadata(identifier);

        // if a variable has a `do` block is no longer a variable because the do block acts as keyword arguments.
        if (doBlock != null) {
            OtpErlangObject[] quotedBlockArguments = doBlock.quoteArguments();

            quoted = quotedFunctionCall(
                    identifierText,
                    callMetadata,
                    quotedBlockArguments
            );
        } else {
            /* @note quotedFunctionCall cannot be used here because in the 3-tuple for function calls, the elements are
              {name, metadata, arguments}, while for an ambiguous call or variable, the elements are
              {name, metadata, context}.  Importantly, context is nil when there is no context while arguments are []
              when there are no arguments. */
            quoted = quotedVariable(
                    identifierText,
                    callMetadata
            );
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final UnqualifiedParenthesesCall unqualifiedParenthesesCall) {
        ASTNode identifierNode = unqualifiedParenthesesCall.getNode().getFirstChildNode();
        OtpErlangList metadata = metadata(unqualifiedParenthesesCall);
        OtpErlangObject quotedIdentifier = new OtpErlangAtom(identifierNode.getText());

        ElixirMatchedParenthesesArguments matchedParenthesesArguments = unqualifiedParenthesesCall.getMatchedParenthesesArguments();
        List<ElixirParenthesesArguments> parenthesesArgumentsList = matchedParenthesesArguments.getParenthesesArgumentsList();
        ElixirDoBlock doBlock = unqualifiedParenthesesCall.getDoBlock();

        return quotedParenthesesCall(
                quotedIdentifier,
                metadata,
                parenthesesArgumentsList,
                doBlock
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirParentheticalStab parentheticalStab) {
        Quotable stab = parentheticalStab.getStab();
        OtpErlangObject quoted;

        if (stab != null) {
            quoted = stab.quote();
        } else {
            // @note CANNOT use quotedFunctionCall because it requires metadata and gives nil instead of [] when no
            //   arguments are given while empty block is quoted as `{__block__, [], []}`
            quoted = new OtpErlangTuple(
                    new OtpErlangObject[]{
                            BLOCK,
                            new OtpErlangList(),
                            new OtpErlangList()
                    }
            );
        }

        return quoted;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirVariable variable) {
        /* @note quotedFunctionCall cannot be used here because in the 3-tuple for function calls, the elements are
           {name, metadata, arguments}, while for an ambiguous call or variable, the elements are
           {name, metadata, context}.  Importantly, context is nil when there is no context while arguments are [] when
           there are no arguments. */
        return quotedVariable(variable);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(Operator operator) {
        ASTNode operatorTokenNode = operatorTokenNode(operator);

        return new OtpErlangAtom(operatorTokenNode.getText());
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(Prefix prefix) {
        PsiElement[] children = prefix.getChildren();

        if (children.length != 2) {
            throw new NotImplementedException("Prefix expected to have 2 children (operator and operand");
        }

        Quotable operator = (Quotable) children[0];
        OtpErlangObject quotedOperator = operator.quote();

        Quotable operand = (Quotable) children[1];
        OtpErlangObject quotedOperand = operand.quote();

        return quotedFunctionCall(
                quotedOperator,
                metadata(prefix),
                quotedOperand
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(PsiElement[] children) {
        List<Quotable> quotableList = new LinkedList<Quotable>();

        for (int i = 0; i < children.length; i++) {
            PsiElement child = children[i];

            if (child instanceof Quotable) {
                quotableList.add((Quotable) child);
            } else if (child instanceof Unquoted) {
                continue;
            } else {
                throw new NotImplementedException("Child, " + child + ", must be Quotable or Unquoted");
            }
        }

        Quotable[] quotableChildren = new Quotable[quotableList.size()];
        quotableList.toArray(quotableChildren);

        return quote(quotableChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(PsiFile file) {
        final FileViewProvider fileViewProvider = file.getViewProvider();
        final ElixirFile root = (ElixirFile) fileViewProvider.getPsi(ElixirLanguage.INSTANCE);

        return ElixirPsiImplUtil.quote(root);
    }

    @Contract(pure = true)
    @NotNull
    private static OtpErlangObject quote(Quotable[] children) {
        Deque<OtpErlangObject> quotedChildren = new ArrayDeque<OtpErlangObject>(children.length);

        for (int i = 0; i < children.length; i++) {
            quotedChildren.add(children[i].quote());
        }

        // Uses toBlock because this is for inside interpolation, which functions the same as an embedded file
        return toBlock(quotedChildren);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull final ElixirUnqualifiedNoParenthesesManyArgumentsCall unqualifiedNoParenthesesManyArgumentsCall) {
        OtpErlangObject quotedIdentifier = unqualifiedNoParenthesesManyArgumentsCall.getIdentifier().quote();
        OtpErlangObject[] quotedArguments = ElixirPsiImplUtil.quoteArguments(unqualifiedNoParenthesesManyArgumentsCall);
        return anchoredQuotedFunctionCall(unqualifiedNoParenthesesManyArgumentsCall, quotedIdentifier, quotedArguments);
    }

    @NotNull
    public static OtpErlangObject anchoredQuotedFunctionCall(PsiElement anchor, OtpErlangObject quotedIdentifier, OtpErlangObject... quotedArguments) {
        OtpErlangList metadata;

        if (Macro.INSTANCE.isExpression(quotedIdentifier)) {
            OtpErlangTuple expression = (OtpErlangTuple) quotedIdentifier;
            /* Grab metadata from quotedIdentifier so line of quotedFunctionCall is line of identifier or `.` in
               identifier, which can differ from the line of quotable call when there are newlines on either side of
               `.`. */
            metadata = Macro.INSTANCE.metadata(expression);
        } else {
            metadata = metadata(anchor);
        }

        return quotedFunctionCall(
                quotedIdentifier,
                metadata,
                quotedArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(SigilHeredoc sigilHeredoc) {
        OtpErlangObject quotedHeredoc = quote((Heredoc) sigilHeredoc);

        return quote(sigilHeredoc, quotedHeredoc);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(SigilLine sigilLine) {
        Body body = sigilLine.getBody();
        ASTNode[] bodyChildNodes = childNodes(body);
        OtpErlangObject quotedBody = quotedChildNodes(sigilLine, bodyChildNodes);

        return quote(sigilLine, quotedBody);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(@NotNull final Arguments arguments) {
        return QuotableArgumentsImpl.quoteArguments(arguments);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(@NotNull final ElixirBlockList blockList) {
        return QuotableArgumentsImpl.quoteArguments(blockList);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(
            @NotNull ElixirUnqualifiedNoParenthesesManyArgumentsCall unqualifiedNoParenthesesManyArgumentsCall
    ) {
        return QuotableArgumentsImpl.quoteArguments(unqualifiedNoParenthesesManyArgumentsCall);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(@NotNull final ElixirDoBlock doBlock) {
        return QuotableArgumentsImpl.quoteArguments(doBlock);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(
            @NotNull final ElixirMapConstructionArguments mapConstructionArguments
    ) {
        return QuotableArgumentsImpl.quoteArguments(mapConstructionArguments);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(@NotNull final ElixirNoParenthesesArguments noParenthesesArguments) {
        return QuotableArgumentsImpl.quoteArguments(noParenthesesArguments);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject[] quoteArguments(ElixirParenthesesArguments parenthesesArguments) {
        return QuotableArgumentsImpl.quoteArguments(parenthesesArguments);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteBinary(InterpolatedCharList interpolatedCharList, OtpErlangTuple binary) {
        return ParentImpl.quoteBinary(interpolatedCharList, binary);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteBinary(InterpolatedString interpolatedString, OtpErlangTuple binary) {
        return ParentImpl.quoteBinary(interpolatedString, binary);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteBinary(Sigil sigil, OtpErlangTuple binary) {
        return ParentImpl.quoteBinary(sigil, binary);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteEmpty(InterpolatedCharList interpolatedCharList) {
        return ParentImpl.quoteEmpty(interpolatedCharList);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteEmpty(InterpolatedString interpolatedString) {
        return ParentImpl.quoteEmpty(interpolatedString);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteEmpty(Sigil sigil) {
        return ParentImpl.quoteEmpty(sigil);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteLiteral(InterpolatedCharList interpolatedCharList, List<Integer> codePointList) {
        return ParentImpl.quoteLiteral(interpolatedCharList, codePointList);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteLiteral(InterpolatedString interpolatedString, List<Integer> codePointList) {
        return ParentImpl.quoteLiteral(interpolatedString, codePointList);
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quoteLiteral(Sigil sigil, List<Integer> codePointList) {
        return ParentImpl.quoteLiteral(sigil, codePointList);
    }

    @NotNull
    public static OtpErlangObject quotedFunctionArguments(final OtpErlangObject... arguments) {
        /*
         * Erlang will automatically stringify a list that is just a list of LATIN-1 printable code
         * points.
         * OtpErlangString and OtpErlangList are not equal when they have the same content, so to check against
         * Elixir.Code.string_to_quoted, this code must determine if Erlang would return an OtpErlangString instead
         * of OtpErlangList and do the same.
         */
        return elixirCharList(
                new OtpErlangList(arguments)
        );
    }

    @NotNull
    public static OtpErlangTuple quotedFunctionCall(final String identifier, final OtpErlangList metadata, final OtpErlangObject... arguments) {
        return quotedFunctionCall(
                new OtpErlangAtom(identifier),
                metadata,
                arguments
        );
    }

    @NotNull
    public static OtpErlangTuple quotedFunctionCall(final String module, final String identifier, final OtpErlangList metadata, final OtpErlangObject... arguments) {
        OtpErlangObject quotedQualifiedIdentifier = quotedFunctionCall(
                ".",
                metadata,
                new OtpErlangAtom(module),
                new OtpErlangAtom(identifier)
        );
        return quotedFunctionCall(
                quotedQualifiedIdentifier,
                metadata,
                arguments
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedLeftOperand(@NotNull final ElixirStabOperation stabOperation) {
        Quotable leftOperand = stabOperation.leftOperand();
        OtpErlangObject quotedLeftOperand;

        if (leftOperand != null) {
            quotedLeftOperand = leftOperand.quote();
        } else {
            // when there is not signature before `->`.
            quotedLeftOperand = new OtpErlangList();
        }

        return quotedLeftOperand;
    }

    @Contract(pure = true)
    @NotNull
    private static OtpErlangObject quotedBlockCall(
            @NotNull OtpErlangObject quotedIdentifier,
            @NotNull final OtpErlangList callMetadata,
            @NotNull final OtpErlangObject[] quotedArguments,
            @Nullable final ElixirDoBlock doBlock) {
        OtpErlangObject[] quotedCombinedArguments = quotedArguments;

        if (doBlock != null) {
            OtpErlangObject[] quotedBlockArguments = doBlock.quoteArguments();
            quotedCombinedArguments = new OtpErlangObject[
                    quotedArguments.length + quotedBlockArguments.length
                    ];

            System.arraycopy(
                    quotedArguments,
                    0,
                    quotedCombinedArguments,
                    0,
                    quotedArguments.length
            );
            System.arraycopy(
                    quotedBlockArguments,
                    0,
                    quotedCombinedArguments,
                    quotedArguments.length,
                    quotedBlockArguments.length
            );
        }

        return quotedFunctionCall(
                quotedIdentifier,
                callMetadata,
                quotedCombinedArguments
        );
    }

    @Contract(pure = true)
    @NotNull
    private static OtpErlangObject quotedParenthesesCall(
            @NotNull OtpErlangObject quotedIdentifier,
            @NotNull final OtpErlangList identifierMetadata,
            @NotNull final List<ElixirParenthesesArguments> parenthesesArgumentsList,
            @Nullable final ElixirDoBlock doBlock) {
        OtpErlangObject quoted = null;

        int parenthesesArgumentsListSize = parenthesesArgumentsList.size();

        assert parenthesesArgumentsListSize > 0;

        int i = 0;
        for (ElixirParenthesesArguments parenthesesArguments : parenthesesArgumentsList) {
            OtpErlangObject[] quotedParenthesesArguments = parenthesesArguments.quoteArguments();

            if (i == parenthesesArgumentsListSize - 1) {
                quoted = quotedBlockCall(
                        quotedIdentifier,
                        identifierMetadata,
                        quotedParenthesesArguments,
                        doBlock
                );
            } else {
                quoted = quotedFunctionCall(
                        quotedIdentifier,
                        identifierMetadata,
                        quotedParenthesesArguments
                );
            }
            // function call is identifier for second call
            quotedIdentifier = quoted;

            i++;
        }

        return quoted;
    }

    @NotNull
    @Contract(pure = true)
    public static OtpErlangObject quotedEmpty(@SuppressWarnings("unused") ElixirCharListHeredoc charListHeredoc) {
        // an empty CharList is just an empty list
        return new OtpErlangList();
    }

    @NotNull
    public static OtpErlangTuple quotedFunctionCall(final OtpErlangObject quotedQualifiedIdentifier, final OtpErlangList metadata, final OtpErlangObject... arguments) {
        return new OtpErlangTuple(
                new OtpErlangObject[] {
                        quotedQualifiedIdentifier,
                        metadata,
                        quotedFunctionArguments(arguments)
                }
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedRightOperand(@NotNull final ElixirStabOperation stabOperation) {
        PsiElement rightOperand = stabOperation.rightOperand();
        OtpErlangObject quotedRightOperand;

        if (rightOperand != null && rightOperand instanceof Quotable) {
            Quotable quotableRightOperand = (Quotable) rightOperand;
            quotedRightOperand = quotableRightOperand.quote();
        } else {
            quotedRightOperand = NIL;
        }

        return quotedRightOperand;
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedVariable(@NotNull final PsiElement variable) {
        return quotedVariable(
                variable.getText(),
                metadata(variable)
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedVariable(@NotNull final String identifier, @NotNull final OtpErlangList metadata) {
        return quotedVariable(
                new OtpErlangAtom(identifier),
                metadata
        );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedVariable(@NotNull final OtpErlangObject quotedIdentifier, @NotNull final OtpErlangList metadata) {
      return quotedVariable(
              quotedIdentifier,
              metadata,
              NIL
      );
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quotedVariable(@NotNull final OtpErlangObject quotedIdentifier, @NotNull final OtpErlangList metadata, @NotNull final OtpErlangObject context) {
        return new OtpErlangTuple(
                new OtpErlangObject[] {
                        quotedIdentifier,
                        metadata,
                        context
                }
        );
    }

    @Contract(pure = true)
    @NotNull
    public static boolean recursiveKernelImport(@NotNull QualifiableAlias qualifiableAlias,
                                                @NotNull PsiElement maxScope) {
        boolean recursiveKernelImport = false;

        if (maxScope instanceof ElixirFile) {
            ElixirFile elixirFile = (ElixirFile) maxScope;

            if (elixirFile.getName().equals("kernel.ex")) {
                String qualifiableAliasName = qualifiableAlias.getName();

                recursiveKernelImport = qualifiableAliasName != null && qualifiableAliasName.equals(KERNEL);
            }
        }

        return recursiveKernelImport;
    }

    @Contract(pure = true)
    @NotNull
    public static int resolvedFinalArity(@NotNull final Call call) {
        Integer resolvedFinalArity = call.resolvedSecondaryArity();

        if (resolvedFinalArity == null) {
            resolvedFinalArity = call.resolvedPrimaryArity();
        }

        if (resolvedFinalArity == null) {
            resolvedFinalArity = 0;
        }

        return resolvedFinalArity;
    }

    @Contract(pure = true)
    @NotNull
    public static int resolvedFinalArity(@NotNull final org.elixir_lang.psi.call.StubBased<Stub> stubBased) {
        Stub stub = stubBased.getStub();
        Integer resolvedFinalArity;

        if (stub != null) {
            resolvedFinalArity = stub.resolvedFinalArity();
        } else {
            resolvedFinalArity = resolvedFinalArity((Call) stubBased);
        }

        if (resolvedFinalArity == null) {
            resolvedFinalArity = 0;
        }

        return resolvedFinalArity;
    }

    @Contract(pure = true)
    @NotNull
    public static IntRange resolvedFinalArityRange(@NotNull final Call call) {
        IntRange arityRange;
        PsiElement[] finalArguments = ElixirPsiImplUtil.finalArguments(call);

        if (finalArguments != null) {
            int defaultCount = defaultArgumentCount(finalArguments);
            int maximum = finalArguments.length;
            int minimum = maximum - defaultCount;
            arityRange = new IntRange(minimum, maximum);
        } else {
            arityRange = new IntRange(0);
        }

        return arityRange;
    }

    /**
     * Similar to {@link functionName}, but takes into account `import`s.
     *
     * @return
     */
    @Nullable
    public static String resolvedFunctionName(@NotNull @SuppressWarnings("unused") final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        // TODO handle resolving function name from module attribute's declaration
        return null;
    }

    /**
     * Similar to {@link functionName}, but takes into account `import`s.
     *
     * @return
     */
    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String resolvedFunctionName(@NotNull @SuppressWarnings("unused") final DotCall dotCall) {
        // TODO handle resolving function name from potential capture when declaring variable
        return null;
    }

    /**
     * Similar to {@link #functionName(Call)}}, but takes into account `import`s.
     *
     * @return
     */
    @Contract(pure = true)
    @NotNull
    public static String resolvedFunctionName(@NotNull final Call call) {
        // TODO handle `import`s
        return call.functionName();
    }

    @Contract(pure = true)
    @NotNull
    public static String resolvedFunctionName(@NotNull final org.elixir_lang.psi.call.StubBased<Stub> stubBased) {
        Stub stub = stubBased.getStub();
        String resolvedFunctionName;

        if (stub != null) {
            resolvedFunctionName = stub.resolvedFunctionName();
        } else {
            resolvedFunctionName = resolvedFunctionName((Call) stubBased);
        }

        //noinspection ConstantConditions
        return resolvedFunctionName;
    }

    @Nullable
    public static String resolvedFunctionName(@NotNull final UnqualifiedNoArgumentsCall<Stub> unqualifiedNoArgumentsCall) {
        Stub stub = unqualifiedNoArgumentsCall.getStub();
        String resolvedFunctionName;

        if (stub != null) {
            resolvedFunctionName = stub.resolvedFunctionName();
        } else {
            // TODO handle `import`s and determine whether actually local variable
            resolvedFunctionName = unqualifiedNoArgumentsCall.functionName();
        }

        return resolvedFunctionName;
    }

    /**
     * Similar to {@link moduleName}, but takes into account `alias`es and `import`s.
     *
     * @param element
     * @param newName
     * @return
     */
    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String resolvedModuleName(@NotNull @SuppressWarnings("unused") final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall) {
        // TODO handle resolving module name from module attribute's declaration
        return null;
    }

    /**
     * Similar to {@link moduleName}, but takes into account `alias`es and `import`s.
     *
     * @param element
     * @param newName
     * @return
     */
    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static String resolvedModuleName(@NotNull @SuppressWarnings("unused") final DotCall dotCall) {
        // TODO handle resolving module name from any capture from variable declaration
        return null;
    }

    @Contract(pure = true)
    @NotNull
    public static String resolvedModuleName(@NotNull final Infix infix) {
        /* TODO handle resolving module name from imports.  Assume KERNEL for now, but some are actually from
           Bitwise */
        return KERNEL;
    }

    @Contract(pure = true)
    @NotNull
    public static String resolvedModuleName(@NotNull final NotIn notIn) {
        return KERNEL;
    }

    @Contract(pure = true)
    @NotNull
    public static String resolvedModuleName(@NotNull final Prefix prefix) {
        /* TODO handle resolving module name from imports.  Assume KERNEL for now. */
        return KERNEL;
    }

    /**
     * Similar to {@link moduleName}, but takes into account `alias`es and `import`s.
     *
     * @param qualifiedNoArgumentsCall
     * @return
     */
    @NotNull
    public static String resolvedModuleName(@NotNull final org.elixir_lang.psi.call.qualification.Qualified qualified) {
        Stub stub = null;

        if (qualified instanceof org.elixir_lang.psi.call.StubBased) {
            org.elixir_lang.psi.call.StubBased<Stub> qualifiedNamedCall = (org.elixir_lang.psi.call.StubBased<Stub>) qualified;
            stub = qualifiedNamedCall.getStub();
        }

        String resolvedModuleName;

        if (stub != null) {
            resolvedModuleName = stub.resolvedModuleName();
        } else {
            // TODO handle `alias`es and `import`s
            resolvedModuleName = stripElixirPrefix(qualified.moduleName());
        }

        //noinspection ConstantConditions
        return resolvedModuleName;
    }

    /**
     * Similar to {@link moduleName}, but takes into account `alias`es and `import`s.
     *
     * @param element
     * @param newName
     * @return
     */
    @NotNull
    public static String resolvedModuleName(@NotNull final Unqualified unqualified) {
        Call unqualifiedCall = (Call) unqualified;
        Stub stub = null;

        if (unqualifiedCall instanceof org.elixir_lang.psi.call.StubBased) {
            org.elixir_lang.psi.call.StubBased<Stub> unqualifiedNamed = (org.elixir_lang.psi.call.StubBased<Stub>) unqualifiedCall;
            stub = unqualifiedNamed.getStub();
        }

        String resolvedModuleName;

        if (stub != null) {
            resolvedModuleName = stub.resolvedModuleName();
        } else {
            // TODO handle `import`s
            resolvedModuleName = KERNEL;
        }

        //noinspection ConstantConditions
        return resolvedModuleName;
    }

    /**
     * Similar to {@link moduleName}, but takes into account `alias`es and `import`s.
     *
     * @param element
     * @param newName
     * @return
     */
    @NotNull
    public static String resolvedModuleName(@NotNull @SuppressWarnings("unused") final UnqualifiedNoArgumentsCall unqualifiedNoArgumentsCall) {
        // TODO handle `import`s and determine whether actually a local variable
        return KERNEL;
    }

    @Contract(pure = true)
    @Nullable
    public static Integer resolvedPrimaryArity(@NotNull final Call call) {
        Integer primaryArity = call.primaryArity();
        Integer resolvedPrimaryArity = primaryArity;

        /* do block and piping attach to the outer most parentheses, only count do block and piping for primary if there
           are no secondary. */
        if (call.secondaryArity() == null) {
            if (call.getDoBlock() != null) {
                if (primaryArity == null) {
                    resolvedPrimaryArity = 1;
                } else {
                    resolvedPrimaryArity += 1;
                }
            }

            PsiElement parent = computeReadAction(call::getParent);

            if (isPipe(parent)) {
                Arrow parentPipeOperation = (Arrow) parent;
                PsiElement pipedInto = parentPipeOperation.rightOperand();

                /* only the right operand has its arity increased because it is the operand that has the output of the
                   left operand prepended to its arguments */
                if (pipedInto != null && call.isEquivalentTo(pipedInto)) {
                    if (primaryArity == null) {
                        resolvedPrimaryArity = 1;
                    } else {
                        resolvedPrimaryArity += 1;
                    }
                }
            }
        }

        return resolvedPrimaryArity;
    }

    @Contract(pure = true)
    @Nullable
    public static Integer resolvedSecondaryArity(@NotNull final Call call) {
        Integer secondaryArity = call.secondaryArity();
        Integer resolvedSecondaryArity = secondaryArity;

        if (secondaryArity != null) {
            if (call.getDoBlock() != null) {
                resolvedSecondaryArity += 1;
            }

            // TODO handle piping
        }

        return resolvedSecondaryArity;
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable rightOperand(ElixirStabOperation stabOperation) {
        return stabOperation.getStabBody();
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable rightOperand(Infix infix) {
        return org.elixir_lang.psi.operation.infix.Normalized.rightOperand(infix);
    }

    @Contract(pure = true)
    @Nullable
    public static Quotable rightOperand(@NotNull NotIn notIn) {
        return org.elixir_lang.psi.operation.not_in.Normalized.rightOperand(notIn);
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull final DotCall dotCall) {
        List<ElixirParenthesesArguments> parenthesesArgumentsList = dotCall.getParenthesesArgumentsList();
        PsiElement[] arguments;

        if (parenthesesArgumentsList.size() < 2) {
            arguments = null;
        } else {
            ElixirParenthesesArguments parenthesesArguments = parenthesesArgumentsList.get(1);
            arguments = parenthesesArguments.arguments();
        }

        return arguments;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull @SuppressWarnings("unused") final Infix infix) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull @SuppressWarnings("unused") final None none) {
        return null;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull @SuppressWarnings("unused") final NotIn notIn) {
        return null;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull @SuppressWarnings("unused") final NoParentheses noParentheses) {
        return null;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull final Parentheses parentheses) {
        ElixirMatchedParenthesesArguments matchedParenthesesArguments = parentheses.getMatchedParenthesesArguments();
        List<ElixirParenthesesArguments> parenthesesArgumentsList = matchedParenthesesArguments.getParenthesesArgumentsList();
        PsiElement[] arguments;

        if (parenthesesArgumentsList.size() < 2) {
            arguments = null;
        } else {
            ElixirParenthesesArguments parenthesesArguments = parenthesesArgumentsList.get(1);
            arguments = parenthesesArguments.arguments();
        }

        return arguments;
    }

    @Contract(pure = true, value = "_ -> null")
    @Nullable
    public static PsiElement[] secondaryArguments(@NotNull @SuppressWarnings("unused") final Prefix prefix) {
        return null;
    }

    @Contract(pure = true)
    @Nullable
    public static Integer secondaryArity(@NotNull final Call call) {
        PsiElement[] secondaryArguments = call.secondaryArguments();
        Integer secondaryArity = null;

        if (secondaryArguments != null) {
            secondaryArity = secondaryArguments.length;
        }

        return secondaryArity;
    }

    @NotNull
    public static PsiElement setName(@NotNull PsiElement element, @NotNull String newName) {
        return null;
    }


    @NotNull
    public static PsiElement setName(@NotNull final AtUnqualifiedNoParenthesesCall atUnqualifiedNoParenthesesCall,
                                     @NotNull final String newName) {
        AtUnqualifiedNoParenthesesCall newAtUnqualifiedNoParenthesesCall = ElementFactory.createModuleAttributeDeclaration(
                atUnqualifiedNoParenthesesCall.getProject(),
                newName,
                "dummy_value"
        );

        ASTNode nameNode = atUnqualifiedNoParenthesesCall.getAtIdentifier().getNode();
        ASTNode newNameNode = newAtUnqualifiedNoParenthesesCall.getAtIdentifier().getNode();

        ASTNode node = atUnqualifiedNoParenthesesCall.getNode();
        node.replaceChild(nameNode, newNameNode);

        return atUnqualifiedNoParenthesesCall;
    }

    @NotNull
    public static PsiElement setName(@NotNull ElixirVariable variable, @NotNull String newName) {
        return null;
    }

    @NotNull
    public static PsiElement setName(@NotNull final org.elixir_lang.psi.call.Named named,
                                     @NotNull final String newName) {
        PsiElement functionNameElement = named.functionNameElement();
        Call newFunctionNameElementCall = ElementFactory.createUnqualifiedNoArgumentsCall(named.getProject(), newName);

        ASTNode nameNode = functionNameElement.getNode();
        ASTNode newNameNode = newFunctionNameElementCall.functionNameElement().getNode();

        ASTNode node = nameNode.getTreeParent();
        node.replaceChild(nameNode, newNameNode);

        return named;
    }

    public static char sigilName(@NotNull org.elixir_lang.psi.Sigil sigil) {
        ASTNode sigilNode = sigil.getNode();
        ASTNode[] childNodes = sigilNode.getChildren(null);
        ASTNode nameNode = childNodes[1];
        CharSequence chars = nameNode.getChars();
        return chars.charAt(0);
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement stripAccessExpression(@NotNull PsiElement maybeWrapped) {
        PsiElement unwrapped = maybeWrapped;

        if (maybeWrapped instanceof ElixirAccessExpression) {
            unwrapped = stripOnlyChildParent(maybeWrapped);
        }

        return unwrapped;
    }

    @Contract(pure = true)
    @NotNull
    public static PsiElement stripOnlyChildParent(@NotNull PsiElement parent) {
        PsiElement unwrapped = parent;
        PsiElement[] children = parent.getChildren();

        if (children.length == 1) {
            unwrapped = children[0];
        }

        return unwrapped;
    }

    public static char terminator(@NotNull SigilLine sigilLine) {
        ASTNode node = sigilLine.getNode();
        ASTNode[] childNodes = node.getChildren(null);
        ASTNode terminatorNode = childNodes[4];
        CharSequence chars = terminatorNode.getChars();

        return chars.charAt(0);
    }

    @NotNull
    public static IElementType validElementType(@SuppressWarnings("unused") @NotNull ElixirBinaryDigits binaryDigits) {
        return ElixirTypes.VALID_BINARY_DIGITS;
    }

    @Contract(pure = true)
    @NotNull
    public static IElementType validElementType(@NotNull @SuppressWarnings("unused") ElixirDecimalDigits decimalDigits) {
        return ElixirTypes.VALID_DECIMAL_DIGITS;
    }

    @NotNull
    public static IElementType validElementType(@SuppressWarnings("unused") @NotNull ElixirHexadecimalDigits hexadecimalDigits) {
        return ElixirTypes.VALID_HEXADECIMAL_DIGITS;
    }

    @NotNull
    public static IElementType validElementType(@NotNull @SuppressWarnings("unused") ElixirOctalDigits octalDigits) {
        return ElixirTypes.VALID_OCTAL_DIGITS;
    }

    @Nullable
    public static IElementType validElementType(@NotNull @SuppressWarnings("unused") ElixirUnknownBaseDigits unknownBaseDigits) {
        return null;
    }

    /*
     * Private static methods
     */

    public static ASTNode[] childNodes(PsiElement parentElement) {
        ASTNode parentNode = parentElement.getNode();
        return parentNode.getChildren(null);
    }

    private static void addMergedFragments(@NotNull Queue<ASTNode> mergedNodes, IElementType fragmentType, StringBuilder fragmentStringBuilder, PsiManager manager) {
        if (fragmentStringBuilder != null) {
            ASTNode charListFragment = Factory.createSingleLeafElement(
                    fragmentType,
                    fragmentStringBuilder.toString(),
                    0,
                    fragmentStringBuilder.length(),
                    null,
                    manager
            );
            mergedNodes.add(charListFragment);
        }
    }

    @Contract(pure = true)
    @Nullable
    private static Call macroDefinitionClauseForArgument(Call callDefinitionClause) {
        Call macroDefinitionClause = null;
        PsiElement parent = callDefinitionClause.getParent();

        if (parent instanceof ElixirMatchedWhenOperation) {
            PsiElement grandParent =  parent.getParent();

            if (grandParent instanceof ElixirNoParenthesesOneArgument) {
                PsiElement greatGrandParent = grandParent.getParent();

                if (greatGrandParent instanceof Call) {
                    Call greatGrandParentCall = (Call) greatGrandParent;

                    if (CallDefinitionClause.isMacro(greatGrandParentCall)) {
                        macroDefinitionClause = greatGrandParentCall;
                    }
                }
            }
        }

        return macroDefinitionClause;
    }

    @NotNull
    @Contract(pure = true)
    private static Queue<ASTNode> mergeFragments(@NotNull Deque<ASTNode> unmergedNodes, IElementType fragmentType, @NotNull PsiManager manager) {
        Queue<ASTNode> mergedNodes = new LinkedList<ASTNode>();
        StringBuilder fragmentStringBuilder = null;

        for (ASTNode unmergedNode : unmergedNodes) {
            if (unmergedNode.getElementType() == fragmentType) {
                if (fragmentStringBuilder == null) {
                    fragmentStringBuilder = new StringBuilder();
                }

                String fragment = unmergedNode.getText();
                fragmentStringBuilder.append(fragment);
            } else {
                addMergedFragments(mergedNodes, fragmentType, fragmentStringBuilder, manager);
                fragmentStringBuilder = null;
                mergedNodes.add(unmergedNode);
            }
        }

        addMergedFragments(mergedNodes, fragmentType, fragmentStringBuilder, manager);

        return mergedNodes;
    }

    /**
     * Finds modular ({@code defmodule}, {@code defimpl}, or {@code defprotocol}) for the qualifier of
     * {@code qualified}.
     *
     * @param qualified a qualified expression
     * @return {@code null} if the modular cannot be resolved, such as when the qualifying Alias is invalid or is an
     *   unparsed module like {@code Kernel} or {@code Enum} OR if the qualified isn't an Alias.
     */
    @Contract(pure = true)
    @Nullable
    public static Call qualifiedToModular(@NotNull final org.elixir_lang.psi.call.qualification.Qualified qualified) {
        return maybeModularNameToModular(qualified.qualifier(), qualified.getContainingFile());
    }

    @NotNull
    private static void queueChildNodes(@NotNull HeredocLine line, IElementType fragmentType, int prefixLength, @NotNull Queue<ASTNode> heredocDescendantNodes) {
        ElixirHeredocLinePrefix heredocLinePrefix = line.getHeredocLinePrefix();

        ASTNode excessWhitespace = heredocLinePrefix.excessWhitespace(fragmentType, prefixLength);

        if (excessWhitespace != null) {
            heredocDescendantNodes.add(excessWhitespace);
        }

        Body body = line.getBody();
        Level level = getNonNullRelease(line).level();
        ASTNode[] childNodes = childNodes(body);

        if (level.compareTo(V_1_3) < 0 &&
                childNodes.length >= 1 &&
                childNodes[childNodes.length - 1].getElementType().equals(ElixirTypes.ESCAPED_EOL)) {
            heredocDescendantNodes.addAll(Arrays.asList(childNodes).subList(0, childNodes.length - 1));
        } else {
            Collections.addAll(heredocDescendantNodes, childNodes);
            ASTNode eolNode = Factory.createSingleLeafElement(
                    fragmentType,
                    "\n",
                    0,
                    1,
                    null,
                    line.getManager()
            );
            heredocDescendantNodes.add(eolNode);
        }
    }

    @Contract(pure = true)
    @NotNull
    public static OtpErlangObject quote(@NotNull Sigil sigil, @NotNull OtpErlangObject quotedContent) {
        char sigilName = sigil.sigilName();

        OtpErlangList sigilMetadata = metadata(sigil);
        OtpErlangTuple sigilBinary;

        if (quotedContent instanceof OtpErlangTuple) {
            sigilBinary = (OtpErlangTuple) quotedContent;
        } else {
            sigilBinary = quotedFunctionCall("<<>>", sigilMetadata, quotedContent);
        }

        ElixirSigilModifiers sigilModifiers = sigil.getSigilModifiers();
        OtpErlangObject quotedModifiers = sigilModifiers.quote();

        return quotedFunctionCall(
                "sigil_" + sigilName,
                sigilMetadata,
                sigilBinary,
                quotedModifiers
        );
    }

    @NotNull
    public static List<Integer> addEscapedCharacterCodePoints(@NotNull Quote parent,
                                                              @Nullable List<Integer> codePointList,
                                                              @NotNull ASTNode child) {
        return ParentImpl.addEscapedCharacterCodePoints(parent, codePointList, child);
    }

    @NotNull
    public static List<Integer> addEscapedCharacterCodePoints(@NotNull Sigil parent,
                                                              @Nullable List<Integer> codePointList,
                                                              @NotNull ASTNode child) {
        return ParentImpl.addEscapedCharacterCodePoints(parent, codePointList, child);
    }

    @NotNull
    public static List<Integer> addEscapedEOL(@NotNull Parent parent,
                                              @Nullable List<Integer> maybeCodePointList,
                                              @NotNull ASTNode child) {
        return ParentImpl.addEscapedEOL(parent, maybeCodePointList, child);
    }

    @NotNull
    public static List<Integer> addFragmentCodePoints(@NotNull Parent parent,
                                                      @Nullable List<Integer> codePointList,
                                                      @NotNull ASTNode child) {
        return ParentImpl.addFragmentCodePoints(parent, codePointList, child);
    }

    @NotNull
    public static List<Integer> addHexadecimalEscapeSequenceCodePoints(@NotNull Quote parent,
                                                                       @Nullable List<Integer> codePointList,
                                                                       @NotNull ASTNode child) {
        return ParentImpl.addHexadecimalEscapeSequenceCodePoints(parent, codePointList, child);
    }

    @NotNull
    public static List<Integer> addHexadecimalEscapeSequenceCodePoints(@NotNull Sigil parent,
                                                                       @Nullable List<Integer> codePointList,
                                                                       @NotNull ASTNode child) {
        return ParentImpl.addHexadecimalEscapeSequenceCodePoints(parent, codePointList, child);
    }

    /**
     * @return {@link Call} for the {@code defmodule}, {@code defimpl}, or {@code defprotocol} that defines
     *   {@code maybeAlias} after it is resolved through any {@code alias}es.
     */
    @Contract(pure = true)
    @Nullable
    public static Call maybeModularNameToModular(@NotNull final PsiElement maybeModularName, @NotNull PsiElement maxScope) {
        PsiElement strippedMaybeModuleName = stripAccessExpression(maybeModularName);

        Call modular = null;

        if (strippedMaybeModuleName instanceof ElixirAtom) {
            ElixirAtom atom = (ElixirAtom) strippedMaybeModuleName;
            PsiReference reference = atom.getReference();

            if (reference != null) {
                final PsiElement resolved = reference.resolve();

                if (resolved != null && resolved instanceof Call) {
                    Call call = (Call) resolved;

                    if (isModular(call)) {
                        modular = call;
                    }
                }
            }
        } else if (strippedMaybeModuleName instanceof QualifiableAlias) {
            QualifiableAlias qualifiableAlias = (QualifiableAlias) strippedMaybeModuleName;

            if (!recursiveKernelImport(qualifiableAlias, maxScope)) {
                /* need to construct reference directly as qualified aliases don't return a reference except for the
                   outermost */
                PsiPolyVariantReference reference = getReference(qualifiableAlias, maxScope);
                modular = aliasToModular(qualifiableAlias, reference);
            }
        }

        return modular;
    }

    @Contract(pure = true)
    @Nullable
    private static Call aliasToModular(@NotNull QualifiableAlias alias,
                                       @NotNull PsiReference startingReference) {
        PsiElement fullyResolvedAlias = fullyResolveAlias(alias, startingReference);
        Call modular = null;

        if (fullyResolvedAlias instanceof Call) {
           Call fullyResolvedAliasCall = (Call) fullyResolvedAlias;

           if (isModular(fullyResolvedAliasCall)) {
               modular = fullyResolvedAliasCall;
           }
        }

        return modular;
    }

    @NotNull
    private static OtpErlangObject quotedChildNodes(@NotNull Parent parent, @NotNull ASTNode... children) {
        return quotedChildNodes(parent, metadata(parent), children);
    }

    private static OtpErlangObject quotedChildNodes(@NotNull Parent parent, @NotNull OtpErlangList metadata, @NotNull ASTNode... children) {
        OtpErlangObject quoted;

        final int childCount = children.length;

        if (childCount == 0) {
            quoted = parent.quoteEmpty();
        } else {
            List<OtpErlangObject> quotedParentList = new LinkedList<OtpErlangObject>();
            List<Integer> codePointList = null;

            for (ASTNode child : children) {
                IElementType elementType = child.getElementType();

                if (elementType == parent.getFragmentType()) {
                    codePointList = parent.addFragmentCodePoints(codePointList, child);
                } else if (elementType == ElixirTypes.ESCAPED_CHARACTER) {
                    codePointList = parent.addEscapedCharacterCodePoints(codePointList, child);
                } else if (elementType == ElixirTypes.ESCAPED_EOL) {
                    codePointList = parent.addEscapedEOL(codePointList, child);
                } else if (elementType == ElixirTypes.HEXADECIMAL_ESCAPE_PREFIX) {
                    codePointList = addChildTextCodePoints(codePointList, child);
                } else if (elementType == ElixirTypes.INTERPOLATION) {
                    if (codePointList != null) {
                        quotedParentList.add(elixirString(codePointList));
                        codePointList = null;
                    }

                    ElixirInterpolation childElement = (ElixirInterpolation) child.getPsi();
                    quotedParentList.add(childElement.quote());
                } else if (elementType == ElixirTypes.QUOTE_HEXADECIMAL_ESCAPE_SEQUENCE ||
                           elementType == ElixirTypes.SIGIL_HEXADECIMAL_ESCAPE_SEQUENCE) {
                    codePointList = parent.addHexadecimalEscapeSequenceCodePoints(codePointList, child);
                } else {
                    throw new NotImplementedException("Can't quote " + child);
                }
            }

            if (codePointList !=  null && quotedParentList.isEmpty()) {
                quoted = parent.quoteLiteral(codePointList);
            } else {
                if (codePointList != null) {
                    quotedParentList.add(elixirString(codePointList));
                }

                OtpErlangObject[] quotedStringElements = new OtpErlangObject[quotedParentList.size()];
                quotedParentList.toArray(quotedStringElements);

                OtpErlangTuple binaryConstruction = quotedFunctionCall("<<>>", metadata, quotedStringElements);
                quoted = parent.quoteBinary(binaryConstruction);
            }
        }

        return quoted;
    }

    private static OtpErlangObject quoteInBase(@NotNull String string, int base) {
        BigInteger parsedInteger = new BigInteger(string, base);

        return new OtpErlangLong(parsedInteger);
    }

    private static String compactDigits(List<Digits> digitsList) {
        StringBuilder builtString = new StringBuilder();

        for (Digits digits : digitsList) {
            builtString.append(digits.getText());
        }

        return builtString.toString();
    }

    @Contract(pure = true)
    @Nullable
    public static PsiElement siblingExpression(@NotNull PsiElement element,
                                                @NotNull com.intellij.util.Function<PsiElement, PsiElement> function) {
        PsiElement expression = element;

        do {
            expression = function.fun(expression);
        } while (expression instanceof ElixirEndOfExpression ||
                expression instanceof LeafPsiElement ||
                expression instanceof PsiComment ||
                expression instanceof PsiWhiteSpace);

        return expression;
    }

    /**
     * If {@code name} is {@code "unquote"} then the {@link Call#primaryArguments()} single argument is added to the
     * name.
     */
    @Nullable
    public static String unquoteName(@NotNull PsiElement named, @Nullable String name) {
        String unquotedName = name;

        if (named instanceof Call && UNQUOTE.equals(name)) {
            Call namedElementCall = (Call) named;

            PsiElement[] primaryArguments = namedElementCall.primaryArguments();

            if (primaryArguments != null && primaryArguments.length == 1) {
                unquotedName += "(" + primaryArguments[0].getText() + ")";
            }
        }

        return unquotedName;
    }

    /**
     * Unwraps <code>when</code> from left end of noParenthesesArguments in stabNoParenthesesSignature so that the when acts as a guard for the signature instead of a guard on the last positional argument.
     *
     * @param
     * @return
     * @see <a href="https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L276-L277"><code>unwrap_when</code> in <code>stab_expr</code></a>
     * @see <a href="https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L716-L722"><code>unwrap_when</code></a>
     */
    private static OtpErlangObject[] unwrapWhen(OtpErlangObject[] quotedArguments) {
        // https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L717
        OtpErlangObject last = quotedArguments[quotedArguments.length - 1];
        // https://github.com/elixir-lang/elixir/blob/de39bbaca277002797e52ffbde617ace06233a2b/lib/elixir/src/elixir_parser.yrl#L720-L721
        OtpErlangObject[] unwrapped = quotedArguments;

        // { _, _, _}
        if (Macro.INSTANCE.isExpression(last)) {
            OtpErlangTuple expression = (OtpErlangTuple) last;
            OtpErlangObject receiver = expression.elementAt(0);

            // {'when', _, _ }
            if (receiver.equals(WHEN)) {
                OtpErlangObject operands = expression.elementAt(2);

                // is_list(End)
                if (operands instanceof OtpErlangList) {
                    OtpErlangList operandList = (OtpErlangList) operands;

                    // Have to check for two element so that unwrap_when doesn't happen recursively as the unwrapped version of when will have more than 2 arguments, which is only seen in stabSignatures.
                    // [_, _] = End
                    if (operandList.arity() == 2) {
                        OtpErlangObject[] unwrappedArguments = new OtpErlangObject[quotedArguments.length - 1 + 2];

                        // Start
                        System.arraycopy(quotedArguments, 0, unwrappedArguments, 0, quotedArguments.length - 1);
                        // ++ End
                        System.arraycopy(operandList.elements(), 0, unwrappedArguments, quotedArguments.length - 1, 2);

                        unwrapped = new OtpErlangObject[1];

                        unwrapped[0] = quotedFunctionCall(
                                receiver,
                                Macro.INSTANCE.metadata(expression),
                                unwrappedArguments
                        );
                    }
                }
            }
        }

        return unwrapped;
    }

    @Nullable
    public static String getModuleName(PsiElement elem) {
        final Predicate<PsiElement> isModuleName = (PsiElement c) ->
          c instanceof MaybeModuleName && ((MaybeModuleName) c).isModuleName();

        PsiElement moduleDefinition = PsiTreeUtil.findFirstParent(elem, e ->
          Stream.of(e.getChildren()).anyMatch(isModuleName)
        );
        if (moduleDefinition != null) {
            Optional<PsiElement> moduleName =
              Stream.of(moduleDefinition.getChildren())
                .filter(isModuleName)
                .findAny();
            if (moduleName.isPresent()) {
                String parentModuleName = getModuleName(moduleDefinition.getParent());
                if (parentModuleName != null) {
                    return parentModuleName + "." + moduleName.get().getText();
                } else {
                    return moduleName.get().getText();
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}

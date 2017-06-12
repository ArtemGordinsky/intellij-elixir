package org.elixir_lang;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.elixir_lang.code_style.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormattingTest extends LightCodeInsightFixtureTestCase {
    private com.intellij.psi.codeStyle.CodeStyleSettings temporaryCodeStyleSettings;

    @Override
    protected String getTestDataPath() {
        return "testData/org/elixir_lang/formatting";
    }

    private void restoreStyleSettings() {
        CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }

    private void setTestStyleSettings() {
        CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(getProject());
        com.intellij.psi.codeStyle.CodeStyleSettings codeStyleSettings = settingsManager.getCurrentSettings();
        assertNotNull(codeStyleSettings);
        temporaryCodeStyleSettings = codeStyleSettings.clone();
        com.intellij.psi.codeStyle.CodeStyleSettings.IndentOptions indentOptions =
                temporaryCodeStyleSettings.getIndentOptions(ElixirFileType.INSTANCE);
        assertNotNull(indentOptions);
        settingsManager.setTemporarySettings(temporaryCodeStyleSettings);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setTestStyleSettings();
    }

    @Override
    public void tearDown() throws Exception {
        restoreStyleSettings();
        super.tearDown();
    }

    public void testAnonymousFunctionWithMultipleClauses() {
        assertFormatted("anonymous_function_with_multiple_clauses.ex");
    }

    public void testAnonymousFunctionAsLastArgument() {
        assertFormatted("anonymous_function_as_last_argument.ex");
    }

    public void testAnonymousFunctionAsOnlyArgument() {
        assertFormatted("anonymous_function_as_only_argument.ex");
    }

    public void testAnonymousFunctionWithMultipleClausesChop() {
        myFixture.configureByFile("anonymous_function_with_multiple_clauses_chop_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("anonymous_function_with_multiple_clauses_chop_after.ex");
    }

    // TODO fix indentation of body after wrapping: it is relative to stab operation start instead start of line
    public void testAnonymousFunctionWithSingleClause() {
        assertFormatted("anonymous_function_with_single_clause.ex");
    }

    public void testNoSpaceAfterAtOperator() {
        myFixture.configureByFile("space_after_at_operator.ex");

        reformatFixture();

        myFixture.checkResultByFile("no_space_after_at_operator.ex");
    }

    public void testCaptureAndSymbolOperatorsWithoutSpaceAfterCapture() {
        assertFormatted(
                "capture_and_symbol_operators_without_space_after_capture.ex",
                () -> {
                    temporaryCodeStyleSettings
                            .getCustomSettings(CodeStyleSettings.class)
                            .SPACE_AFTER_CAPTURE_OPERATOR = false;
                    temporaryCodeStyleSettings
                            .getCommonSettings(ElixirLanguage.INSTANCE)
                            .SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
                }
        );
    }

    public void testCaptureNameArityWithSpaceAroundMultiplication() {
        assertFormatted(
                "capture_name_arity_with_space_around_multiplication.ex",
                () -> {
                    temporaryCodeStyleSettings
                            .getCommonSettings(ElixirLanguage.INSTANCE)
                            .SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
                }
        );
    }

    public void testCaptureQualifierDotNameArityWithSpaceAroundMultiplication() {
        assertFormatted(
                "capture_qualifier_dot_name_arity_with_space_around_multiplication.ex",
                () -> {
                    temporaryCodeStyleSettings
                            .getCommonSettings(ElixirLanguage.INSTANCE)
                            .SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
                }
        );
    }

    public void testCaseAtEndOfPipeline() {
        assertFormatted("case_at_end_of_pipeline.ex");
    }

    public void testCommentIndentAfterDoInDoBlock() {
        assertFormatted("comment_indent_after_do_in_do_block.ex");
    }

    public void testCommentIndentAfterFn() {
        assertFormatted("comment_indent_after_fn.ex");
    }

    public void testIndentWithoutOverrides() {
        myFixture.configureByFile("indent_without_override_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("indent_without_override_after.ex");
    }

    public void testWhenWrapsWithRightOperand() {
        myFixture.configureByFile("when_wraps_with_right_operand_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("when_wraps_with_right_operand_after.ex");
    }

    public void testWithSpaceAroundAdditionOperators() {
        myFixture.configureByFile("without_space_around_addition_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_ADDITIVE_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_addition_operators.ex");
    }

    public void testWithoutSpaceAroundAdditionOperators() {
        myFixture.configureByFile("with_space_around_addition_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_ADDITIVE_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_addition_operators.ex");
    }

    public void testSpaceAfterAfterKeyword() {
        myFixture.configureByFile("incorrect_spaces_after_after_keyword.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_after_after_keyword.ex");
    }

    public void testCaptureRightOperandWithoutSpaceAroundAndOperators() {
        myFixture.configureByFile("capture_right_operand_without_space_around_and_operators_before.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_AND_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("capture_right_operand_without_space_around_and_operators_after.ex");
    }

    public void testWithSpaceAroundAndOperators() {
        myFixture.configureByFile("without_space_around_and_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_AND_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_and_operators.ex");
    }

    public void testWithoutSpaceAroundAndOperators() {
        myFixture.configureByFile("with_space_around_and_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_AND_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_and_operators.ex");
    }

    public void testWithSpaceAroundArrowOperators() {
        myFixture.configureByFile("without_space_around_arrow_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_ARROW_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_arrow_operators.ex");
    }

    public void testWithoutSpaceAroundArrowOperators() {
        myFixture.configureByFile("with_space_around_arrow_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_ARROW_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_arrow_operators.ex");
    }


    public void testWithSpaceAroundAssociationOperator() {
        myFixture.configureByFile("without_space_around_association_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_ASSOCIATION_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_association_operator.ex");
    }

    public void testWithoutSpaceAroundAssociationOperator() {
        myFixture.configureByFile("with_space_around_association_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_ASSOCIATION_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_association_operator.ex");
    }

    public void testWithSpaceAfterCaptureOperator() {
        myFixture.configureByFile("without_space_after_capture_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AFTER_CAPTURE_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_after_capture_operator.ex");
    }

    public void testWithoutSpaceAfterCaptureOperator() {
        myFixture.configureByFile("with_space_after_capture_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AFTER_CAPTURE_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_after_capture_operator.ex");
    }

    public void testSpaceAfterCatchKeyword() {
        myFixture.configureByFile("incorrect_spaces_after_catch_keyword.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_after_catch_keyword.ex");
    }

    public void testWithSpaceWithinBits() {
        myFixture.configureByFile("without_space_within_bits.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_WITHIN_BITS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_within_bits.ex");
    }

    public void testWithoutSpaceWithinBits() {
        myFixture.configureByFile("with_space_within_bits.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_WITHIN_BITS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_within_bits.ex");
    }

    public void testWithSpaceWithinBrackets() {
        myFixture.configureByFile("without_space_within_brackets.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_BRACKETS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_within_brackets.ex");
    }

    public void testWithoutSpaceWithinBrackets() {
        myFixture.configureByFile("with_space_within_brackets.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_BRACKETS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_within_brackets.ex");
    }

    public void testWithSpaceAfterComma() {
        myFixture.configureByFile("without_space_after_comma.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AFTER_COMMA = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_after_comma.ex");
    }

    public void testWithoutSpaceAfterComma() {
        myFixture.configureByFile("with_space_after_comma.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AFTER_COMMA = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_after_comma.ex");
    }

    public void testWithSpaceBeforeComma() {
        myFixture.configureByFile("without_space_before_comma.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_BEFORE_COMMA = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_before_comma.ex");
    }

    public void testWithoutSpaceBeforeComma() {
        myFixture.configureByFile("with_space_before_comma.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_BEFORE_COMMA = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_before_comma.ex");
    }

    public void testWithSpaceAroundComparisonOperators() {
        myFixture.configureByFile("without_space_around_comparison_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_EQUALITY_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_comparison_operators.ex");
    }

    public void testWithoutSpaceAroundComparisonOperators() {
        myFixture.configureByFile("with_space_around_comparison_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_EQUALITY_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_comparison_operators.ex");
    }

    public void testWithSpaceWithinCurlyBraces() {
        myFixture.configureByFile("without_space_within_curly_braces.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_BRACES = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_within_curly_braces.ex");
    }

    public void testWithoutSpaceWithinCurlyBraces() {
        myFixture.configureByFile("with_space_within_curly_braces.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_BRACES = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_within_curly_braces.ex");
    }

    public void testNoSpaceAroundDotOperator() {
        myFixture.configureByFile("incorrect_spaces_around_dot_operator.ex");

        reformatFixture();

        myFixture.checkResultByFile("no_spaces_around_dot_operator.ex");
    }

    public void testPipelineMatchAlignment() {
        String path = "pipeline_match_alignment.ex";

        myFixture.configureByFile(path);

        reformatFixture();

        myFixture.checkResultByFile(path);
    }

    public void testIndentBeforeElseKeyword() {
        myFixture.configureByFile("incorrect_indent_before_else_keyword.ex");

        reformatFixture();

        myFixture.checkResultByFile("indent_before_else_keyword.ex");
    }

    public void testIndentAfterElseKeyword() {
        myFixture.configureByFile("incorrect_indent_after_else_keyword.ex");

        reformatFixture();

        myFixture.checkResultByFile("indent_after_else_keyword.ex");
    }

    public void testHeredocAlignment() {
        myFixture.configureByFile("heredoc_alignment_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("heredoc_alignment_after.ex");
    }

    public void testHeredocAsLastArgument() {
        assertFormatted("heredoc_as_last_argument.ex");
    }

    public void testHeredocAsOnlyArgument() {
        assertFormatted("heredoc_as_only_argument.ex");
    }

    public void testHeredocBlankLines() {
        assertFormatted( "heredoc_blank_lines.ex");
    }

    public void testWithSpaceAroundInMatchOperators() {
        myFixture.configureByFile("without_space_around_in_match_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_IN_MATCH_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_in_match_operators.ex");
    }

    public void testWithoutSpaceAroundInMatchOperators() {
        myFixture.configureByFile("with_space_around_in_match_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_IN_MATCH_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_in_match_operators.ex");
    }

    public void testSpaceAroundInOperator() {
        myFixture.configureByFile("incorrect_spaces_around_in_operator.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_around_in_operator.ex");
    }

    public void testSpaceAfterKeywordPairColon() {
        myFixture.configureByFile("incorrect_spaces_after_keyword_pair_colon.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_after_keyword_pair_colon.ex");
    }

    public void testListIndent() {
        assertFormatted("list_indent.ex");
    }

    public void testMapConstructionArgumentsKeywordsAllWrapIfAnyWraps() {
        myFixture.configureByFile("map_construction_arguments_keywords_all_wrap_if_any_wraps_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("map_construction_arguments_keywords_all_wrap_if_any_wraps_after.ex");
    }

    public void testMapConstructionArgumentsAssociationsAllWrapIfAnyWraps() {
        myFixture.configureByFile("map_construction_arguments_associations_all_wrap_if_any_wraps_before.ex");

        reformatFixture();

        myFixture.checkResultByFile("map_construction_arguments_associations_all_wrap_if_any_wraps_after.ex");
    }

    public void testMapConstructionArgumentsAssociationsBaseAndKeywordsAllWrapIfAnyWraps() {
        myFixture.configureByFile(
                "map_construction_arguments_associations_base_and_keywords_all_wrap_if_any_wraps_before.ex"
        );

        reformatFixture();

        myFixture.checkResultByFile(
                "map_construction_arguments_associations_base_and_keywords_all_wrap_if_any_wraps_after.ex"
        );
    }

    public void testWithSpaceAroundMatchOperator() {
        myFixture.configureByFile("without_space_around_match_operator.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_ASSIGNMENT_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_match_operator.ex");
    }

    public void testWithoutSpaceAroundMatchOperator() {
        myFixture.configureByFile("with_space_around_match_operator.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_ASSIGNMENT_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_match_operator.ex");
    }

    public void testWithSpaceAroundMultiplicationOperators() {
        myFixture.configureByFile("without_space_around_multiplication_operators.ex");

        temporaryCodeStyleSettings
                .getCommonSettings(ElixirLanguage.INSTANCE)
                .SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_multiplication_operators.ex");
    }

    public void testWithoutSpaceAroundMultiplicationOperators() {
        myFixture.configureByFile("with_space_around_multiplication_operators.ex");

        temporaryCodeStyleSettings
                .getCommonSettings(ElixirLanguage.INSTANCE)
                .SPACE_AROUND_MULTIPLICATIVE_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_multiplication_operators.ex");
    }

    public void testWithSpaceAroundOrOperators() {
        myFixture.configureByFile("without_space_around_or_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_OR_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_or_operators.ex");
    }

    public void testWithoutSpaceAroundOrOperators() {
        myFixture.configureByFile("with_space_around_or_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_OR_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_or_operators.ex");
    }

    public void testWithSpaceWithinParentheses() {
        myFixture.configureByFile("without_space_within_parentheses.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_PARENTHESES = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_within_parentheses.ex");
    }

    public void testWithoutSpaceWithinParentheses() {
        myFixture.configureByFile("with_space_within_parentheses.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_WITHIN_PARENTHESES = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_within_parentheses.ex");
    }

    public void testWithSpaceAroundPipeOperators() {
        myFixture.configureByFile("without_space_around_pipe_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_PIPE_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_pipe_operator.ex");
    }

    public void testWithoutSpaceAroundPipeOperators() {
        myFixture.configureByFile("with_space_around_pipe_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_PIPE_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_pipe_operator.ex");
    }

    public void testWithSpaceAroundRangeOperator() {
        myFixture.configureByFile("without_space_around_range_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_RANGE_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_range_operator.ex");
    }

    public void testWithoutSpaceAroundRangeOperator() {
        myFixture.configureByFile("with_space_around_range_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_RANGE_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_range_operator.ex");
    }

    public void testWithSpaceAroundRelationalOperators() {
        myFixture.configureByFile("without_space_around_relational_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_RELATIONAL_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_relational_operators.ex");
    }

    public void testWithoutSpaceAroundRelationalOperators() {
        myFixture.configureByFile("with_space_around_relational_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_RELATIONAL_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_relational_operators.ex");
    }

    public void testSpaceAfterRescueKeyword() {
        myFixture.configureByFile("incorrect_spaces_after_rescue_keyword.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_after_rescue_keyword.ex");
    }

    public void testWithSpaceAroundStabOperator() {
        myFixture.configureByFile("without_space_around_stab_operator.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_LAMBDA_ARROW = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_stab_operator.ex");
    }

    public void testWithoutSpaceAroundStabOperator() {
        myFixture.configureByFile("with_space_around_stab_operator.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_LAMBDA_ARROW = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_stab_operator.ex");
    }

    public void testWithSpaceAroundThreeOperator() {
        myFixture.configureByFile("without_space_around_three_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_THREE_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_three_operator.ex");
    }

    public void testWithoutSpaceAroundThreeOperator() {
        myFixture.configureByFile("with_space_around_three_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_THREE_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_three_operator.ex");
    }

    public void testWithSpaceAroundTwoOperator() {
        myFixture.configureByFile("without_space_around_two_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_TWO_OPERATORS = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_two_operators.ex");
    }

    public void testWithoutSpaceAroundTwoOperator() {
        myFixture.configureByFile("with_space_around_two_operators.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_TWO_OPERATORS = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_two_operators.ex");
    }

    public void testWithSpaceAroundTypeOperator() {
        myFixture.configureByFile("without_space_around_type_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_TYPE_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_type_operator.ex");
    }

    public void testWithoutSpaceAroundTypeOperator() {
        myFixture.configureByFile("with_space_around_type_operator.ex");

        temporaryCodeStyleSettings.getCustomSettings(CodeStyleSettings.class).SPACE_AROUND_TYPE_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_type_operator.ex");
    }

    public void testWithSpaceAroundUnaryOperators() {
        myFixture.configureByFile("without_space_around_unary_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_UNARY_OPERATOR = true;

        reformatFixture();

        myFixture.checkResultByFile("with_space_around_unary_operators.ex");
    }

    public void testWithoutSpaceAroundUnaryOperators() {
        myFixture.configureByFile("with_space_around_unary_operators.ex");

        temporaryCodeStyleSettings.getCommonSettings(ElixirLanguage.INSTANCE).SPACE_AROUND_UNARY_OPERATOR = false;

        reformatFixture();

        myFixture.checkResultByFile("without_space_around_unary_operators.ex");
    }

    public void testSpaceAroundWhenOperators() {
        myFixture.configureByFile("incorrect_spaces_around_when_operator.ex");

        reformatFixture();

        myFixture.checkResultByFile("space_around_when_operator.ex");
    }

    public void testSingleSpaceNotOperator() {
        myFixture.configureByFile("multispace_not_operator.ex");

        reformatFixture();

        myFixture.checkResultByFile("single_space_not_operator.ex");
    }

    public void testSpec() {
        assertFormatted("spec.ex");
    }

    public void testStructKeysAreAligned() {
        myFixture.configureByFile("struct_keys_unaligned.ex");

        reformatFixture();

        myFixture.checkResultByFile("struct_keys_aligned.ex");
    }

    public void testStructKeysAreNormalIndented() {
        String path = "struct_keys_are_normal_indented.ex";

        myFixture.configureByFile(path);

        reformatFixture();

        myFixture.checkResultByFile(path);
    }

    public void testStructAllKeysWrap() {
        myFixture.configureByFile("struct_some_keys_wrap.ex");

        reformatFixture();

        myFixture.checkResultByFile("struct_all_keys_wrap.ex");
    }

    public void testTupleIndent() {
        assertFormatted("tuple_indent.ex");
    }

    public void testUnmatchedQualifedParenthesesCallArgumentIndent() {
        String path = "unmatched_qualifed_parentheses_call_argument_indent.ex";

        myFixture.configureByFile(path);

        reformatFixture();

        myFixture.checkResultByFile(path);
    }

    private void assertFormatted(@NotNull String path) {
        assertFormatted(path, null);
    }

    /**
     * Asserts that {@code path} will not change when reformatted.
     */
    private void assertFormatted(@NotNull String path, @Nullable Runnable runnable) {
        myFixture.configureByFile(path);

        if (runnable != null) {
            runnable.run();
        }

        reformatFixture();

        myFixture.checkResultByFile(path);
    }

    private void reformatFixture() {
        new WriteCommandAction.Simple(getProject()) {
            @Override
            protected void run() throws Throwable {
                CodeStyleManager.getInstance(getProject()).reformatText(myFixture.getFile(),
                        ContainerUtil.newArrayList(myFixture.getFile().getTextRange()));
            }
        }.execute();
    }
}

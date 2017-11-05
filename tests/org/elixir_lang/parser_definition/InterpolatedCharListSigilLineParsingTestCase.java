package org.elixir_lang.parser_definition;

/**
 * Created by luke.imhoff on 8/8/14.
 */
public class InterpolatedCharListSigilLineParsingTestCase extends ParsingTestCase {
    public void testBraces() {
        assertParsedAndQuotedCorrectly();
    }

    public void testBrackets() {
        assertParsedAndQuotedCorrectly();
    }

    public void testChevrons() {
        assertParsedAndQuotedCorrectly();
    }

    public void testDoubleQuotes() {
        assertParsedAndQuotedCorrectly();
    }

    public void testEscapeSequences() {
        assertParsedAndQuotedCorrectly();
    }

    public void testInterpolation() {
        assertParsedAndQuotedCorrectly();
    }

    public void testMinimal() {
        assertParsedAndQuotedCorrectly();
    }

    public void testSigilModifiers() {
        assertParsedAndQuotedCorrectly();
    }

    public void testSingleQuotes() {
        assertParsedAndQuotedCorrectly();
    }

    @Override
    protected String getTestDataPath() {
        return super.getTestDataPath() + "/interpolated_char_list_sigil_line_parsing_test_case";
    }
}

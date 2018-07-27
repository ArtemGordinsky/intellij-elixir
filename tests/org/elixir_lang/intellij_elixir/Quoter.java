package org.elixir_lang.intellij_elixir;

import com.ericsson.otp.erlang.*;
import com.intellij.psi.PsiFile;
import org.elixir_lang.GenericServer;
import org.elixir_lang.IntellijElixir;
import org.elixir_lang.psi.impl.ElixirPsiImplUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.ComparisonFailure;

import java.io.IOException;

import static org.apache.commons.lang.CharUtils.isAsciiPrintable;
import static org.elixir_lang.psi.impl.ParentImpl.elixirString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

/**
 * Created by luke.imhoff on 12/31/14.
 */
public class Quoter {
    /* remote name is Elixir.IntellijElixir.Quoter because all aliases in Elixir look like atoms prefixed with
       with Elixir. from erlang's perspective. */
    private static final String REMOTE_NAME = "Elixir.IntellijElixir.Quoter";
    private static final int TIMEOUT_IN_MILLISECONDS = 1000;

    public static void assertError(PsiFile file) {
        final String text = file.getText();

        try {
            OtpErlangTuple quotedMessage = Quoter.quote(text);
            Quoter.assertMessageReceived(quotedMessage);

            OtpErlangAtom status = (OtpErlangAtom) quotedMessage.elementAt(0);
            String statusString = status.atomValue();

            assertEquals(statusString, "error");
        } catch (IOException | OtpErlangDecodeException | OtpErlangExit e) {
            throw new RuntimeException(e);
        }
    }

    public static void assertExit(PsiFile file) {
        final String text = file.getText();
        Object exception = null;

        try {
            Quoter.quote(text);
        } catch (IOException | OtpErlangDecodeException | OtpErlangExit e) {
            exception = e;
        }

        assertThat(exception, instanceOf(OtpErlangExit.class));
    }

    @Contract("null -> fail")
    private static void assertMessageReceived(OtpErlangObject message) {
        assertNotNull(
                "did not receive message from " + REMOTE_NAME + "@" + IntellijElixir.REMOTE_NODE + ".  Make sure it is running",
                message
        );
    }

    public static void assertQuotedCorrectly(PsiFile file) {
        final String text = file.getText();

        try {
            OtpErlangTuple quotedMessage = Quoter.quote(text);
            Quoter.assertMessageReceived(quotedMessage);

            OtpErlangAtom status = (OtpErlangAtom) quotedMessage.elementAt(0);
            String statusString = status.atomValue();
            OtpErlangObject expectedQuoted = quotedMessage.elementAt(1);

            if (statusString.equals("ok")) {
                OtpErlangObject actualQuoted = ElixirPsiImplUtil.quote(file);
                assertQuotedCorrectly(expectedQuoted, actualQuoted);
            } else if (statusString.equals("error")) {
                OtpErlangTuple error = (OtpErlangTuple) expectedQuoted;

                OtpErlangLong line = (OtpErlangLong) error.elementAt(0);

                OtpErlangBinary messageBinary = (OtpErlangBinary) error.elementAt(1);
                String message = ElixirPsiImplUtil.javaString(messageBinary);

                OtpErlangBinary tokenBinary = (OtpErlangBinary) error.elementAt(2);
                String token = ElixirPsiImplUtil.javaString(tokenBinary);

                throw new AssertionError(
                        "intellij_elixir returned \"" + message + "\" on line " + line + " due to " + token +
                                ", use assertQuotesAroundError if error is expect in Elixir natively, " +
                                "but not in intellij-elixir plugin"
                );
            }
        } catch (IOException | OtpErlangDecodeException | OtpErlangExit e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertQuotedCorrectly(@NotNull OtpErlangObject expectedQuoted,
                                              @NotNull OtpErlangObject actualQuoted) {
        if (!expectedQuoted.equals(actualQuoted)) {
            throw new ComparisonFailure(null, toString(expectedQuoted), toString(actualQuoted));
        }
    }

    public static OtpErlangTuple quote(@NotNull String code) throws IOException, OtpErlangExit, OtpErlangDecodeException {
        final OtpNode otpNode = IntellijElixir.getLocalNode();
        final OtpMbox otpMbox = otpNode.createMbox();
        OtpErlangObject request = elixirString(code);

        return (OtpErlangTuple) GenericServer.INSTANCE.call(
                otpMbox,
                otpNode,
                REMOTE_NAME,
                IntellijElixir.REMOTE_NODE,
                request,
                TIMEOUT_IN_MILLISECONDS
        );
    }

    @NotNull
    private static String toString(@NotNull OtpErlangBitstr quoted) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('"');

        for (byte element : quoted.binaryValue()) {
            if (element == 0x0A) {
                stringBuilder.append("\\n");
            } else if (isAsciiPrintable((char) element)) {
                stringBuilder.append((char) element);
            } else {
                stringBuilder.append(String.format("\\x%02X", element));
            }
        }

        stringBuilder.append('"');

        return stringBuilder.toString();
    }

    @NotNull
    private static String toString(@NotNull OtpErlangList quoted) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[");

        for (int i = 0; i < quoted.arity(); i++) {
            if (i > 0) {
                stringBuilder.append(",");
            }

            stringBuilder.append(toString(quoted.elementAt(i)));
        }

        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    @NotNull
    private static String toString(@NotNull OtpErlangObject quoted) {
        String string;

        if (quoted instanceof OtpErlangBoolean ||
                quoted instanceof OtpErlangAtom ||
                quoted instanceof OtpErlangByte ||
                quoted instanceof OtpErlangChar ||
                quoted instanceof OtpErlangFloat ||
                quoted instanceof OtpErlangDouble ||
                quoted instanceof OtpErlangExternalFun ||
                quoted instanceof OtpErlangFun ||
                quoted instanceof OtpErlangInt ||
                quoted instanceof OtpErlangLong ||
                quoted instanceof OtpErlangMap ||
                quoted instanceof OtpErlangPid ||
                quoted instanceof OtpErlangString) {
            string = quoted.toString();
        } else if (quoted instanceof OtpErlangBitstr) {
            string = toString((OtpErlangBitstr) quoted);
        } else if (quoted instanceof OtpErlangList) {
            string = toString((OtpErlangList) quoted);
        } else if (quoted instanceof OtpErlangTuple) {
            string = toString((OtpErlangTuple) quoted);
        } else {
            throw new IllegalArgumentException("Don't know how to convert " + quoted.getClass() + " to string");
        }

        return string;
    }

    @NotNull
    private static String toString(@NotNull OtpErlangTuple quoted) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("{");

        for (int i = 0; i < quoted.arity(); i++) {
            if (i > 0) {
                stringBuilder.append(",");
            }

            stringBuilder.append(toString(quoted.elementAt(i)));
        }

        stringBuilder.append("}");

        return stringBuilder.toString();
    }
}

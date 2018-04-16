// This is a generated file. Not intended for manual editing.
package org.elixir_lang.psi;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ElixirInterpolatedWordsHeredoc extends WordsFragmented, InterpolatedSigilHeredoc {

  @Nullable
  ElixirHeredocPrefix getHeredocPrefix();

  @NotNull
  List<ElixirInterpolatedWordsHeredocLine> getInterpolatedWordsHeredocLineList();

  @Nullable
  ElixirSigilModifiers getSigilModifiers();

  @NotNull
  List<Integer> addEscapedCharacterCodePoints(List<Integer> codePointList, ASTNode node);

  @NotNull
  List<Integer> addEscapedEOL(List<Integer> maybeCodePointList, ASTNode node);

  @NotNull
  List<Integer> addFragmentCodePoints(List<Integer> codePointList, ASTNode node);

  @NotNull
  List<Integer> addHexadecimalEscapeSequenceCodePoints(List<Integer> codePointList, ASTNode node);

  IElementType getFragmentType();

  @NotNull
  List<HeredocLine> getHeredocLineList();

  @NotNull
  OtpErlangObject quote();

  @NotNull
  OtpErlangObject quote(OtpErlangObject quotedContent);

  @NotNull
  OtpErlangObject quoteBinary(OtpErlangTuple binary);

  @NotNull
  OtpErlangObject quoteEmpty();

  @NotNull
  OtpErlangObject quoteLiteral(List<Integer> codePointList);

  char sigilName();

}

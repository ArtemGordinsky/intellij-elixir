// This is a generated file. Not intended for manual editing.
package org.elixir_lang.psi;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.intellij.psi.PsiElement;
import kotlin.ranges.IntRange;
import org.elixir_lang.psi.call.Named;
import org.elixir_lang.psi.operation.Prefix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ElixirAtNumericOperation extends Named, Prefix {

  @NotNull
  ElixirAtPrefixOperator getAtPrefixOperator();

  @Nullable
  ElixirBinaryWholeNumber getBinaryWholeNumber();

  @Nullable
  ElixirCharToken getCharToken();

  @Nullable
  ElixirDecimalFloat getDecimalFloat();

  @Nullable
  ElixirDecimalWholeNumber getDecimalWholeNumber();

  @Nullable
  ElixirHexadecimalWholeNumber getHexadecimalWholeNumber();

  @Nullable
  ElixirOctalWholeNumber getOctalWholeNumber();

  @Nullable
  ElixirUnknownBaseWholeNumber getUnknownBaseWholeNumber();

  @Nullable
  String functionName();

  @NotNull
  PsiElement functionNameElement();

  @Nullable
  ElixirDoBlock getDoBlock();

  @Nullable
  String getName();

  @Nullable
  PsiElement getNameIdentifier();

  boolean hasDoBlockOrKeyword();

  boolean isCalling(String resolvedModuleName, String functionName);

  boolean isCalling(String resolvedModuleName, String functionName, int resolvedFinalArity);

  boolean isCallingMacro(String resolvedModuleName, String functionName);

  boolean isCallingMacro(String resolvedModuleName, String functionName, int resolvedFinalArity);

  @Nullable
  String moduleName();

  @Nullable
  Quotable operand();

  @NotNull
  Operator operator();

  @NotNull
  PsiElement[] primaryArguments();

  @Nullable
  Integer primaryArity();

  @NotNull
  OtpErlangObject quote();

  @Nullable
  PsiElement[] secondaryArguments();

  @Nullable
  Integer secondaryArity();

  int resolvedFinalArity();

  @NotNull
  IntRange resolvedFinalArityRange();

  @NotNull
  String resolvedModuleName();

  @Nullable
  Integer resolvedPrimaryArity();

  @Nullable
  Integer resolvedSecondaryArity();

  @NotNull
  PsiElement setName(String newName);

}

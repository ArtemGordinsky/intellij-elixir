package org.elixir_lang.psi.call.arguments.star;

import org.elixir_lang.psi.ElixirNoParenthesesOneArgument;
import org.elixir_lang.psi.call.arguments.star.NoParentheses;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Created by limhoff on 12/17/15.
 */
public interface NoParenthesesOneArgument extends NoParentheses {
    @Contract(pure = true)
    @NotNull
    ElixirNoParenthesesOneArgument getNoParenthesesOneArgument();
}

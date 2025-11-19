package ch.njol.skript.lang.util;

import ch.njol.skript.lang.Expression;
import org.eclipse.jdt.annotation.Nullable;

public interface Contract {

    boolean isSingle(Expression<?>... arguments);

    @Nullable
    Class<?> getReturnType(Expression<?>... arguments);
}

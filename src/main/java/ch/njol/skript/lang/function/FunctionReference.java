/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Copyright 2011-2013 Peter Güttinger
 *
 */

package ch.njol.skript.lang.function;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.config.Node;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.util.coll.CollectionUtils;

/**
 * @author Peter Güttinger
 */
public class FunctionReference<T> {

    final String functionName;

    private Function<? extends T> function;

    private boolean singleUberParam;
    private final Expression<?>[] parameters;

    private boolean single;
    @Nullable
    private final Class<? extends T>[] returnTypes;

    @Nullable
    private final Node node;
    @Nullable
    public final File script;

    @SuppressWarnings("null")
    public FunctionReference(final String functionName, final @Nullable Node node, @Nullable final File script, @Nullable final Class<? extends T>[] returnTypes, final Expression<?>[] params) {
        this.functionName = functionName;
        this.node = node;
        this.script = script;
        this.returnTypes = returnTypes;
        parameters = params;
    }

    @SuppressWarnings("unchecked")
    public boolean validateFunction(final boolean first) {
        Function<?> newFunc = Functions.getFunction(functionName);

        // FORWARD REFERENCE
        if (newFunc == null) {
            return true;
        }

        SkriptLogger.setNode(node);

        if(newFunc == function)
            return true;

        final Class<? extends T>[] returnTypes = this.returnTypes;

        if(returnTypes != null) {

            final ClassInfo<?> rt = newFunc.returnType;
            if(rt == null) {
                Skript.error("The function '" + functionName + "' doesn't return any value.");
                return false;
            }

            if(!CollectionUtils.containsAnySuperclass(returnTypes, rt.getC())) {
                Skript.error("The returned value of the function '" + functionName + "' is incompatible.");
                return false;
            }

            single = newFunc.single;
        }

        singleUberParam = newFunc.getMaxParameters() == 1 && !newFunc.parameters[0].single;

        if(!singleUberParam) {
            if(parameters.length > newFunc.getMaxParameters()) {
                Skript.error("Too many arguments for new function '"+ functionName+ "'");
                return false;
            }
        }

        if(parameters.length < newFunc.getMinParameters()) {
            Skript.error("Not enough arguments for function '"+functionName+"'");
            return false;
        }

        for (int i = 0; i < parameters.length; i++) {
            final Parameter<?> p = newFunc.parameters[singleUberParam ? 0 : i];
            final Expression<?> e = parameters[i].getConvertedExpression(p.type.getC());

            if(e == null) {
                Skript.error("Invalid parameter type in function '"+functionName+"'");
                return false;
            }

            parameters[i] = e;
        }

        function = (Function<? extends T>) newFunc;

        Functions.registerCaller(this);

        return true;
    }

    @SuppressWarnings("unchecked")
    public void setFunction(Function<?> function) {
        this.function = (Function<? extends T>) function;
    }

    @Nullable
    protected T[] execute(final Event e) {
        final Object[][] params = new Object[singleUberParam ? 1 : parameters.length][];
        if (singleUberParam && parameters.length > 1) {
            final ArrayList<Object> l = new ArrayList<Object>();
            for (int i = 0; i < params.length; i++)
                l.addAll(Arrays.asList(parameters[i].getArray(e))); // TODO what if an argument is not available? pass null or abort?
            params[0] = l.toArray();
        } else {
            for (int i = 0; i < params.length; i++)
                params[i] = parameters[i].getArray(e); // TODO what if an argument is not available? pass null or abort?
        }
        return function.execute(params);
    }

    public boolean isSingle() {
        return single;
    }

    @SuppressWarnings("null")
    public Class<? extends T> getReturnType() {
        return function.returnType.getC();
    }

    public String toString(@Nullable final Event e, final boolean debug) {
        final StringBuilder b = new StringBuilder(functionName + "(");
        for (int i = 0; i < parameters.length; i++) {
            if (i != 0)
                b.append(", ");
            b.append(parameters[i].toString(e, debug));
        }
        return "" + b.append(")");
    }

}
package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Environment;
import com.craftinginterpreters.lox.Interpreter;
import com.craftinginterpreters.lox.LoxCallable;

import java.util.List;

public class LoxFunction implements LoxCallable
{
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;
    public final boolean isGetter;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer, boolean isGetter)
    {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
        this.isGetter = isGetter;
    }

    @Override
    public int arity()
    {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments)
    {
        Environment environment = new Environment(closure);
        for (int i = 0; i < arguments.size(); i++)
        {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        try
        {
            interpreter.executeBlock(declaration.body, environment);
        }
        catch (Return returnValue)
        {
            if (isInitializer) return closure.getAt(0, "this");
            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    public LoxFunction bind(LoxInstance instance)
    {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer, isGetter);
    }
}
package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Environment;
import com.craftinginterpreters.lox.Interpreter;
import com.craftinginterpreters.lox.LoxCallable;

import java.util.List;

public class LoxLambda implements LoxCallable
{
    private final Expr.Function declaration;
    private final Environment closure;

    LoxLambda(Expr.Function declaration, Environment closure)
    {
        this.declaration = declaration;
        this.closure = closure;
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
            return returnValue.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<unnamed lamda >";
    }
}
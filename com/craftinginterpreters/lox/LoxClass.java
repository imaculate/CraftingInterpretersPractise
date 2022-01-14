package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.LoxCallable;
import com.craftinginterpreters.lox.LoxFunction;

import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable {
    
    public final String name;
    private final Map<String, LoxFunction> methods;
    private final LoxClass superClass;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods)
    {
        this.name = name;
        this.methods = methods;
        this.superClass = superClass;
    }

    @Override
    public String toString()
    {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments)
    {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null)
        {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public int arity()
    {
        LoxFunction initializer = findMethod("init");
        if (initializer != null)  return initializer.arity();
        return 0;
    }

    public LoxFunction findMethod(String name)
    {
        if (methods.containsKey(name)) return methods.get(name);
        if (superClass != null) return superClass.findMethod(name);
        return null;
    }
}

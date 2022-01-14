package com.craftinginterpreters.lox;

import java.util.Map;
import java.util.HashMap;

import com.craftinginterpreters.lox.LoxFunction;
import com.craftinginterpreters.lox.Token;

public class LoxInstance {
    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();
    public LoxInstance(LoxClass klass)
    {
        this.klass = klass;
    }

    @Override
    public String toString()
    {
        return klass.name + " instance";
    }

    public Object get(Token name)
    {
        if (fields.containsKey(name.lexeme))
        {
            return fields.get(name.lexeme);
        }

        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RunTimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    public void set(Token name, Object value)
    {
        fields.put(name.lexeme, value);
    }
}

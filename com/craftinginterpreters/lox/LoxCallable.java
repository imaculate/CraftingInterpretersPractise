package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Interpreter;

import java.util.List;
public interface LoxCallable {
    Object call(Interpreter interpreter, List<Object> arguments);
    int arity();
}

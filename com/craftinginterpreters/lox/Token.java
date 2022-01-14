package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

public class Token
{
    public String lexeme;
    public TokenType type;
    public Object literal;
    public int line;

    public Token(TokenType type, String lexeme, Object literal, int line)
    {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString()
    {
        return type + " " + lexeme + " " + literal;
    }
}
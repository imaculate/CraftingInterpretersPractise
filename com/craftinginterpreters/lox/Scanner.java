package com.craftinginterpreters.lox;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<Token>();
    private static final Map<String, TokenType> keywords;
    static {
        keywords = new HashMap<>();
        keywords.put("and", TokenType.AND);
        keywords.put("or", TokenType.OR);
        keywords.put("false", TokenType.FALSE);
        keywords.put("true", TokenType.TRUE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("for", TokenType.FOR);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("print", TokenType.PRINT);
        keywords.put("return", TokenType.RETURN);
        keywords.put("var", TokenType.VAR);
        keywords.put("nil", TokenType.NIL);
        keywords.put("fun", TokenType.FUN);
        keywords.put("class", TokenType.CLASS);
        keywords.put("this", TokenType.THIS);
        keywords.put("super", TokenType.SUPER);
        keywords.put("break", TokenType.BREAK);
    }
    private int start;
    private int current;
    private int line;


    public Scanner(String source)
    {
        this.source = source;
    }
    
    public List<Token> scanTokens()
    {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }
    
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken()
    {
        char c = advance();
        switch (c)
        {
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{': addToken(TokenType.LEFT_BRACE); break;
            case '}': addToken(TokenType.RIGHT_BRACE); break;
            case ',': addToken(TokenType.COMMA); break;
            case '.': addToken(TokenType.DOT); break;
            case '-': addToken(TokenType.MINUS); break;
            case '+': addToken(TokenType.PLUS); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '*': addToken(TokenType.STAR); break;
            case '?': addToken(TokenType.QUESTION_MARK); break;
            case ':': addToken(TokenType.COLON); break;
            case '!': addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG); break;
            case '=': addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL); break;
            case '<': addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS); break;
            case '>': addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER); break;
            case '/':
                // TODO: add support for /*...*/ with nesting?
                // read about implicit semicolons
                if (match('/'))
                {
                    while (!isAtEnd() && peek() != '\n') advance();
                }
                else if (match('*'))
                {
                    int levels = 1;
                    while (!isAtEnd())
                    {
                        if (peek() == '\n')
                        {
                            line++;
                        }
                        else if (peek() == '/' && peekNext() == '*')
                        {
                            advance();
                            levels++;
                        }
                        else if (peek() == '*' && peekNext() == '/')
                        {
                            levels--;
                            if (levels == 0)
                                break;

                        }
                        advance();
                    }
                }
                else
                {
                    addToken(TokenType.SLASH);
                }
                break;
            case ' ':
            case '\t':
            case '\r':
                break;
            case '\n':
                line++;
                break;
            case '"':
                string(); break;
            default:
                if (Character.isDigit(c))
                {
                    number();
                }
                else if (Character.isLetter(c))
                {
                    identifier();
                }
                else
                {
                    Lox.error(line, "Unexpected character: " + c);
                }
                break;
        }
    }

    private void identifier()
    {
        while(Character.isLetterOrDigit(peek()) || peek() == '_') advance();
        String text =  source.substring(start, current);
        addToken(keywords.getOrDefault(text, TokenType.IDENTIFIER), text);
    }

    private void number()
    {
        while (Character.isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && Character.isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (Character.isDigit(peek())) advance();
        }

        addToken(TokenType.NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string()
    {
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\n') line++;
            advance();
        }
      
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }
      
        advance();
      
        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }

    private boolean match(char expected)
    {
        if (isAtEnd() || (source.charAt(current) != expected))
            return false;

        current++;
        return true;
    }

    private void addToken(TokenType type)
    {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal)
    {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private char advance()
    {
        return isAtEnd() ? '\0' : source.charAt(current++);
    }

    private char peek()
    {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext()
    {
        return (current + 1 >= source.length()) ? '\0' : source.charAt(current+1);
    }

    private boolean isAtEnd()
    {
        return current >= source.length();
    }
}

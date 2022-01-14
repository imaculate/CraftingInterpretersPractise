package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.*;
import static com.craftinginterpreters.lox.TokenType.*;
import java.util.List;

import javax.swing.SpringLayout.Constraints;

import java.util.ArrayList;
import java.util.Arrays;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current;
    private int loops;
    //private boolean repl;

    Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }
    
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<Stmt>();
        while (!isAtEnd())
        {
            statements.add(declaration());
        }
        return statements;
    }

    private Stmt declaration()
    {
        try
        {
            if (match(CLASS)) return classDeclaration();
            if (match(FUN)) return function("function");
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement()
    {
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.BREAK)) return breakStatement();
        if (match(TokenType.RETURN)) return returnStatement();
        return expressionStatement();
    }

    private Stmt breakStatement()
    {
        if (loops < 1) throw error(previous(), "Break statement outside of loop");
        consume(TokenType.SEMICOLON, "Expect ';' after statement");
        return new Stmt.Break();
    }

    private Stmt whileStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expected '(' before while condition");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expected ')' after while condition");
        loops++;
        Stmt statement = statement();
        loops--;
        return new Stmt.While(condition, statement);
    }

    private Stmt forStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expected '(' before while condition");
        Stmt initializer = null;
        if (match(TokenType.VAR))
        {
            initializer = varDeclaration();
        }
        else if (!match(TokenType.SEMICOLON))
        {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(TokenType.SEMICOLON))
        {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        loops++;
        Stmt body = statement();
        loops--;
        if (increment != null)
        {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);

        body = new Stmt.While(condition, body);
        if (initializer != null) body = new Stmt.Block(Arrays.asList(initializer, body));

        return body;
    }

    private Stmt ifStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expected '(' before if condition");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expected ')' after if condition");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE))
        {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt returnStatement()
    {
        Token token = previous();
        Expr value = null;
        if (!check(TokenType.SEMICOLON))
        {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value");
        return new Stmt.Return(previous(), value);
    }

    private List<Stmt> block()
    {
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd())
        {
            statements.add(declaration());
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block");
        return statements;
    }

    private Stmt varDeclaration()
    {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name");
        Expr expression = null;
        if (match(TokenType.EQUAL))
        {
            expression = expression();
        }
        consume(TokenType.SEMICOLON, "Expect ';' after statement");
        return new Stmt.Var(name, expression);
    }

    private Stmt classDeclaration()
    {
        Token name = consume(IDENTIFIER, "Expect class name.");
        List<Expr.Variable> superClasses = new ArrayList<>();


        if(match(LESS)) 
        {
            do
            {
                consume(IDENTIFIER, "Expect superclass name.");
                superClasses.add(new Expr.Variable(previous()));
            }while (match(LEFT_BRACE))
            
        }
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd())
        {
            methods.add(function("method"));
        }
        
        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, superClass, methods);
    }

    private Stmt.Function function(String kind)
    {
        if (match(CLASS)) kind = "static";
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        List<Token> parameters =  new ArrayList<>();
        if (check(TokenType.LEFT_BRACE))
        {
            if (!kind.equals("method")) Lox.error(peek(), "Defining getter in incorrect context");
            kind = "getter";
        }
        else
        {
            consume(LEFT_PAREN, "Expect '(' after " + kind + " name. ");
            if (!check(RIGHT_PAREN))
            {
                do
                {
                    if (parameters.size() >= 255)
                    {
                        error(peek(), "Can't have more than 255 parameters");
                    }
                    parameters.add(consume(IDENTIFIER, "Expect parameter name"));
                }while(match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters");
        }

        consume(LEFT_BRACE, "Expect '{' before " + kind + "body");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body, kind);
    }

    private Stmt printStatement()
    {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after statement");
        return new Stmt.Print(expr);
    }

    private Stmt expressionStatement()
    {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after statement");
        return new Stmt.Expression(expr);
    }

    private Expr expression()
    {
        return assignment();
    }

    private Expr assignment()
    {
        Expr expr = ternary();
        if (match(TokenType.EQUAL))
        {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof Expr.Variable)
            {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            else if (expr instanceof Expr.Get)
            {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr ternary()
    {
        Expr expr = or();

        if (match(TokenType.QUESTION_MARK))
        {
            Expr left = equality();
            consume(TokenType.COLON, "Right operand expected with ternary operator.");
            Expr right = equality();
            return new Expr.Ternary(expr, left, right);
        }

        return expr;
    }

    private Expr or()
    {
        Expr expr = and();
        
        while (match(TokenType.OR))
        {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(operator, expr, right);
        }

        return expr;
    }

    private Expr and()
    {
        Expr expr = equality();
        while (match(TokenType.AND))
        {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(operator, expr, right);
        }

        return expr;
    }


    private Expr equality()
    {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL))
        {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison()
    {
        Expr expr = term();

        while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL))
        {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term()
    {
        Expr expr = factor();

        while (match(TokenType.PLUS, TokenType.MINUS))
        {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor()
    {
        Expr expr = unary();

        while (match(TokenType.STAR, TokenType.SLASH))
        {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary()
    {
        if (match(TokenType.MINUS, TokenType.BANG))
        {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        else if (match(TokenType.PLUS, TokenType.STAR, TokenType.SLASH))
        {
            advance();
            Expr expr = expression();
            throw error(peek(), "Binary operator without left operand");
        }

        return call();
    }

    private Expr call()
    {
        Expr expr = primary();

        while(true)
        {
            if (match(LEFT_PAREN))
            {
                expr = finishCall(expr);
            }
            else if (match(DOT))
            {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            }
            else
            {
                break;
            }
        }

        return expr;
    }


    private Expr finishCall(Expr callee)
    {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)){
            do
            {
                if (arguments.size() >= 255)
                {
                    error(peek(), "Can't have more than 255 arguments");
                }
                arguments.add(expression());
            } while (match(TokenType.COMMA));
        }
        
        Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after argument list");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary()
    {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.NIL)) return new Expr.Literal(null);
        if (match(TokenType.NUMBER, TokenType.STRING)) return new Expr.Literal(previous().literal);
        if (match(TokenType.SUPER))
        {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expected super class method name.");
            return new Expr.Super(keyword, method);
        }
        if (match(TokenType.THIS)) return new Expr.This(previous());
        if (match(TokenType.IDENTIFIER)) return new Expr.Variable(previous());
        if (match(TokenType.FUN)) return functionExpression();
        if (match(TokenType.LEFT_PAREN))
        {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression");
    }

    private Expr functionExpression()
    {
        consume(LEFT_PAREN, "Expect '(' after lambda declaration. ");
        List<Token> parameters =  new ArrayList<>();
        if (!check(RIGHT_PAREN))
        {
            do
            {
                if (parameters.size() >= 255)
                {
                    error(peek(), "Can't have more than 255 parameters");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name"));
            }while(match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters");
        consume(LEFT_BRACE, "Expect '{' before lambda body");
        List<Stmt> body = block();
        return new Expr.Function(parameters, body);
    }

    private boolean match(TokenType... types)
    {
        for (TokenType type : types)
        {
            if (check(type))
            {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type)
    {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token previous()
    {
        return tokens.get(current-1);
    }

    private Token advance()
    {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token consume(TokenType type, String errorMessage)
    {
        if (check(type)) return advance();
        throw error(peek(), errorMessage);
    }

    private Token peek()
    {
        return tokens.get(current);
    }

    private boolean isAtEnd()
    {
        return peek().type == TokenType.EOF;
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();
    
        while (!isAtEnd()) {
          if (previous().type == SEMICOLON) return;
    
          switch (peek().type) {
            case CLASS:
            case FUN:
            case VAR:
            case FOR:
            case IF:
            case WHILE:
            case PRINT:
            case RETURN:
              return;
            default:
                break;
          }
    
          advance();
        }
    }
}

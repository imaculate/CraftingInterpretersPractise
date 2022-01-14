package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.*;
import com.craftinginterpreters.lox.Stmt.Expression;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    public final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();
    private boolean hitBreak = false;


    public Interpreter()
    {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments)
            {
                return (double) System.currentTimeMillis()/1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    public void interpret(List<Stmt> statements)
    {
        try
        {
            for (Stmt statement: statements)
            {
                execute(statement);
            }
        }
        catch (RunTimeError err)
        {
            Lox.runtimeError(err);
        }
    }

    public void resolve(Expr expr, int depth)
    {
        locals.put(expr, depth);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        Object value = null;
        if (stmt.initializer != null)
        {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt)
    {
        Object superclass = null;
        if (stmt.superclass != null) {
          superclass = evaluate(stmt.superclass);
          if (!(superclass instanceof LoxClass)) {
            throw new RunTimeError(stmt.superclass.name,
                "Superclass must be a class.");
          }
        }

        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null)
        {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method: stmt.methods)
        {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"), method.kind.equals("getter"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);
        if (superclass != null)
        {
            environment = environment.enclosing;
        }
        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt)
    {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        executeBlock(stmt.statements, environment);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        if (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.thenBranch);
        }
        else if (stmt.elseBranch != null)
        {
            execute(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        while (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.body);
            if (hitBreak)
            {
                hitBreak = false;
                return null;
            }
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt)
    {
        hitBreak = true;
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        LoxFunction function = new LoxFunction(stmt, environment, false, stmt.kind.equals("getter"));
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }


    public void executeBlock(List<Stmt> statements, Environment environment)
    {
        Environment previous = this.environment;
        try 
        {
            this.environment = environment;
            for (Stmt statement: statements)
            {
                execute(statement);
                if (hitBreak) break;
            }
        }
        finally
        {
            this.environment = previous;
        }
    }

    private void execute(Stmt statement)
    {
        statement.accept(this);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr)
    {
        Object left = expr.left.accept(this);
        Object right = expr.right.accept(this);

        switch (expr.operator.type)
        {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                checkZeroDivisor(expr.operator, right);
                return (double)left/(double)right;
            
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                {
                    return (double)left + (double)right;
                }
                else if (left instanceof String || right instanceof String)
                {
                    return stringify(left) + stringify(right);
                }
                throw new RunTimeError(expr.operator, "Operands must be strings or numbers");
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case EQUAL_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return isEqual(left, right);
            case BANG_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return !isEqual(left, right);
        }
        return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr)
    {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr)
    {
        Object right = evaluate(expr.right);
        switch (expr.operator.type)
        {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }
        return null;
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr)
    {
        Object condition = evaluate(expr.condition);
        if (isTruthy(condition)) return evaluate(expr.left);
        return evaluate(expr.right);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr)
    {
        return expr.value;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr)
    {
        Object value = environment.get(expr.name);
        if (value != null) return value;
        throw new RunTimeError(expr.name, "Accesing uninitialized variable '" + expr.name.lexeme + "'.");
    }

    private Object lookUpVariable(Token name, Expr expr)
    {
        Integer distance = locals.get(expr);
        if (distance != null)
        {
            return environment.getAt(distance, name.lexeme);
        }
        else
        {
            return globals.get(name);
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr)
    {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null)
        {
            environment.assignAt(distance, expr.name, value);
        }
        else
        {
            globals.assign(expr.name, value);
        }
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr)
    {
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR)
        {
            if (isTruthy(left)) return left;
        }
        else
        {
            if (!isTruthy(left)) return left;
        }
        
        return evaluate(expr.right);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr)
    {
        Object callee = evaluate(expr.callee);
        List<Object> arguments = new ArrayList<>();
        for (Expr argument: expr.arguments)
        {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable))
        {
            throw new RunTimeError(expr.paren, "Can only call functions and classes");
        }

        LoxCallable function  = (LoxCallable)callee;
        if (arguments.size() != function.arity())
        {
            throw new RunTimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr)
    {
        return new LoxLambda(expr, environment);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr)
    {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            object = ((LoxInstance) object).get(expr.name);
            if (object instanceof LoxFunction && ((LoxFunction)object).isGetter) return ((LoxFunction) object).call(this, new ArrayList<>());
            return object;
        }
        else if (object instanceof LoxClass) {
            return ((LoxClass) object).findMethod(expr.name.lexeme);
        }

        throw new RunTimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr)
    {
        Object object = evaluate(expr.object);
        if (!(object instanceof LoxInstance))
        {
            throw new RunTimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr)
    {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr)
    {
        int distance = locals.get(expr);
        LoxClass superClass = (LoxClass)environment.getAt(distance, "super");
        LoxInstance object = (LoxInstance)environment.getAt(distance-1, "this");
        LoxFunction method = superClass.findMethod(expr.method.lexeme);
        if (method == null)
        {
            throw new RunTimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    private Object evaluate(Expr expr)
    {
        return expr.accept(this);
    }

    private boolean isTruthy(Object object)
    {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b)
    {
        if (a == null && b == null) return true;
        if (a == null) return true;
        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand)
    {
        if (operand instanceof Double) return;
        throw new RunTimeError(operator, "Operand must be a number.");
    }

    private void checkZeroDivisor(Token operator, Object operand)
    {
        if (((Double)operand).equals((Double)0.0))
            throw new RunTimeError(operator, "Divisor can not be zero.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right)
    {
        if (left instanceof Double && right instanceof Double) return;
        throw new RunTimeError(operator, "Operands must be numbers.");
    }

    private String stringify(Object object)
    {
        if (object == null) return "nil";
        if (object instanceof Double)
        {
            String s = object.toString();
            if (s.endsWith(".0"))
            {
                return s.substring(0, s.length()-2);
            }
            return s;
        }
        return object.toString();
    }
}


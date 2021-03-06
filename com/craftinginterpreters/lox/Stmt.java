package com.craftinginterpreters.lox;

import java.util.List;

public abstract class Stmt {
  interface Visitor<R> {
    R visitExpressionStmt(Expression stmt);
    R visitPrintStmt(Print stmt);
    R visitVarStmt(Var stmt);
    R visitBlockStmt(Block stmt);
    R visitIfStmt(If stmt);
    R visitWhileStmt(While stmt);
    R visitBreakStmt(Break stmt);
    R visitFunctionStmt(Function stmt);
    R visitReturnStmt(Return stmt);
    R visitClassStmt(Class stmt);
  }
    public static class Expression extends Stmt
    {
        public final Expr expression;
        public Expression(Expr expression) {
          this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
          return visitor.visitExpressionStmt(this);
        }
    }

    public static class Print extends Stmt
    {
        public final Expr expression;
        public Print(Expr expression) {
          this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
          return visitor.visitPrintStmt(this);
        }
    }

    public static class Var extends Stmt
    {
        public final Token name;
        public final Expr initializer;
        public Var(Token name, Expr initializer) {
          this.name = name;
          this.initializer = initializer;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
          return visitor.visitVarStmt(this);
        }
    }

    public static class Block extends Stmt
    {
      public final List<Stmt> statements;
      public Block(List<Stmt> statements)
      {
        this.statements = statements;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitBlockStmt(this);
      }
    }

    public static class If extends Stmt
    {
      public Expr condition;
      public Stmt thenBranch;
      public Stmt elseBranch;

      public If(Expr condition, Stmt thenBranch, Stmt elseBranch)
      {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitIfStmt(this);
      }
    }

    public static class While extends Stmt
    {
      public Expr condition;
      public Stmt body;

      public While(Expr condition, Stmt body)
      {
        this.condition = condition;
        this.body = body;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitWhileStmt(this);
      }
    }

    public static class Break extends Stmt
    {
      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitBreakStmt(this);
      }
    }

    public static class Function extends Stmt
    {
      public Token name;
      public List<Token> params;
      public List<Stmt> body;
      public String kind;

      public Function(Token name, List<Token> params, List<Stmt> body, String kind)
      {
        this.name = name;
        this.params = params;
        this.body = body;
        this.kind = kind;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitFunctionStmt(this);
      }
    }

    public static class Return extends Stmt
    {
      public Token keyword;
      public Expr value;

      public Return(Token keyword, Expr value)
      {
        this.keyword = keyword;
        this.value = value;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitReturnStmt(this);
      }
    }

    public static class Class extends Stmt
    {
      public Token name;
      public List<Expr.Variable> superclasses;
      public List<Stmt.Function> methods;
      
      public Class(Token name, List<Expr.Variable> superclasses, List<Stmt.Function> methods)
      {
        this.name = name;
        this.superclasses = superclasses;
        this.methods = methods;
      }

      @Override
      public <R> R accept(Visitor<R> visitor) {
        return visitor.visitClassStmt(this);
      }
    }

  abstract <R> R accept(Visitor<R> visitor);
}
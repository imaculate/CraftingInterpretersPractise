package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.*;
import com.craftinginterpreters.lox.TokenType.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.module.ResolutionException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
  static boolean hadError = false;
  static boolean hadRuntimeError = false;
  static boolean repl = false;
  private static Interpreter interpreter = new Interpreter();
  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64); 
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    run(new String(bytes, Charset.defaultCharset()));
    if (hadError) System.exit(65);
    if (hadRuntimeError) System.exit(70);
  }

  private static void runPrompt() throws IOException {
    repl = true;
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    for (;;) { 
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null) break;
      run(line);
      hadError = false;
    }
  }

  private static void run(String source)
  {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

    Parser parser = new Parser(tokens);
    List<Stmt> statements = parser.parse();
    if (hadError) return;
    //System.out.println(new AstPrinter().print(expression));
    Resolver resolver = new Resolver(interpreter);
    resolver.resolve(statements);

    if (hadError) return;
    interpreter.interpret(statements);
  }

  public static void error(Token token, String message)
  {
    if (token.type == TokenType.EOF) {
      report(token.line, " at end", message);
    } else {
      report(token.line, " at '" + token.lexeme + "'", message);
    }
  }

  public static void runtimeError(RunTimeError error)
  {
    System.err.println(error.getMessage() + 
    "\nline[" + error.token.line + "]");
    hadRuntimeError = true;
  }

  protected static void error(int line, String message)
  {
      report(line, "", message);
  }

  private static void report(int line, String where, String message)
  {
    System.err.println( "[line " + line + "] Error" + where + ": " + message);
    hadError = true;
  }
}
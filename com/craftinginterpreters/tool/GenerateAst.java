package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

class GenerateAst
{
    public static void main(String[] args) throws IOException
    {
        if (args.length != 1)
        {
            System.out.println("Usage: generate_ast <output_directory>");
            System.exit(64);
        }

        String outputDir = args[0];
        /* defineAst(outputDir, "Expr", Arrays.asList(
            "Binary : Expr left, Token operator, Expr right",
            "Grouping : Expr expression",
            "Unary : Token operator, Expr right",
            "Literal : Object value"
        )); */

        defineAst(outputDir, "Stmt", Arrays.asList(
            "Expression : Expr expression",
            "Print      : Expr expression"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException
    {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter printWriter = new PrintWriter(path, "UTF-8");

        printWriter.println("package com.craftinginterpreters.lox;");
        printWriter.println();
        printWriter.println("abstract class " + baseName + " {");
        defineVisitor(printWriter, baseName, types);
        for (String type: types)
        {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim(); 
            defineType(printWriter, baseName, className, fields);
        }
        printWriter.println();
        printWriter.println("  abstract <R> R accept(Visitor<R> visitor);");

        printWriter.print("}");
        printWriter.close();
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fields)
    {
        writer.println("    static class " + className + " extends " + baseName);
        writer.println("    {");
        // fields
        for (String field : fields.split(", "))
        {
            writer.println("        final " + field + ";");
        }

        // Constructor
        writer.println("        " + className + "(" + fields + ") {");

        // Store parameters in fields.
        for (String field : fields.split(", ")) {
            String name = field.split(" ")[1];
            writer.println("          this." + name + " = " + name + ";");
        }
        writer.println("        }");

        // Visitor pattern.
        writer.println();
        writer.println("        @Override");
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("          return visitor.visit" + className + baseName + "(this);");
        writer.println("        }");

        
        writer.println("    }");
        writer.println();
    }

    private static void defineVisitor(
      PrintWriter writer, String baseName, List<String> types) {
        writer.println("  interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" +
                typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
  }
}


javac -cp . -d .\bin\ .\com\craftinginterpreters\lox\*.java

java -cp .\bin\  com.craftinginterpreters.lox.Lox


gcc main.c chunk.c debug.c memory.c -o clox.exe
clox.exe
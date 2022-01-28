#include <stdarg.h>
#include <stdio.h>
#include "common.h"
#include "compile.h"
#include "vm.h"
#include "debug.h"
#include "memory.h"
#include "value.h"

#define READ_BYTE() (*vm.ip++)
#define READ_CONSTANT() (vm.chunk->constants.values[READ_BYTE()])
#define BINARY_OP(valueType, op) \
    do { \
    if (!IS_NUMBER(peek(0))  || !IS_NUMBER(peek(1))) \
    { \
        runtimeError("Operands must be numbers."); \
        return INTERPRET_RUNTIME_ERROR; \
    } \
    double b = AS_NUMBER(pop()); \
    double a = AS_NUMBER(pop()); \
    push(valueType(a op b)); \
    } while(false)

VM vm;

static void resetStack()
{
    vm.stackCapacity = 0;
    vm.stack = NULL;
    vm.stackTop = vm.stack;
}

static void runtimeError(const char* format, ...)
{
    va_list args;
    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
    fputs("\n", stderr);

    size_t instruction = vm.ip - vm.chunk->code - 1;
    int line = vm.chunk->lines[instruction];
    fprintf(stderr, "[line %d] in script\n", line);
    resetStack();
}

void initVM()
{
    resetStack();
}

void freeVM()
{
    FREE_ARRAY(Value, vm.stack, vm.stackCapacity);
    resetStack();
}

void push(Value value)
{
    int stackCount = vm.stackTop - vm.stack;
    if (vm.stackCapacity <= stackCount)
    {
        int oldCapacity = vm.stackCapacity;
        vm.stackCapacity = GROW_CAPACITY(oldCapacity);
        vm.stack = GROW_ARRAY(Value, vm.stack, oldCapacity, vm.stackCapacity);
        vm.stackTop = vm.stack + stackCount;
    }
    *vm.stackTop = value;
    vm.stackTop++;
}

Value pop()
{
    vm.stackTop--;
    return *vm.stackTop;
}

static Value peek(int distance)
{
    return vm.stackTop[-1-distance];
}

Value readConstantLong()
{
    long index = 0;
    for (int i = 0; i < 3; i++)
    {
        index |= READ_BYTE() << (8*i);
    }
    return vm.chunk->constants.values[index];
}

static InterpretResult run()
{
    for (;;) {
#ifdef DEBUG_TRACE_EXECUTION
    printf("       ");
    for (Value* slot = vm.stack; slot < vm.stackTop; slot++)
    {
        printf("[ ");
        printValue(*slot);
        printf(" ]");
    }
    printf("\n");
    disassembleInstruction(vm.chunk, (int)(vm.ip - vm.chunk->code));
#endif
        uint8_t instruction;
        switch (instruction = READ_BYTE()) {
            case OP_CONSTANT:
            {
                Value constant = READ_CONSTANT();
                printValue(constant);
                push(constant);
                printf("\n");
                break;
            }
            case OP_CONSTANT_LONG:
            {
                Value constant = readConstantLong();
                printValue(constant);
                push(constant);
                printf("\n");
                break;
            }
            case OP_RETURN: {
                printValue(pop());
                printf("\n");
                return INTERPRET_OK;
            }
            case OP_ADD:BINARY_OP(NUMBER_VALUE, +); break;
            case OP_SUBTRACT: BINARY_OP(NUMBER_VALUE, -); break;
            case OP_MULTIPLY: BINARY_OP(NUMBER_VALUE, *); break;
            case OP_DIVIDE: BINARY_OP(NUMBER_VALUE, /); break;
            case OP_NEGATE:
            if (!IS_NUMBER(peek(0)))
            {
                runtimeError("Operand must be a number.");
                return INTERPRET_RUNTIME_ERROR;
            }
            push(NUMBER_VALUE(-AS_NUMBER(pop())));
        }
    }
}

InterpretResult interpret(const char* source)
{
    /*compile(source);
    return INTERPRET_OK;*/
    Chunk chunk;
    initChunk(&chunk);

    if (!compile(source, &chunk)) 
    {
        freeChunk(&chunk);
        return INTERPRET_COMPILE_ERROR;
    }

    vm.chunk = &chunk;
    vm.ip = vm.chunk->code;

    InterpretResult result = run();
    freeChunk(&chunk);
    return result;
}
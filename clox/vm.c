#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "common.h"
#include "compile.h"
#include "vm.h"
#include "debug.h"
#include "memory.h"
#include "value.h"
#include "object.h"

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
    vm.objects = NULL;
    initTable(&vm.strings);
}

void freeVM()
{
    freeTable(&vm.strings);
    freeObjects();
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

static bool isFalsey(Value value)
{
    return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static void concatenate()
{
    ObjString* b = AS_STRING(pop());
    ObjString* a = AS_STRING(pop());

    int length = a->length + b->length;
    char* chars = ALLOCATE(char, length + 1);
    memcpy(chars, a->chars, a->length);
    memcpy(chars, b->chars, b->length);
    chars[length] = '\0';

    ObjString* result = takeString(chars, length);
    push(OBJ_VALUE(result));
}

static bool valuesEqual(Value a, Value b)
{
    if (a.type != b.type) return false;
    switch (a.type)
    {
        case VAL_BOOL: return AS_BOOL(a) == AS_BOOL(b);
        case VAL_NIL: return true;
        case VAL_NUMBER: return AS_NUMBER(a) == AS_NUMBER(b);
        case VAL_OBJ: return AS_OBJ(a) == AS_OBJ(b);
        default: return false;
    }
    return false;
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
            case OP_NIL: push(NIL_VALUE); break;
            case OP_TRUE: push(BOOL_VALUE(true)); break;
            case OP_FALSE: push(BOOL_VALUE(false)); break;
            case OP_ADD: //BINARY_OP(NUMBER_VALUE, +); break;
            {
                if (IS_STRING(peek(0)) && IS_STRING(peek(1)))
                {
                    concatenate();
                }
                else if (IS_NUMBER(peek(0)) && IS_NUMBER(peek(1)))
                {
                    double b = AS_NUMBER(pop());
                    double a = AS_NUMBER(pop());
                    push(NUMBER_VALUE(a + b));
                }
                else
                {
                    runtimeError("Operands must be two numbers or two strings.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_SUBTRACT: BINARY_OP(NUMBER_VALUE, -); break;
            case OP_MULTIPLY: BINARY_OP(NUMBER_VALUE, *); break;
            case OP_DIVIDE: BINARY_OP(NUMBER_VALUE, /); break;
            case OP_NOT: push(BOOL_VALUE(isFalsey(pop()))); break;
            case OP_EQUAL:
            {
                Value b = pop();
                Value a = pop();
                push(BOOL_VALUE(valuesEqual(a, b)));
                break;
            }
            case OP_GREATER: BINARY_OP(BOOL_VALUE, >); break;
            case OP_LESS: BINARY_OP(BOOL_VALUE, <); break;
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
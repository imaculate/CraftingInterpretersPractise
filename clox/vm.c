#include <stdio.h>
#include "common.h"
#include "vm.h"
#include "debug.h"
#include "memory.h"

#define READ_BYTE() (*vm.ip++)
#define READ_CONSTANT() (vm.chunk->constants.values[READ_BYTE()])
#define BINARY_OP(op) \
    do { \
        double b = pop(); \
        double a = pop(); \
        push(a op b); \
    } while(false)

VM vm;

static void resetStack()
{
    vm.stackCapacity = 0;
    vm.stack = NULL;
    vm.stackTop = vm.stack;
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
            case OP_ADD:BINARY_OP(+); break;
            case OP_SUBTRACT: BINARY_OP(-); break;
            case OP_MULTIPLY: BINARY_OP(*); break;
            case OP_DIVIDE: BINARY_OP(/); break;
            //case OP_NEGATE: push(-pop()); break;
            case OP_NEGATE: *(vm.stackTop-1) = -(*(vm.stackTop-1)); break;
        }
    }
}

InterpretResult interpret(Chunk* chunk)
{
    vm.chunk = chunk;
    vm.ip = vm.chunk->code;
    return run();
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
#include <stdio.h>
#include "common.h"
#include "vm.h"
#include "debug.h"

#define READ_BYTE() (*vm.ip++)
#define READ_CONSTANT() (vm.chunk->constants.values[READ_BYTE()])
#define READ_CONSTANT_LONG() (vm.chunk->constants.values[READ_BYTE()])

VM vm;

void initVM()
{

}

void freeVM()
{

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
    disassembleInstruction(vm.chunk, (int)(vm.ip - vm.chunk->code));
#endif
        uint8_t instruction;
        switch (instruction = READ_BYTE()) {
            case OP_CONSTANT:
            {
                Value constant = READ_CONSTANT();
                printValue(constant);
                printf("\n");
                break;
            }
            case OP_CONSTANT_LONG:
            {
                Value constant = readConstantLong();
                printValue(constant);
                printf("\n");
                break;
            }
            case OP_RETURN: {
                return INTERPRET_OK;
            }
        }
    }
}

InterpretResult interpret(Chunk* chunk)
{
    vm.chunk = chunk;
    vm.ip = vm.chunk->code;
    return run();
}
#ifndef clox_vm_h
#define clox_vm_h

#include "chunk.h"
#include "table.h"
#include "object.h"
#include "value.h"

#define STACK_MAX 256
#define FRAMES_MAX 64

typedef struct {
    ObjClosure* closure;
    uint8_t* ip;
    Value* slots;
} CallFrame;

typedef struct vm
{
    Value* stack;
    CallFrame frames[FRAMES_MAX];
    int frameCount;
    Value* stackTop;
    Obj* objects;
    Table strings;
    Table globals;
    int stackCapacity;
} VM;

typedef enum {
    INTERPRET_OK,
    INTERPRET_COMPILE_ERROR,
    INTERPRET_RUNTIME_ERROR
} InterpretResult;

extern VM vm;

void initVM();
void freeVM();
InterpretResult interpret(const char* source);
void push(Value value);
Value pop();
#endif
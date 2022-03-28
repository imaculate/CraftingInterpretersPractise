#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <math.h>

#include "common.h"
#include "compile.h"
#include "vm.h"
#include "debug.h"
#include "memory.h"
#include "value.h"
#include "object.h"

#define READ_STRING() AS_STRING(READ_CONSTANT())
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
    vm.frameCount = 0;
    vm.openUpvalues = NULL;
}

static void runtimeError(const char* format, ...)
{
    va_list args;
    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
    fputs("\n", stderr);
    for (int i = vm.frameCount - 1; i >= 0; i--)
    {
        CallFrame* frame = &vm.frames[i];
        ObjFunction* function  = frame->closure->function;
        size_t instruction = frame->ip -function->chunk.code - 1;
        fprintf(stderr, "[line %d] in \n", function->chunk.lines[instruction]);
        if (function->name == NULL)
        {
            fprintf(stderr, "script\n");
        }
        else
        {
            fprintf(stderr, "%s()\n", function->name->chars);
        }
    }

    resetStack();
}

static Value clockNative(int argCount, Value* args)
{
    return NUMBER_VALUE((double)clock()/CLOCKS_PER_SEC);
}

static Value sqrtNative(int argCount, Value* args)
{
    if (argCount != 1)
    {
        runtimeError("Expected one argument but got %d.", argCount);
        return NIL_VALUE;
    }
    return NUMBER_VALUE(sqrt(AS_NUMBER(args[0])));
}

static void defineNative(const char* name, NativeFn function)
{
    push(OBJ_VALUE(copyString(name, (int)strlen(name))));
    push(OBJ_VALUE(newNative(function)));
    tableSet(&vm.globals, AS_STRING(vm.stack[0]), vm.stack[1]);
    pop();
    pop();
}

void initVM()
{
    resetStack();
    vm.objects = NULL;
    vm.grayCount = 0;
    vm.grayCapacity = 0;
    vm.grayStack = NULL;
    vm.bytesAllocated = 0;
    vm.nextGC = 1024 * 1024;
    vm.initString = NULL;
    vm.initString = copyString("init", 4);

    initTable(&vm.strings);
    initTable(&vm.globals);

    defineNative("clock", clockNative);
    defineNative("sqrt", sqrtNative);
}

void freeVM()
{
    freeTable(&vm.strings);
    freeTable(&vm.globals);
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

static bool call(ObjClosure* closure, int argCount)
{
    if (argCount != closure->function->arity)
    {
        runtimeError("Expected %d arguments but got %d.", closure->function->arity, argCount);
        return false;
    }

    if (vm.frameCount == FRAMES_MAX)
    {
        runtimeError("Stack overflow.");
        return false;
    }

    CallFrame* frame = &vm.frames[vm.frameCount++];
    frame->closure = closure;
    frame->ip = closure->function->chunk.code;
    frame->slots = vm.stackTop - argCount - 1;
    return true;
}

static bool callValue(Value callee, int argCount)
{
    if (IS_OBJ(callee))
    {
        switch (OBJ_TYPE(callee))
        {
            case OBJ_BOUND_METHOD:
            {
                ObjBoundMethod* bound = AS_BOUND_METHOD(callee);
                vm.stackTop[-argCount - 1] = bound->receiver;
                return call(bound->method, argCount);
            }
            case OBJ_CLASS:
            {
                ObjClass* klass = AS_CLASS(callee);
                vm.stackTop[-argCount - 1] = OBJ_VALUE(newInstance(klass));
                Value initializer;
                if (tableGet(&klass->methods, vm.initString, &initializer))
                {
                    return call(AS_CLOSURE(initializer), argCount);
                }
                else if (argCount != 0)
                {
                    runtimeError("Expected 0 arguments but got %d", argCount);
                }
                return true;
            }
            case OBJ_NATIVE:
            {
                NativeFn native = AS_NATIVE(callee);
                Value result = native(argCount, vm.stackTop - argCount);
                if (IS_NIL(result)) return false;
                vm.stackTop -= argCount + 1;
                push(result);
                return true;
            }
            case OBJ_CLOSURE:
            {
                return call(AS_CLOSURE(callee), argCount);
            }
            /*case OBJ_FUNCTION:
                return call(AS_FUNCTION(callee), argCount);
            */
            default:
                break;
        }
    }
    runtimeError("Can only call functions and classes.");
    return false;
}

static bool invokeFromClass(ObjClass* klass, ObjString* name, int argCount)
{
    Value method;
    if (tableGet(&klass->methods, name, &method))
    {
        runtimeError("Undefined property '%s'.", name->chars);
        return false;
    }
    return call(AS_CLOSURE(method), argCount);
}

static bool invoke(ObjString* name, int argCount)
{
    Value receiver = peek(argCount);

    if (!IS_INSTANCE(receiver))
    {
        runtimeError("Only instances have methods.");
        return false;
    }
    ObjInstance* instance = AS_INSTANCE(receiver);
    
    Value value;
    if (tableGet(&instance->fields, name, &value))
    {
        vm.stackTop[-argCount - 1] = value;
        return callValue(value, argCount);
    }
    return invokeFromClass(instance->klass, name, argCount);
}

static bool bindMethod(ObjClass* klass, ObjString* name)
{
    Value method;
    if (!tableGet(&klass->methods, name, &method))
    {
        runtimeError("Undefined property '%s'.", name->chars);
        return false;
    }

    ObjBoundMethod* bound = newBoundMethod(peek(0), AS_CLOSURE(method));
    pop();
    push(OBJ_VALUE(bound));
    return true;
}

static ObjUpValue* captureUpvalue(Value* local)
{
    ObjUpValue* preUpvalue = NULL;
    ObjUpValue* upvalue = vm.openUpvalues;
    while (upvalue != NULL && upvalue->location > local)
    {
        preUpvalue = upvalue;
        upvalue = upvalue->next;
    }

    if (upvalue != NULL && upvalue->location == local) {
        return upvalue;
    }

    ObjUpValue* createdUpValue = newUpValue(local);
    createdUpValue->next = upvalue;

    if (preUpvalue == NULL)
    {
        vm.openUpvalues = createdUpValue;
    }
    else
    {
        preUpvalue->next = createdUpValue;
    }
    return createdUpValue;
}

static void closeUpvalues(Value* last)
{
    while (vm.openUpvalues != NULL && vm.openUpvalues->location >= last)
    {
        ObjUpValue* upvalue = vm.openUpvalues;
        upvalue->closed = *upvalue->location;
        upvalue->location = &upvalue->closed;
        vm.openUpvalues = upvalue->next;
    }
}

static void defineMethod(ObjString* name)
{
    Value method = peek(0);
    ObjClass* klass = AS_CLASS(peek(1));
    tableSet(&klass->methods, name, method);
    pop();
}

static bool isFalsey(Value value)
{
    return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static void concatenate()
{
    ObjString* b = AS_STRING(peek(0));
    ObjString* a = AS_STRING(peek(0));

    int length = a->length + b->length;
    char* chars = ALLOCATE(char, length + 1);
    memcpy(chars, a->chars, a->length);
    memcpy(chars + a->length, b->chars, b->length);
    chars[length] = '\0';

    ObjString* result = takeString(chars, length);
    pop();
    pop();
    push(OBJ_VALUE(result));
}

static bool valuesEqual(Value a, Value b)
{
#ifdef NAN_BOXING
    if (IS_NUMBER(a) && IS_NUMBER(b))
    {
        return AS_NUMBER(a) == AS_NUMBER(b);
    }
    return a == b;
#else
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
#endif
}

Value readConstantLong()
{
    long index = 0;
    CallFrame* frame = &vm.frames[vm.frameCount - 1];
    for (int i = 0; i < 3; i++)
    {
        // READ_BYTE()
        index |= (*frame->ip++) << (8*i);
    }
    return frame->closure->function->chunk.constants.values[index];
}

static InterpretResult run()
{
    CallFrame* frame = &vm.frames[vm.frameCount - 1];
#define READ_BYTE() (*frame->ip++)
#define READ_CONSTANT() (frame->closure->function->chunk.constants.values[READ_BYTE()])
#define READ_SHORT() \
   (frame->ip += 2, (uint16_t)(( frame->ip[-2] << 8) | frame->ip[-1]))
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
    disassembleInstruction(&frame->closure->function->chunk, (int)(frame->ip - frame->closure->function->chunk.code));
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
            case OP_PRINT:
            {
                printValue(pop());
                printf("\n");
                break;
            }
            case OP_JUMP_IF_FALSE:
            {
                uint16_t offset = READ_SHORT();
                if (isFalsey(peek(0))) frame->ip += offset;
                break;
            }
            case OP_JUMP:
            {
                uint16_t offset = READ_SHORT();
                frame->ip += offset;
                break;
            }
            case OP_LOOP:
            {
                uint16_t offset = READ_SHORT();
                frame->ip -= offset;
                break;
            }
            case OP_CALL:
            {
                int argCount = READ_BYTE();
                if (!callValue(peek(argCount), argCount))
                {
                    return INTERPRET_RUNTIME_ERROR;
                }
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_CLASS:
            {
                push(OBJ_VALUE(newClass(READ_STRING())));
                break;
            }
            case OP_INHERIT:
            {
                Value superclass = peek(1);
                if (!IS_CLASS(superclass))
                {
                    runtimeError("Superclass must be a class.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjClass* subclass = AS_CLASS(peek(0));
                tableAddAll(&AS_CLASS(superclass)->methods, &subclass->methods);
                pop();
                break;
            }
            case OP_GET_SUPER:
            {
                ObjString* name = READ_STRING();
                ObjClass* superclass = AS_CLASS(pop());

                if (!bindMethod(superclass, name))
                {
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_METHOD:
            {
                defineMethod(READ_STRING());
                break;
            }
            case OP_INVOKE:
            {
                ObjString* method = READ_STRING();
                int argCount = READ_BYTE();
                if (!invoke(method, argCount))
                {
                    return INTERPRET_RUNTIME_ERROR;
                }
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_SUPER_INVOKE:
            {
                ObjString* method = READ_STRING();
                int argCount = READ_BYTE();
                ObjClass* superClass = AS_CLASS(pop());
                if (!invokeFromClass(superClass, method, argCount))
                {
                    return INTERPRET_RUNTIME_ERROR;
                }

                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_CLOSURE: {
                ObjFunction* function = AS_FUNCTION(READ_CONSTANT());
                ObjClosure* closure = newClosure(function);
                push(OBJ_VALUE(closure));

                for (int i = 0; i < closure->upvalueCount; i++)
                {
                    uint8_t isLocal = READ_BYTE();
                    uint8_t index = READ_BYTE();
                    if (isLocal)
                    {
                        closure->upvalues[i] = captureUpvalue(frame->slots + index);
                    }
                    else
                    {
                        closure->upvalues[i] = frame->closure->upvalues[index];
                    }
                }
                break;
            }
            case OP_RETURN: {
                Value result = pop();
                closeUpvalues(frame->slots);
                vm.frameCount--;
                if (vm.frameCount == 0)
                {
                    pop();
                    return INTERPRET_OK;
                }
                
                vm.stackTop = frame->slots;
                push(result);
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_NIL: push(NIL_VALUE); break;
            case OP_TRUE: push(BOOL_VALUE(true)); break;
            case OP_FALSE: push(BOOL_VALUE(false)); break;
            case OP_POP: pop(); break;
            case OP_CLOSE_UPVALUE:
            {
                closeUpvalues(vm.stackTop - 1);
                pop();
                break;
            }
            case OP_GET_LOCAL:
            {
                uint8_t slot = READ_BYTE();
                push(frame->slots[slot]);
                break;
            }
            case OP_SET_LOCAL:
            {
                uint8_t slot = READ_BYTE();
                frame->slots[slot] = peek(0);
                break;
            }
            case OP_GET_GLOBAL:
            {
                ObjString* name = READ_STRING();
                Value value;
                if (!tableGet(&vm.globals, name, &value))
                {
                    runtimeError("Undefined variable '%s'.", name->chars);
                    return INTERPRET_RUNTIME_ERROR;
                }
                push(value);
                break;
            }
            case OP_DEFINE_GLOBAL:
            {
                ObjString* name = READ_STRING();
                tableSet(&vm.globals, name, peek(0));
                pop();
                break;
            }
            case OP_SET_GLOBAL:
            {
                ObjString* name = READ_STRING();
                if (!tableSet(&vm.globals, name, peek(0)))
                {
                    tableDelete(&vm.globals, name);
                    runtimeError("Assignment to undefined variable '%s'.", name->chars);
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_ADD:
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
            case OP_GET_UPVALUE:
            {
                uint8_t slot = READ_BYTE();
                push(*frame->closure->upvalues[slot]->location);
                break;
            }
            case OP_SET_UPVALUE:
            {
                uint8_t slot = READ_BYTE();
                *frame->closure->upvalues[slot]->location = peek(0);
                break;
            }
            case OP_GET_PROPERTY:
            {
                if (!IS_INSTANCE(peek(0)))
                {
                    runtimeError("Only instances have properties.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjInstance* instance = AS_INSTANCE(peek(0));
                ObjString* name = READ_STRING();

                Value value;
                if (tableGet(&instance->fields, name, &value))
                {
                    pop();
                    push(value);
                    break;
                }

                if (!bindMethod(instance->klass, name))
                {
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_SET_PROPERTY:
            {
                if (!IS_INSTANCE(peek(1)))
                {
                    runtimeError("Only instances have fields.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjInstance* instance = AS_INSTANCE(peek(1));
                ObjString* name = READ_STRING();
                tableSet(&instance->fields, name, peek(0));
                Value value = pop();
                pop();
                push(value);
                break;
            }
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
    printf("Starting compilation\n");
    ObjFunction* function = compile(source);
    printf("Compilation completed\n");
    if (function == NULL) return INTERPRET_COMPILE_ERROR;
    printf("Compilation completed with no errors\n");

    push(OBJ_VALUE(function));
    /*CallFrame* frame = &vm.frames[vm.frameCount++];
    frame->closure = newClosure(function);
    frame->ip = function->chunk.code;
    frame->slots = vm.stack;*/

    ObjClosure* closure = newClosure(function);
    pop();
    push(OBJ_VALUE(closure));
    call(closure, 0);

    return run();
}
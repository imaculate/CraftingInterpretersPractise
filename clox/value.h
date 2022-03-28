#ifndef clox_value_h
#define clox_value_h

#include <string.h>

#include "common.h"

typedef struct Obj Obj;
typedef struct ObjString ObjString;

#ifdef NAN_BOXING

#define QNAN ((uint64_t)0x7ffc000000000000)
#define SIGN_BIT ((uint64_t)0x8000000000000000)

#define TAG_NIL 1
#define TAG_FALSE 2
#define TAG_TRUE 3

typedef uint64_t Value;

#define IS_BOOL(value) (((value) | 1) == TRUE_VAL)
#define IS_NIL(value) ((value) == NIL_VALUE)
#define IS_NUMBER(value) (((value) & QNAN) != QNAN)
#define IS_OBJ(value) (((value) & (SIGN_BIT | QNAN)) == (SIGN_BIT | QNAN))
#define AS_NUMBER(value) valueToNum(value)
#define AS_BOOL(value) ((value) == TRUE_VAL)
#define AS_OBJ(value) ((Obj*)(uintptr_t)((value) & ~(SIGN_BIT | QNAN)))

#define BOOL_VALUE(b) ((b) ? TRUE_VAL : FALSE_VAL)
#define FALSE_VAL (Value)(uint64_t) (QNAN | TAG_FALSE)
#define TRUE_VAL (Value)(uint64_t) (QNAN | TAG_TRUE)
#define NIL_VALUE ((Value)(uint64_t)(QNAN | TAG_NIL))
#define NUMBER_VALUE(num) numToValue(num)
#define OBJ_VALUE(obj) (Value) (SIGN_BIT | QNAN | (uint64_t)(uintptr_t) (obj))

static inline Value numToValue(double num)
{
    Value value;
    memcpy(&value, &num, sizeof(double));
    return value;
}

static inline double valueToNum(Value value)
{
    double num;
    memcpy(&num, &value, sizeof(Value));
    return num;
}

#else

typedef enum {
    VAL_BOOL,
    VAL_NIL,
    VAL_NUMBER,
    VAL_OBJ,
} ValueType;

typedef struct {
    ValueType type;
    union
    {
        bool boolean;
        double number;
        Obj* obj;
    } as;
} Value;

#define IS_BOOL(value) ((value).type == VAL_BOOL)
#define IS_NIL(value) ((value).type == VAL_NIL)
#define IS_NUMBER(value) ((value).type == VAL_NUMBER)
#define IS_OBJ(value) ((value).type == VAL_OBJ)

#define AS_BOOL(value) (value).as.boolean
#define AS_NUMBER(value) (value).as.number
#define AS_OBJ(value)    (value).as.obj

#define BOOL_VALUE(value)   ((Value) { VAL_BOOL, {.boolean = value}})
#define NIL_VALUE           ((Value) {VAL_NIL, {.number = 0}})
#define NUMBER_VALUE(value) ((Value) {VAL_NUMBER, {.number = value}})
#define OBJ_VALUE(object)    ((Value) {VAL_OBJ, {.obj = (Obj*)object}})

#endif

typedef struct {
    long capacity;
    long count;
    Value* values;
} ValueArray;

void initValueArray(ValueArray* array);
void writeValueArray(ValueArray* array, Value value);
void freeValueArray(ValueArray* array);
void printValue(Value value);
#endif
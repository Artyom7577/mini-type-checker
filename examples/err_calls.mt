// err_calls.mt — function-call type errors.  Should FAIL (3 type errors).

int add(int a, int b) { return a + b; }   // type: (int, int) -> int

int f() {
    int x = 1;
    int bad1 = add(1, 2, 3);   // ERROR: wrong arity (3 args, expects 2)
    int bad2 = add(1, 2.0);    // ERROR: argument 2 expects int but got double
    int bad3 = x(5);           // ERROR: calling a non-function (int)
    return x;
}

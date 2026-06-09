// ok_arrays.mt — array declaration and indexing (Array(T)).  Should SUCCEED.

int sum(int data[10], int n) {      // parameter of type Array(int)
    int total = 0;
    int i     = 0;
    while (i < n) {
        total = total + data[i];    // data[i] : int
        i = i + 1;
    }
    return total;
}

int demo() {
    int xs[5];                      // local array Array(int)
    xs[0] = 42;                     // index target is an lvalue
    return xs[0];                   // int
}

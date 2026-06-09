// err_arrays_pointers.mt — array/pointer type errors.  Should FAIL (4 type errors).

int f() {
    int x = 5;
    int arr[3];
    int bad1 = x[0];       // ERROR: indexing a non-array (int)
    int bad2 = arr[1.5];   // ERROR: array index must be int (got double)
    int bad3 = *x;         // ERROR: dereferencing a non-pointer (int)
    int* p   = &arr;       // ERROR: cannot take the address of an array
    return x;
}

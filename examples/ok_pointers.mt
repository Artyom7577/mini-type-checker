// ok_pointers.mt — address-of (&), dereference (*), pointer-to-pointer.  Should SUCCEED.

int deref2(int** pp) {     // pp : Pointer(Pointer(int))
    int* p = *pp;          // *pp : Pointer(int)
    return *p;             // *p  : int
}

int demo() {
    int   a  = 7;
    int*  p  = &a;         // &a : Pointer(int)
    int** pp = &p;         // &p : Pointer(Pointer(int))
    return *(*pp);         // dereference twice -> int
}

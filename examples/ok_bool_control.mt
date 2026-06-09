// ok_bool_control.mt — bool type, comparisons, logical ops, if/while.  Should SUCCEED.

bool check(int n) {
    bool ok = true;
    int  i  = 0;
    while (i < n) {                 // i < n : int < int -> bool
        if (i == 5) {               // i == 5 : bool
            ok = false;
        } else {
            ok = ok && (i >= 0);    // bool && bool -> bool
        }
        i = i + 1;
    }
    return ok && !false;            // logical not + and -> bool
}

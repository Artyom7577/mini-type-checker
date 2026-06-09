// err_stmts.mt — statement/declaration type errors.  Should FAIL (4 type errors).

int g = 10;
int g = 20;                // ERROR: redefinition of 'g' (global)

int f() {
    if (5) {               // ERROR: if condition must be bool (got int)
        return 0;
    }
    int n = 3;
    n = 2.5;               // ERROR: cannot assign double to an int target
    return undefined;      // ERROR: undefined name 'undefined'
}

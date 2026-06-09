// err_arith.mt — arithmetic type errors.  Should FAIL (3 type errors).

int f() {
    int  a = 5;
    char c = 'x';
    int  bad1 = a + c;     // ERROR: '+' on int and char (operands must match & be numeric)
    double d  = 2.5;
    int  bad2 = a % d;     // ERROR: '%' requires int operands (d is double)
    int  bad3 = a + 1.0;   // ERROR: '+' on int and double (mixed numeric types)
    return bad1;
}

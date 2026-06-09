// ok_arithmetic.mt — int/double arithmetic and explicit casts.  Should SUCCEED.

int   gi = 2 + 3 * 4;       // global int  (precedence: 2 + (3*4) = 14)
double gd = 1.5 + 2.5;      // global double

int compute() {
    int    a = 10;
    double b = 3.14;
    int    c = a * 2 - 5;   // int arithmetic
    double d = b / 2.0;     // double arithmetic
    int    e = (int) b;     // cast double -> int
    double g = (double) a;  // cast int -> double
    int    m = a % 3;       // % on ints
    return c + e + m;       // int, matches the int return type
}

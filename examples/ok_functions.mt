// ok_functions.mt — function declarations, calls, recursion, function types.  Should SUCCEED.
// The type of `factorial` is  int -> int ; the type of `scale` is  (double, double) -> double.

int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);     // recursive call (resolved in pass 1)
}

double scale(double x, double k) {
    return x * k;
}

int main() {
    int    f = factorial(5);         // int
    double s = scale(2.0, 3.0);      // double
    return f;
}

package minitype.types;

/**
 * Type — the abstract base of every MiniType "type expression" (the course's
 * Տիպերի արտահայտություն).  Concrete kinds:
 *
 *   BaseType     int, double, char, bool, void           (atomic / base types)
 *   ArrayType    Array(T)                                 (type constructor)
 *   PointerType  Pointer(T)                               (type constructor)
 *   FunctionType T1 x ... x Tn -> R                       (type constructor)
 *   RecordType   struct Name { ... }                      (type constructor)
 *   ErrorType    type_error                               (error sentinel)
 *
 * Type equality is given by {@link #same(Type)}: STRUCTURAL for base/array/
 * pointer/function, BY-NAME for records.
 */
public abstract class Type {

    /** Are this and {@code other} the same type? (structural / by-name) */
    public abstract boolean same(Type other);

    /** True only for the {@code type_error} sentinel. */
    public boolean isError() { return false; }

    /** True for the numeric base types int and double. */
    public boolean isNumeric() { return false; }

    @Override public abstract String toString();
}

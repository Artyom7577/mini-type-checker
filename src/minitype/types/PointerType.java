package minitype.types;

/**
 * Pointer(T) — the pointer type constructor. Course rule:  D -> T * id ;   =>   Pointer(T). Structural equality:
 * Pointer(A) == Pointer(B) iff A == B.
 */
public final class PointerType extends Type {

    public final Type pointee;

    public PointerType(Type pointee) {
        this.pointee = pointee;
    }

    @Override
    public boolean same(Type other) {
        return other instanceof PointerType && pointee.same(((PointerType) other).pointee);
    }

    @Override
    public String toString() {
        return "Pointer(" + pointee + ")";
    }
}

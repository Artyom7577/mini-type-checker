package minitype.types;

/**
 * Array(T) — the array type constructor.
 * Course rule:  D -> T id [ num ] ;   =>   Array(T).
 * Structural equality: Array(A) == Array(B) iff A == B.
 */
public final class ArrayType extends Type {

    public final Type elem;
    public ArrayType(Type elem) { this.elem = elem; }

    @Override public boolean same(Type other) {
        return other instanceof ArrayType && elem.same(((ArrayType) other).elem);
    }

    @Override public String toString() { return "Array(" + elem + ")"; }
}

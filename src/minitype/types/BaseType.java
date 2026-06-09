package minitype.types;

/**
 * The atomic base types: int, double, char, bool, void. These are SINGLETONS, so reference identity ({@code ==}) is
 * exactly structural equality — which is what {@link #same(Type)} uses.
 */
public final class BaseType extends Type {

    public final String name;

    private BaseType(String name) {
        this.name = name;
    }

    public static final BaseType INT = new BaseType("int");
    public static final BaseType DOUBLE = new BaseType("double");
    public static final BaseType CHAR = new BaseType("char");
    public static final BaseType BOOL = new BaseType("bool");
    public static final BaseType VOID = new BaseType("void");

    @Override
    public boolean same(Type other) {
        return this == other;
    }

    @Override
    public boolean isNumeric() {
        return this == INT || this == DOUBLE;
    }

    @Override
    public String toString() {
        return name;
    }
}

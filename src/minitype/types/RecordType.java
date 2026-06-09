package minitype.types;

import java.util.LinkedHashMap;

/**
 * Record / struct type, e.g.  struct Point { int x; int y; }.
 * <p>
 * Equivalence discipline: BY-NAME — two record types are the same iff they have the same struct name.  This matches C
 * and most languages, and makes recursive structs (e.g. {@code struct Node { int data; Node* next; }}) trivial to
 * represent: a field of type {@code Node*} is just Pointer(<the named Node>). The course's Type-Checking deck discusses
 * STRUCTURAL equivalence as the alternative discipline (see README).
 */
public final class RecordType extends Type {

    public final String name;
    /**
     * Field name -> field type, in declaration order.
     */
    public final LinkedHashMap<String, Type> fields = new LinkedHashMap<>();

    public RecordType(String name) {
        this.name = name;
    }

    @Override
    public boolean same(Type other) {
        return other instanceof RecordType && name.equals(((RecordType) other).name);
    }

    @Override
    public String toString() {
        return "struct " + name;
    }
}

package minitype;

import minitype.types.RecordType;
import minitype.types.Type;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * SymbolTable — the course's getType/addType, extended with lexical SCOPES.
 *
 * A stack of scopes models the global scope, each function-body scope, and any
 * nested block scope.  {@link #define} = addType (into the current scope),
 * {@link #lookup} = getType (searches inner-to-outer).  Named struct (record)
 * types live in a separate global table since structs are always top-level.
 */
public final class SymbolTable {

    /** Scope stack; the LAST element pushed (top) is the innermost scope. */
    private final Deque<Map<String, Type>> scopes = new ArrayDeque<>();

    /** Global table of named struct types (struct declarations are top-level). */
    private final Map<String, RecordType> structs = new HashMap<>();

    public SymbolTable() { enterScope(); } // create the global scope

    public void enterScope() { scopes.push(new HashMap<>()); }
    public void exitScope()  { scopes.pop(); }

    /**
     * addType(id, type): define {@code name} in the CURRENT (innermost) scope.
     * Returns false if the name is already defined in that same scope
     * (i.e. a redefinition); shadowing an outer scope is allowed.
     */
    public boolean define(String name, Type type) {
        Map<String, Type> current = scopes.peek();
        if (current.containsKey(name)) return false;
        current.put(name, type);
        return true;
    }

    /** getType(id): search from the innermost scope outward; null if undefined. */
    public Type lookup(String name) {
        for (Map<String, Type> scope : scopes)   // ArrayDeque iterates top -> bottom
            if (scope.containsKey(name)) return scope.get(name);
        return null;
    }

    // --- named struct (record) types -----------------------------------
    public void defineStruct(RecordType r)       { structs.put(r.name, r); }
    public RecordType lookupStruct(String name)  { return structs.get(name); }
    public boolean structExists(String name)     { return structs.containsKey(name); }
}

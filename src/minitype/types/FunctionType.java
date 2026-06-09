package minitype.types;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Function type  T1 x T2 x ... x Tn -> R. Course rule:  D -> T id ( L ) ;   =>   addType(id, L.type -> T.type), where
 * the parameter list L.type is the cartesian product of the parameter types.  Printed as "(T1, T2) -> R" (or "T1 -> R"
 * for a single parameter). Structural equality: same arity, pairwise-equal params, equal return type.
 */
public final class FunctionType extends Type {

    public final List<Type> params;
    public final Type ret;

    public FunctionType(List<Type> params, Type ret) {
        this.params = params;
        this.ret = ret;
    }

    @Override
    public boolean same(Type other) {
        if (!(other instanceof FunctionType)) {
            return false;
        }
        FunctionType f = (FunctionType) other;
        if (params.size() != f.params.size()) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            if (!params.get(i).same(f.params.get(i))) {
                return false;
            }
        }
        return ret.same(f.ret);
    }

    @Override
    public String toString() {
        String ps = params.stream().map(Type::toString).collect(Collectors.joining(", "));
        String lhs = (params.size() == 1) ? ps : "(" + ps + ")";
        return lhs + " -> " + ret;
    }
}

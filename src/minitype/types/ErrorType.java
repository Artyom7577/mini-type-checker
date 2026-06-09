package minitype.types;

/**
 * type_error — the single error sentinel from the course SDD.
 *
 * It is produced by any failing typing rule and PROPAGATES upward.  To avoid a
 * cascade of duplicate messages, each rule first checks whether an operand is
 * already {@code type_error} (via {@link #isError()}); if so it returns
 * {@code type_error} WITHOUT emitting a new message.  {@link #same(Type)} treats
 * {@code type_error} as equal only to itself, so an unguarded comparison such as
 * {@code declaredType.same(VOID)} on an already-errored type does NOT misfire.
 */
public final class ErrorType extends Type {

    public static final ErrorType INSTANCE = new ErrorType();
    private ErrorType() {}

    @Override public boolean same(Type other) { return other instanceof ErrorType; }
    @Override public boolean isError() { return true; }
    @Override public String toString() { return "type_error"; }
}

package minitype;

import minitype.types.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * TypeChecker — the Syntax-Directed Definition (SDD) of MiniType.
 *
 * It extends the ANTLR-generated {@code MiniTypeBaseVisitor<Type>}.  Each
 * {@code visitX} method corresponds to ONE grammar production and is the
 * semantic action that SYNTHESIZES that node's type from its children's types —
 * exactly the course's {@code { ... }} rules that compute the {@code .type}
 * attribute.  On a mismatch it records an error via {@link Diagnostics} and
 * returns {@link ErrorType} ({@code type_error}), which propagates upward
 * WITHOUT cascading (each rule first checks {@code isError()} on its operands).
 *
 * The program is checked in two passes (see {@link #visitProgram}) so that
 * declarations may be used before the point where they textually appear
 * (mutual recursion, forward references, recursive structs).
 */
public final class TypeChecker extends MiniTypeBaseVisitor<Type> {

    private final SymbolTable sym = new SymbolTable();
    private final Diagnostics diag;

    /** Return type of the function currently being checked (for `return`). */
    private Type currentReturnType = null;

    public TypeChecker(Diagnostics diag) { this.diag = diag; }

    // shorthands for the base types
    private static final Type INT = BaseType.INT, DOUBLE = BaseType.DOUBLE,
                              CHAR = BaseType.CHAR, BOOL = BaseType.BOOL, VOID = BaseType.VOID;
    private static final Type ERR = ErrorType.INSTANCE;

    /** Report an error at {@code at} and return the {@code type_error} sentinel. */
    private Type fail(ParserRuleContext at, String message) {
        diag.error(at.getStart(), message);
        return ERR;
    }

    // =======================================================================
    //  PROGRAM  (two passes)
    // =======================================================================
    @Override
    public Type visitProgram(MiniTypeParser.ProgramContext ctx) {
        // Pass 1a — register all struct NAMES (empty), so a field may refer to
        //           any struct, including itself via a pointer.
        for (MiniTypeParser.TopDeclContext td : ctx.topDecl()) {
            if (td.structDecl() != null) {
                String name = td.structDecl().ID().getText();
                if (sym.structExists(name))
                    diag.error(td.structDecl().ID().getSymbol(), "redefinition of struct '" + name + "'");
                else
                    sym.defineStruct(new RecordType(name));
            }
        }
        // Pass 1b — resolve struct FIELDS now that all names exist.
        for (MiniTypeParser.TopDeclContext td : ctx.topDecl())
            if (td.structDecl() != null) declareStructFields(td.structDecl());

        // Pass 1c — register function signatures and global variables.
        for (MiniTypeParser.TopDeclContext td : ctx.topDecl()) {
            if (td.funcDecl() != null) declareFunction(td.funcDecl());
            else if (td.varDecl() != null) declareVarType(td.varDecl());
        }
        // Pass 2 — check function bodies and global initialisers.
        for (MiniTypeParser.TopDeclContext td : ctx.topDecl()) {
            if (td.funcDecl() != null) checkFunctionBody(td.funcDecl());
            else if (td.varDecl() != null) checkInit(sym.lookup(td.varDecl().ID().getText()), td.varDecl());
        }
        return VOID;
    }

    // -----------------------------------------------------------------------
    //  Declaration helpers
    // -----------------------------------------------------------------------

    /** Resolve a `type` node (base/struct + pointer stars) to a Type value. */
    private Type resolveType(MiniTypeParser.TypeContext ctx) {
        String base = ctx.typeName().getText();
        Type t;
        switch (base) {
            case "int":    t = INT;    break;
            case "double": t = DOUBLE; break;
            case "char":   t = CHAR;   break;
            case "bool":   t = BOOL;   break;
            case "void":   t = VOID;   break;
            default: // a struct name
                RecordType r = sym.lookupStruct(base);
                if (r == null) { diag.error(ctx.getStart(), "unknown type '" + base + "'"); t = ERR; }
                else t = r;
        }
        for (int i = 0; i < ctx.star().size(); i++) t = new PointerType(t); // each '*' wraps in Pointer
        return t;
    }

    /** Resolve struct fields (called in pass 1b). */
    private void declareStructFields(MiniTypeParser.StructDeclContext sd) {
        RecordType rec = sym.lookupStruct(sd.ID().getText());
        if (rec == null) return; // was a redefinition; skip
        for (MiniTypeParser.FieldDeclContext f : sd.fieldDecl()) {
            Type ft = resolveType(f.type());
            // void check is on the ELEMENT type, before any array wrapping, so
            // that `void f[3];` (an array of void) is also rejected.
            if (ft == VOID) { diag.error(f.getStart(), "field '" + f.ID().getText() + "' cannot have type void"); ft = ERR; }
            if (f.arraySuffix() != null) ft = new ArrayType(ft);
            if (rec.fields.containsKey(f.ID().getText()))
                diag.error(f.ID().getSymbol(), "duplicate field '" + f.ID().getText() + "' in struct " + rec.name);
            else
                rec.fields.put(f.ID().getText(), ft);
        }
    }

    /** Register a function signature (called in pass 1c). */
    private void declareFunction(MiniTypeParser.FuncDeclContext fd) {
        Type ret = resolveType(fd.type());
        List<Type> params = new ArrayList<>();
        if (fd.paramList() != null)
            for (MiniTypeParser.ParamContext p : fd.paramList().param()) {
                Type pt = resolveType(p.type());
                if (pt == VOID) { diag.error(p.getStart(), "parameter '" + p.ID().getText() + "' cannot have type void"); pt = ERR; }
                if (p.arraySuffix() != null) pt = new ArrayType(pt);
                params.add(pt);
            }
        if (!sym.define(fd.ID().getText(), new FunctionType(params, ret)))
            diag.error(fd.ID().getSymbol(), "redefinition of '" + fd.ID().getText() + "'");
    }

    /** Resolve, void-check and define a variable; returns its declared type. */
    private Type declareVarType(MiniTypeParser.VarDeclContext vd) {
        Type t = resolveType(vd.type());
        // void check is on the ELEMENT type, before any array wrapping, so that
        // `void a[3];` (an array of void) is rejected as well as plain `void a;`.
        if (t == VOID) { diag.error(vd.getStart(), "variable '" + vd.ID().getText() + "' cannot have type void"); t = ERR; }
        if (vd.arraySuffix() != null) t = new ArrayType(t);
        if (!sym.define(vd.ID().getText(), t))
            diag.error(vd.ID().getSymbol(), "redefinition of '" + vd.ID().getText() + "'");
        return t;
    }

    /** Check an optional initialiser against the declared type. */
    private void checkInit(Type declared, MiniTypeParser.VarDeclContext vd) {
        if (vd.expr() == null) return;
        if (declared == null) declared = ERR;
        Type init = visit(vd.expr());
        if (!declared.isError() && !init.isError() && !declared.same(init))
            diag.error(vd.expr().getStart(),
                    "cannot initialize '" + vd.ID().getText() + "' of type " + declared + " with value of type " + init);
    }

    /** Check a function body (called in pass 2). */
    private void checkFunctionBody(MiniTypeParser.FuncDeclContext fd) {
        Type ret = resolveType(fd.type());
        Type saved = currentReturnType;
        currentReturnType = ret;
        sym.enterScope(); // function scope holds the parameters
        if (fd.paramList() != null)
            for (MiniTypeParser.ParamContext p : fd.paramList().param()) {
                Type pt = resolveType(p.type());
                if (p.arraySuffix() != null) pt = new ArrayType(pt);
                if (!sym.define(p.ID().getText(), pt))
                    diag.error(p.ID().getSymbol(), "duplicate parameter '" + p.ID().getText() + "'");
            }
        visit(fd.block());
        sym.exitScope();
        currentReturnType = saved;
    }

    // =======================================================================
    //  STATEMENTS
    // =======================================================================

    @Override public Type visitDeclStmt(MiniTypeParser.DeclStmtContext ctx) {
        MiniTypeParser.VarDeclContext vd = ctx.varDecl();
        Type t = declareVarType(vd);
        checkInit(t, vd);
        return VOID;
    }

    @Override public Type visitBlockStmt(MiniTypeParser.BlockStmtContext ctx) {
        return visit(ctx.block());
    }

    @Override public Type visitBlock(MiniTypeParser.BlockContext ctx) {
        sym.enterScope();
        for (MiniTypeParser.StmtContext s : ctx.stmt()) visit(s);
        sym.exitScope();
        return VOID;
    }

    @Override public Type visitIfStmt(MiniTypeParser.IfStmtContext ctx) {
        Type c = visit(ctx.expr());
        if (!c.isError() && !c.same(BOOL))
            diag.error(ctx.expr().getStart(), "if condition must be bool, got " + c);
        for (MiniTypeParser.StmtContext s : ctx.stmt()) visit(s); // then + optional else
        return VOID;
    }

    @Override public Type visitWhileStmt(MiniTypeParser.WhileStmtContext ctx) {
        Type c = visit(ctx.expr());
        if (!c.isError() && !c.same(BOOL))
            diag.error(ctx.expr().getStart(), "while condition must be bool, got " + c);
        visit(ctx.stmt());
        return VOID;
    }

    @Override public Type visitReturnStmt(MiniTypeParser.ReturnStmtContext ctx) {
        Type expected = (currentReturnType == null) ? VOID : currentReturnType;
        if (expected.isError()) {                 // the function's return type already errored; don't cascade
            if (ctx.expr() != null) visit(ctx.expr());
            return VOID;
        }
        if (ctx.expr() == null) {
            if (expected != VOID)
                diag.error(ctx.getStart(), "return without a value in a function returning " + expected);
        } else {
            Type t = visit(ctx.expr());
            if (expected == VOID)
                diag.error(ctx.expr().getStart(), "return with a value in a void function");
            else if (!t.isError() && !t.same(expected))
                diag.error(ctx.expr().getStart(), "return type mismatch: expected " + expected + ", got " + t);
        }
        return VOID;
    }

    @Override public Type visitAssignStmt(MiniTypeParser.AssignStmtContext ctx) {
        MiniTypeParser.ExprContext lhs = ctx.expr(0), rhs = ctx.expr(1);
        Type lt = visit(lhs);
        Type rt = visit(rhs);
        if (!isLvalue(lhs))
            diag.error(lhs.getStart(), "left-hand side of '=' is not assignable");
        else if (lt instanceof FunctionType)
            diag.error(lhs.getStart(), "cannot assign to a function");
        else if (!lt.isError() && !rt.isError() && !lt.same(rt))
            diag.error(ctx.getStart(), "cannot assign value of type " + rt + " to a target of type " + lt);
        return VOID;
    }

    @Override public Type visitExprStmt(MiniTypeParser.ExprStmtContext ctx) {
        visit(ctx.expr());
        return VOID;
    }

    /** An expression is assignable / addressable iff it is a variable, an array
     *  element, a struct field, or a pointer dereference. */
    private boolean isLvalue(MiniTypeParser.ExprContext e) {
        if (e instanceof MiniTypeParser.IdentContext) return true;
        if (e instanceof MiniTypeParser.IndexContext) return true;
        if (e instanceof MiniTypeParser.FieldContext) return true;
        if (e instanceof MiniTypeParser.ParenContext)
            return isLvalue(((MiniTypeParser.ParenContext) e).expr());
        if (e instanceof MiniTypeParser.UnaryContext)
            return ((MiniTypeParser.UnaryContext) e).op.getText().equals("*"); // *p is an lvalue
        return false;
    }

    // =======================================================================
    //  EXPRESSIONS  (each method synthesises the node's .type)
    // =======================================================================

    @Override public Type visitIntLit(MiniTypeParser.IntLitContext ctx)       { return INT; }
    @Override public Type visitDoubleLit(MiniTypeParser.DoubleLitContext ctx) { return DOUBLE; }
    @Override public Type visitCharLit(MiniTypeParser.CharLitContext ctx)     { return CHAR; }
    @Override public Type visitBoolLit(MiniTypeParser.BoolLitContext ctx)     { return BOOL; }

    @Override public Type visitParen(MiniTypeParser.ParenContext ctx) { return visit(ctx.expr()); }

    @Override public Type visitIdent(MiniTypeParser.IdentContext ctx) {
        String name = ctx.ID().getText();
        Type t = sym.lookup(name);
        if (t == null) return fail(ctx, "undefined name '" + name + "'");
        return t;
    }

    @Override public Type visitCast(MiniTypeParser.CastContext ctx) {
        Type target = baseTypeOf(ctx.baseType());
        Type t = visit(ctx.expr());
        if (t.isError()) return ERR;
        if (allowConversion(t, target)) return target;
        return fail(ctx, "cannot cast " + t + " to " + target);
    }

    @Override public Type visitUnary(MiniTypeParser.UnaryContext ctx) {
        String op = ctx.op.getText();
        Type t = visit(ctx.expr());
        if (t.isError()) return ERR;
        switch (op) {
            case "!":
                if (t.same(BOOL)) return BOOL;
                return fail(ctx, "operator '!' requires a bool operand, got " + t);
            case "-":
                if (t.isNumeric()) return t;
                return fail(ctx, "unary '-' requires int or double, got " + t);
            case "*": // dereference
                if (t instanceof PointerType) return ((PointerType) t).pointee;
                return fail(ctx, "cannot dereference non-pointer type " + t);
            case "&": // address-of
                if (!isLvalue(ctx.expr())) return fail(ctx, "cannot take the address of a non-lvalue");
                if (t instanceof ArrayType) return fail(ctx, "cannot take the address of an array (" + t + ")");
                if (t instanceof FunctionType) return fail(ctx, "cannot take the address of a function");
                return new PointerType(t);
            default:
                return fail(ctx, "unknown unary operator '" + op + "'");
        }
    }

    @Override public Type visitMulDiv(MiniTypeParser.MulDivContext ctx) {
        String op = ctx.op.getText();
        Type l = visit(ctx.expr(0)), r = visit(ctx.expr(1));
        if (l.isError() || r.isError()) return ERR;
        if (op.equals("%")) {
            if (l.same(INT) && r.same(INT)) return INT;
            return fail(ctx, "operator '%' requires int operands, got " + l + " and " + r);
        }
        if (l.same(r) && l.isNumeric()) return l;
        return fail(ctx, "operator '" + op + "' requires matching int or double operands, got " + l + " and " + r);
    }

    @Override public Type visitAddSub(MiniTypeParser.AddSubContext ctx) {
        String op = ctx.op.getText();
        Type l = visit(ctx.expr(0)), r = visit(ctx.expr(1));
        if (l.isError() || r.isError()) return ERR;
        if (l.same(r) && l.isNumeric()) return l;
        return fail(ctx, "operator '" + op + "' requires matching int or double operands, got " + l + " and " + r);
    }

    @Override public Type visitRel(MiniTypeParser.RelContext ctx) {
        String op = ctx.op.getText();
        Type l = visit(ctx.expr(0)), r = visit(ctx.expr(1));
        if (l.isError() || r.isError()) return ERR;
        if (l.same(r) && (l.same(INT) || l.same(DOUBLE) || l.same(CHAR))) return BOOL;
        return fail(ctx, "operator '" + op + "' requires matching int, double or char operands, got " + l + " and " + r);
    }

    @Override public Type visitEq(MiniTypeParser.EqContext ctx) {
        String op = ctx.op.getText();
        Type l = visit(ctx.expr(0)), r = visit(ctx.expr(1));
        if (l.isError() || r.isError()) return ERR;
        // Course rule: operands must be the SAME type and comparable
        // (int, double, char, bool, or Pointer).  Arrays/functions are not comparable.
        boolean comparable = l.same(INT) || l.same(DOUBLE) || l.same(CHAR) || l.same(BOOL) || l instanceof PointerType;
        if (l.same(r) && comparable) return BOOL;
        return fail(ctx, "operator '" + op + "' cannot compare " + l + " and " + r);
    }

    @Override public Type visitAnd(MiniTypeParser.AndContext ctx) { return logical("&&", ctx, ctx.expr(0), ctx.expr(1)); }
    @Override public Type visitOr(MiniTypeParser.OrContext ctx)   { return logical("||", ctx, ctx.expr(0), ctx.expr(1)); }

    private Type logical(String op, ParserRuleContext at, MiniTypeParser.ExprContext a, MiniTypeParser.ExprContext b) {
        Type l = visit(a), r = visit(b);
        if (l.isError() || r.isError()) return ERR;
        if (l.same(BOOL) && r.same(BOOL)) return BOOL;
        return fail(at, "operator '" + op + "' requires bool operands, got " + l + " and " + r);
    }

    @Override public Type visitIndex(MiniTypeParser.IndexContext ctx) {
        Type arr = visit(ctx.expr(0)), idx = visit(ctx.expr(1));
        if (arr.isError() || idx.isError()) return ERR;
        if (!(arr instanceof ArrayType)) return fail(ctx, "cannot index non-array type " + arr);
        if (!idx.same(INT)) return fail(ctx, "array index must be int, got " + idx);
        return ((ArrayType) arr).elem;
    }

    @Override public Type visitField(MiniTypeParser.FieldContext ctx) {
        Type rec = visit(ctx.expr());
        String field = ctx.ID().getText();
        if (rec.isError()) return ERR;
        if (!(rec instanceof RecordType)) return fail(ctx, "field access '." + field + "' on non-struct type " + rec);
        RecordType r = (RecordType) rec;
        Type ft = r.fields.get(field);
        if (ft == null) return fail(ctx, "struct " + r.name + " has no field '" + field + "'");
        return ft;
    }

    @Override public Type visitCall(MiniTypeParser.CallContext ctx) {
        // Evaluate argument types first (so errors inside arguments are reported).
        List<MiniTypeParser.ExprContext> argExprs =
                (ctx.argList() != null) ? ctx.argList().expr() : new ArrayList<>();
        List<Type> argTypes = new ArrayList<>();
        for (MiniTypeParser.ExprContext a : argExprs) argTypes.add(visit(a));

        Type callee = visit(ctx.expr());
        if (callee.isError()) return ERR;
        if (!(callee instanceof FunctionType)) return fail(ctx, "cannot call non-function type " + callee);

        FunctionType ft = (FunctionType) callee;
        if (argTypes.size() != ft.params.size())
            return fail(ctx, "function of type " + ft + " expects " + ft.params.size()
                    + " argument(s) but got " + argTypes.size());
        for (int i = 0; i < argTypes.size(); i++) {
            Type at = argTypes.get(i), pt = ft.params.get(i);
            if (!at.isError() && !pt.isError() && !pt.same(at))
                diag.error(argExprs.get(i).getStart(),
                        "argument " + (i + 1) + " expects " + pt + " but got " + at);
        }
        return ft.ret; // result type is the function's return type
    }

    // -----------------------------------------------------------------------
    //  small helpers
    // -----------------------------------------------------------------------

    private Type baseTypeOf(MiniTypeParser.BaseTypeContext ctx) {
        switch (ctx.getText()) {
            case "int":    return INT;
            case "double": return DOUBLE;
            case "char":   return CHAR;
            default:       return BOOL;
        }
    }

    /** Explicit casts are allowed among the scalar types int/double/char only. */
    private boolean allowConversion(Type from, Type to) { return isScalar(from) && isScalar(to); }
    private boolean isScalar(Type t) { return t.same(INT) || t.same(DOUBLE) || t.same(CHAR); }
}

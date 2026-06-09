# How MiniType Works — a step-by-step walkthrough

This is the **teaching companion** to `README.md`. The README is the reference
(rules, build commands, file map); *this* document walks you through **how the
type checker actually works**, one stage at a time, with real traces — so you
can understand it and defend it.

> Read it top to bottom once. By the end you'll be able to trace any program
> from text to "type-checks / has errors" in your head, and answer "why is it
> built this way?" for every part.

---

## 0. The one-sentence idea

> A **type checker** reads a program, figures out the **type** of every
> expression, and complains when an operation is used on the wrong type
> (e.g. adding an `int` to a `char`, or indexing something that isn't an array).

It does **not** run the program. It reasons about types only. That's why
`int A[10]; A[11]` is *not* a type error here — out-of-bounds is a runtime
thing; the types are all fine.

---

## 1. The pipeline (the big picture)

Your course's intro deck draws a compiler as a pipeline. We implement the first
three boxes; **type checking is the "semantic analysis" box**:

```
   source text                 tokens                  parse tree                 result
 ┌──────────────┐  LEXER   ┌───────────────┐  PARSER ┌──────────────┐ TYPE CHECK ┌───────────┐
 │ int a = 1+2; │ ───────▶ │ int · a · =   │ ──────▶ │   (tree of   │ ─────────▶ │ ✓ ok  OR  │
 │ ...          │  (split  │ · 1 · + · 2 · ;│ (build  │  AddSub,...) │   (SDD     │ ✗ errors  │
 └──────────────┘  chars)  └───────────────┘  a tree)└──────────────┘  over tree)└───────────┘
        │                         │                        │                          │
   MiniType.g4 (lexer)     MiniType.g4 (parser)      ANTLR parse tree         TypeChecker.java
```

- **Lexer** turns characters into **tokens** (`int`, `a`, `=`, `1`, `+`, `2`, `;`).
- **Parser** turns tokens into a **parse tree** (which `+` binds which numbers).
- **Type checker** walks that tree and assigns a **type** to every node, checking
  the rules as it goes.

The lexer and parser are **generated for us by ANTLR4** from one grammar file.
We only hand-write the type checker.

---

## 2. What we use, and why

| Tool | Why |
|------|-----|
| **ANTLR4** (parser generator) | Generates the lexer + parser + a **visitor** from one `.g4` grammar. We picked it because this machine has a JDK but no .NET (so the course's GPPG can't run); see README §2 for the full comparison. |
| **Java 21** | The language ANTLR generates here; already installed. |
| **JDK built-in HTTP server** | The optional web UI (`WebServer.java`) — zero extra dependencies. |

The **only** external file is the ANTLR jar (`lib/antlr-4.13.2-complete.jar`),
downloaded once by `build.sh`. Everything else is plain Java.

---

## 3. The key concept: a Syntax-Directed Definition (SDD)

This is the heart of the project and the technique your course teaches.

> An **SDD** = a grammar where **each production has a little rule** `{ … }` that
> computes an **attribute** of that node from its children's attributes.

Our one attribute is called **`.type`**. It is a **synthesized** attribute,
meaning it flows **bottom-up**: a node's type is computed *from its children's
types*. Example (straight from the course's *Type System* deck):

```
 production            semantic rule (the SDD)
 E → E1 + E2           E.type := (E1.type == E2.type && E1.type ∈ {int,double}) ? E1.type : type_error
 E → num              E.type := int
```

**The clever part — how we implement an SDD with ANTLR:**

ANTLR generates a **visitor** with **one method per grammar production**. So:

> **one `visitX` method  =  one SDD semantic rule.**
> The `Type` value the method **returns** *is* the synthesized `.type` attribute.

Here is the exact rule above, as real code in `TypeChecker.java`:

```java
@Override public Type visitAddSub(MiniTypeParser.AddSubContext ctx) {
    Type l = visit(ctx.expr(0));   // E1.type  (recurse into the left child)
    Type r = visit(ctx.expr(1));   // E2.type  (recurse into the right child)
    if (l.isError() || r.isError()) return ERR;            // don't cascade
    if (l.same(r) && l.isNumeric()) return l;              // both int, or both double  → that type
    return fail(ctx, "operator '+' requires matching int or double operands, got " + l + " and " + r);
}
```

Compare it line-for-line with the SDD rule. That correspondence is the whole
argument that "this is an SDD type checker."

---

## 4. Trace #1 — a program that type-checks

Take this program:

```c
int f() {
    int a = 2 + 3;
    return a;
}
```

### Stage 1 — Lexer → tokens

The lexer (rules at the bottom of `MiniType.g4`) splits the text into tokens
(whitespace/comments are skipped):

```
'int'  ID(f)  '('  ')'  '{'  'int'  ID(a)  '='  INT_LIT(2)  '+'  INT_LIT(3)  ';'  'return'  ID(a)  ';'  '}'
```

(This is exactly what the **Tokens** panel of the web UI shows.)

### Stage 2 — Parser → parse tree

The parser groups tokens by the grammar, encoding **precedence**. The meaningful
shape (dropping punctuation) is:

```
Program
└─ FuncDecl  (returns int, name f, no params)
   └─ Block
      ├─ DeclStmt:  int a = ⟨AddSub⟩
      │              └─ AddSub
      │                 ├─ IntLit 2
      │                 └─ IntLit 3
      └─ ReturnStmt:  return ⟨Ident a⟩
```

### Stage 3 — Type checker (SDD) walks the tree **bottom-up**

This is where `.type` gets synthesized. Watch the types bubble up:

| Node | Rule used | Result |
|------|-----------|--------|
| `IntLit 2` | `visitIntLit` | `int` |
| `IntLit 3` | `visitIntLit` | `int` |
| `2 + 3` | `visitAddSub` | both `int`, numeric ⇒ **`int`** |
| `int a = (2+3)` | `visitDeclStmt` | declared `int`, init `int`, equal ⇒ ok; **symbol table: `a ↦ int`** |
| `a` (in return) | `visitIdent` | `lookup(a)` ⇒ **`int`** |
| `return a;` | `visitReturnStmt` | expected `int` (f's return type), got `int` ⇒ ok |
| whole program | `visitProgram` | no errors collected ⇒ **✓ succeeds** |

Notice two things:
1. Types are computed **bottom-up** (leaves first), exactly like the course's
   `val = 25` parse-tree example, but with **types** instead of values.
2. The **symbol table** records `a ↦ int` at the declaration so the later use of
   `a` can be looked up.

---

## 5. Trace #2 — a program with a type error

```c
int f() {
    char c = 'x';
    int b = 1 + c;     // ← the mistake
    return b;
}
```

Walk it the same way:

| Node | What happens |
|------|--------------|
| `'x'` | `char`; symbol table: `c ↦ char` |
| `1` | `int` |
| `c` | `lookup(c)` ⇒ `char` |
| `1 + c` | `visitAddSub`: `int` vs `char` — **not the same type** ⇒ records the error **"operator '+' requires matching int or double operands, got int and char"** and returns **`type_error`** |
| `int b = (1+c)` | the initializer's type is `type_error`; the rule sees `init.isError()` and stays **silent** (no second, confusing message). `b ↦ int` |
| `return b;` | `b` is `int`, expected `int` ⇒ ok |

Result: **exactly one** error, at the right spot:

```
3:13: error: operator '+' requires matching int or double operands, got int and char
Type checking FAILED with 1 type error(s).
```

### The two ideas that make errors behave well

- **`type_error` sentinel.** Any failing rule returns the single `ErrorType`
  value. It *propagates upward* so a broken sub-expression makes the whole
  expression "broken" too.
- **No cascade.** Every rule first checks `if (operand.isError()) return ERR;`
  *before* comparing types. That's why one mistake produces **one** message, not
  a storm of follow-on errors. (This is the course's "emit a message and
  **continue**" requirement.)

---

## 6. The type system — types are little trees

A type is not always a single word. `Array`, `Pointer`, and functions are
**type constructors** that wrap other types, so a type is really a **tree**.
Example: the declaration `int* a[10];` has type:

```
        Array
          │
       Pointer        =  Array(Pointer(int))
          │
         int
```

The 8 type forms, and how we compare them (`Type.same(...)`):

| Form | Java class | Equality |
|------|-----------|----------|
| `int double char bool void` | `BaseType` (singletons) | identity (`==`) |
| `Array(T)` | `ArrayType` | **structural**: `Array(A)==Array(B)` iff `A==B` |
| `Pointer(T)` | `PointerType` | **structural** |
| `T1 -> T2` (function) | `FunctionType` | **structural**: same arity, params, return |
| `struct Name` | `RecordType` | **by-name**: same struct name |
| `type_error` | `ErrorType` | only equal to itself |

> **Clever bit — why records are by-name.** A `struct Node { Node* next; }`
> refers to *itself*. With **by-name** equality, the field type is just
> "Pointer to the struct called Node" — no infinite tree, no cycle detection
> needed. (Structural equality, the alternative the course mentions, would have
> to chase the cycle.)

---

## 7. The code, file by file

```
src/minitype/
  MiniType.g4       the grammar: lexer + parser. Each expr alternative has a #Label.
  Analyzer.java     SHARED pipeline: text → tokens, parse tree, syntax + type errors.
  TypeChecker.java  THE SDD: one visitX method per production (the type rules).
  SymbolTable.java  getType/addType with scopes (global / function / block) + struct table.
  Diagnostics.java  collects "line:col: error: ..." messages; keeps going after each.
  Main.java         command-line front end (prints the report; --tree).
  WebServer.java    web front end (serves the page + JSON endpoint).
  types/            Type + the 6 concrete kinds above.
web/index.html      the visualizer page (tokens, collapsible tree, clickable errors).
```

**Read them in this order to understand the flow:** `MiniType.g4` (what the
language looks like) → `types/` (what a type is) → `TypeChecker.java` (the rules)
→ `Analyzer.java` (glue) → `Main.java` / `WebServer.java` (the two front ends).

> **Clever bit — one checker, two front ends.** `Main` (CLI) and `WebServer`
> (UI) both call `Analyzer.analyze(source)`. The type-checking logic exists in
> **exactly one place** (`TypeChecker`). Fix a rule once → both front ends update.
> That's why adding the GUI did **not** make the project harder to maintain.

---

## 8. Two passes — why the checker reads the program twice

Look at `visitProgram` in `TypeChecker.java`. It loops over the top-level
declarations **several times**:

1. **Pass 1a** — register every **struct name** (empty).
2. **Pass 1b** — fill in each struct's **fields** (now any field can refer to any
   struct, including itself → recursive structs work).
3. **Pass 1c** — register every **function signature** and **global variable**.
4. **Pass 2** — type-check the **function bodies** and global initializers.

> **Why?** So a name can be used **before** the line it's declared on:
> recursion (`factorial` calls `factorial`), one function calling another defined
> later, and recursive structs. If we checked in a single top-to-bottom pass,
> `factorial`'s body wouldn't yet know `factorial` exists.

---

## 9. The clever design decisions (defense gold)

These are the "why is it done this way?" answers a reviewer loves.

1. **Precedence by alternative order.** In the `expr` rule, the alternatives are
   listed **highest precedence first** (`Call`, `Index`, … then `MulDiv`, then
   `AddSub`, …). ANTLR4 turns that order into precedence automatically, so
   `-a*b` parses as `(-a)*b` and `2+3*4` as `2+(3*4)` with no extra work.

2. **Casts target base types only** (`(int)e`, not `(MyStruct)e`). That single
   restriction makes `(int)x` (a cast) unambiguous from `(x)` (parentheses) —
   the parser can tell them apart by one token of lookahead.

3. **`type_error` + `isError()` guards** give clean, single-message errors
   (Section 5).

4. **`VOID` is checked with `==`, before array-wrapping.** The void check uses
   reference identity on the singleton `BaseType.VOID` and runs on the *element*
   type, so both `void v;` and `void v[3];` are rejected, while `void* p;`
   (a different type) is allowed.

5. **Functions aren't lvalues.** `g = g;` and `&g` are rejected ("cannot
   assign to / take the address of a function") — a function isn't a storage
   location.

6. **Graceful on pathological input.** A 6000-deep expression is reported as
   "input is too deeply nested" instead of dumping a Java stack trace.

---

## 10. How to EXTEND it (shows you really understand it)

A reviewer may ask "how would you add X?". Here are the recipes — each is small
*because* of the SDD design.

### Add a new operator, e.g. bitwise `^`
1. **Grammar** (`MiniType.g4`): add an alternative in `expr`, e.g.
   `| expr op='^' expr  # Xor` at the right precedence position.
2. **Checker** (`TypeChecker.java`): add `visitXor` returning `int` when both
   operands are `int`, else `fail(...)`.
3. Rebuild. Done — the visitor method *is* the type rule.

### Add a new base type, e.g. `string`
1. **`BaseType.java`**: add `public static final BaseType STRING = new BaseType("string");`
2. **Grammar**: add `string` to `typeName` (and `baseType` if it should be castable).
3. **`TypeChecker.resolveType`**: add a `case "string": t = STRING;`.
4. Add a string literal token + a `visitStringLit` rule if you want literals.

### Add a new statement, e.g. `for`
1. **Grammar**: add a labeled `# ForStmt` alternative under `stmt`.
2. **Checker**: add `visitForStmt` — check the condition is `bool`, visit the body.

The pattern is always the same: **grammar alternative ↔ visitor method ↔ type
rule.** That symmetry is the payoff of the SDD approach.

---

## 11. Defense Q&A (likely questions)

**Q: What is a Syntax-Directed Definition and where is it in your code?**
A: A grammar whose productions each carry a semantic rule computing an
attribute. Here the attribute is `.type`, and each rule is a `visitX` method in
`TypeChecker.java` that returns the node's `Type`. (Show `visitAddSub`.)

**Q: Why ANTLR4 and not the GPPG from the course?**
A: Same selection criteria the course used (match the platform): GPPG targets C#
and needs .NET, which isn't installed here; ANTLR targets the JVM, which is. Its
visitor is the cleanest realization of an SDD. ANTLR is LL/ALL(\*) (top-down)
where the course highlighted LALR — I note that honestly in README §2.

**Q: How do you detect, say, `int + char`?**
A: `visitAddSub` synthesizes both operand types, requires `same` type and
numeric; `int` ≠ `char` ⇒ it records an error and returns `type_error`.

**Q: Why doesn't one error cause ten more?**
A: `type_error` propagates, and every rule checks `isError()` on its operands
before comparing, so it stays silent on already-broken sub-expressions.

**Q: How are the 6+ types represented?**
A: A `Type` class hierarchy: base types as singletons; `Array`, `Pointer`,
function, and record as constructors that hold child types — so a type is a
small tree. Equality is structural (by-name for records).

**Q: Why check the program in two passes?**
A: So declarations can be used before they appear textually (recursion, forward
references, recursive structs).

**Q: What can't it catch?**
A: Runtime/value properties — out-of-bounds indexing, division by zero, null
dereference. It checks **types**, not **values**. (And it's monomorphic — no
type inference; the course's Hindley-Milner decks are the advanced alternative.)

---

## 12. Glossary

- **Token** — a smallest meaningful chunk of source (`int`, `+`, an identifier).
- **Parse tree** — the tree the parser builds from tokens; here it doubles as the AST.
- **SDD (Syntax-Directed Definition)** — grammar + per-production semantic rules.
- **Synthesized attribute** — a value computed from a node's children (bottom-up); ours is `.type`.
- **Symbol table** — the map from names to their declared types (`getType`/`addType`).
- **`type_error`** — the sentinel type produced by a failing rule; propagates upward.
- **Structural vs by-name equality** — same shape vs same declared name.
- **lvalue** — something you can assign to / take the address of (a variable, array element, field, `*p`).
- **Visitor** — ANTLR's generated tree-walker; we override one method per production.

---

*See `README.md` for the full type-rule table, build/run/test commands, and the
parser-generator comparison.*

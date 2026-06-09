# How MiniType Works — a step-by-step walkthrough

This is the **teaching companion** to `README.md`. The README is the reference
(rules, build commands, file map); *this* document walks you through **how the
type checker actually works**, one stage at a time, with real traces — so you
can understand it and defend it.

---

## 1. The pipeline

We implement the first three boxes; **type checking is the "semantic analysis" box**:

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
| **ANTLR4** (parser generator) | Generates the lexer + parser + a **visitor** from one `.g4` grammar. We picked it because this machine has a JDK.
| **Java 21** | The language ANTLR generates here; already installed. |
| **JDK built-in HTTP server** | The optional web UI (`WebServer.java`) — zero extra dependencies. |

The **only** external file is the ANTLR jar (`lib/antlr-4.13.2-complete.jar`),
downloaded once by `build.sh`. Everything else is plain Java.

---

## 3. The key concept: a Syntax-Directed Definition (SDD)


> An **SDD** = a grammar where **each production has a little rule** `{ … }` that
> computes an **attribute** of that node from its children's attributes.

Our one attribute is called **`.type`**. It is a **synthesized** attribute,
meaning it flows **bottom-up**: a node's type is computed *from its children's
types*.

```
 production            semantic rule (the SDD)
 E → E1 + E2           E.type := (E1.type == E2.type && E1.type ∈ {int,double}) ? E1.type : type_error
 E → num               E.type := int
```

**The clever part — how we implement an SDD with ANTLR:**

ANTLR generates a **visitor** with **one method per grammar production**. So:

> **one `visitX` method  =  one SDD semantic rule.**
> The `Type` value the method **returns** *is* the synthesized `.type` attribute.

Here is the exact rule above, as real code in `TypeChecker.java`:

```java
@Override
public Type visitAddSub(MiniTypeParser.AddSubContext ctx) {
    Type l = visit(ctx.expr(0));   // E1.type  (recurse into the left child)
    Type r = visit(ctx.expr(1));   // E2.type  (recurse into the right child)
    if (l.isError() || r.isError()) {
         return ERR;               // don't cascade
    }
    if (l.same(r) && l.isNumeric()) {
         return l;                 // both int, or both double  → that type
    }
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
1. Types are computed **bottom-up** (leaves first),
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
  a storm of follow-on errors.

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

*See `README.md` for the full type-rule table, build/run/test commands, and the
parser-generator comparison.*

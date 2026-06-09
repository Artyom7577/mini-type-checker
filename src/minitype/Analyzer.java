package minitype;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Analyzer — the shared front end that turns MiniType source TEXT into its
 * tokens, parse tree, syntax errors and type errors.
 *
 * <p>Both front ends use this one class, so the type-checking logic lives in
 * exactly one place: the command-line driver {@link Main} and the web server
 * {@link WebServer} call {@code analyze(...)} and simply present the result
 * differently. Fixing a type rule (in {@link TypeChecker}) updates both.
 */
public final class Analyzer {

    private Analyzer() {
    }

    /** One lexer token, for display in the UI. */
    public static final class TokenInfo {
        public final String type; // symbolic name (ID, INT_LIT) or literal ('+', 'int')
        public final String text;
        public final int line;
        public final int col;

        TokenInfo(String type, String text, int line, int col) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.col = col;
        }
    }

    /** The full result of analysing one program. */
    public static final class Result {
        public final List<String> syntaxErrors;
        public final List<String> typeErrors;
        public final List<TokenInfo> tokens;
        public final ParseTree tree;   // null if parsing failed catastrophically
        public final Parser parser;

        Result(List<String> syntaxErrors, List<String> typeErrors, List<TokenInfo> tokens,
               ParseTree tree, Parser parser) {
            this.syntaxErrors = syntaxErrors;
            this.typeErrors = typeErrors;
            this.tokens = tokens;
            this.tree = tree;
            this.parser = parser;
        }

        public boolean ok() {
            return syntaxErrors.isEmpty() && typeErrors.isEmpty();
        }
    }

    /** Run the whole pipeline (lexer -> parser -> SDD type checker) over the source. */
    public static Result analyze(String source) {
        // --- tokens: a separate, silent lexer so we can list them for the UI ---
        List<TokenInfo> tokenInfos = new ArrayList<>();
        MiniTypeLexer tokenLexer = new MiniTypeLexer(CharStreams.fromString(source));
        tokenLexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(tokenLexer);
        tokenStream.fill();
        for (Token t : tokenStream.getTokens()) {
            if (t.getType() == Token.EOF) {
                continue;
            }
            String name = MiniTypeLexer.VOCABULARY.getSymbolicName(t.getType());
            if (name == null) {
                name = MiniTypeLexer.VOCABULARY.getDisplayName(t.getType()); // e.g. "'+'"
            }
            tokenInfos.add(new TokenInfo(name, t.getText(), t.getLine(), t.getCharPositionInLine() + 1));
        }

        // --- parse + type-check (a fresh lexer/parser with an error collector) ---
        MiniTypeLexer lexer = new MiniTypeLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MiniTypeParser parser = new MiniTypeParser(tokens);

        List<String> syntaxErrors = new ArrayList<>();
        Collector collector = new Collector(syntaxErrors);
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);
        parser.removeErrorListeners();
        parser.addErrorListener(collector);

        ParseTree tree;
        try {
            tree = parser.program();
        } catch (StackOverflowError e) {
            syntaxErrors.add("error: input is too deeply nested to parse");
            return new Result(syntaxErrors, new ArrayList<>(), tokenInfos, null, parser);
        }

        List<String> typeErrors = new ArrayList<>();
        if (syntaxErrors.isEmpty()) {
            Diagnostics diag = new Diagnostics();
            try {
                new TypeChecker(diag).visit(tree);
                typeErrors = diag.all();
            } catch (StackOverflowError e) {
                typeErrors.add("error: input is too deeply nested to type-check");
            }
        }
        return new Result(syntaxErrors, typeErrors, tokenInfos, tree, parser);
    }

    /** Collects ANTLR syntax errors into a list (1-based column), same format as the CLI. */
    static final class Collector extends BaseErrorListener {
        private final List<String> sink;

        Collector(List<String> sink) {
            this.sink = sink;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            sink.add(line + ":" + (charPositionInLine + 1) + ": syntax error: " + msg);
        }
    }
}

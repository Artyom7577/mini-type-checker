package minitype;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Main — the command-line driver. It runs the full course pipeline on a .mt file
 * via the shared {@link Analyzer} (the SAME pipeline the web {@link WebServer}
 * uses), then prints a textual report.
 *
 * <p>Usage:  java minitype.Main &lt;file.mt&gt; [--tree]
 * <br>--tree also prints the parse tree (handy for demos / the AST topic).
 *
 * <p>Exit code: 0 if type checking succeeds, 1 on syntax or type errors, 2 on usage error.
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: java minitype.Main <file.mt> [--tree]");
            System.exit(2);
        }
        String path = args[0];
        boolean showTree = args.length > 1 && args[1].equals("--tree");

        String source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Analyzer.Result result = Analyzer.analyze(source);

        if (showTree && result.tree != null) {
            System.out.println("--- parse tree ---");
            printTree(result.tree, "");
            System.out.println("------------------");
        }

        if (!result.syntaxErrors.isEmpty()) {
            for (String e : result.syntaxErrors) {
                System.err.println(e);
            }
            System.err.println("Parsing FAILED with " + result.syntaxErrors.size()
                + " syntax error(s); skipping type check.");
            System.exit(1);
        }

        System.out.println("=== Type checking: " + path + " ===");
        if (!result.typeErrors.isEmpty()) {
            for (String e : result.typeErrors) {
                System.out.println(e);
            }
            System.out.println("Type checking FAILED with " + result.typeErrors.size() + " type error(s).");
            System.exit(1);
        }
        System.out.println("Type checking SUCCEEDED. No type errors.");
        System.exit(0);
    }

    /** Pretty-print the parse tree: rule/label names for inner nodes, quoted text for leaves. */
    private static void printTree(ParseTree t, String indent) {
        String label = (t instanceof TerminalNode)
            ? "'" + t.getText() + "'"
            : t.getClass().getSimpleName().replace("Context", ""); // shows the # label, e.g. AddSub
        System.out.println(indent + label);
        for (int i = 0; i < t.getChildCount(); i++) {
            printTree(t.getChild(i), indent + "  ");
        }
    }
}

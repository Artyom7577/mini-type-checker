package minitype;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * WebServer — a tiny visualiser front end for the SAME type checker the CLI uses.
 *
 * <p>It uses only the JDK's built-in HTTP server (no extra dependencies). It
 * serves one static page (web/index.html) and a JSON endpoint POST /api/check
 * that runs {@link Analyzer#analyze} and returns the tokens, parse tree, and
 * type errors so the page can visualise them.
 *
 * <p>Run with:  ./serve.sh        (then open http://localhost:8000)
 */
public final class WebServer {

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8000;
        Path pagePath = Path.of("web", "index.html");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // The type-checking endpoint: POST the source text, get JSON back.
        server.createContext("/api/check", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain", "use POST");
                return;
            }
            String source = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String json = toJson(Analyzer.analyze(source));
            send(exchange, 200, "application/json; charset=utf-8", json);
        });

        // Everything else serves the single HTML page.
        server.createContext("/", exchange -> {
            if (!Files.exists(pagePath)) {
                send(exchange, 500, "text/plain", "web/index.html not found (run serve.sh from the project root)");
                return;
            }
            String html = Files.readString(pagePath, StandardCharsets.UTF_8);
            send(exchange, 200, "text/html; charset=utf-8", html);
        });

        server.setExecutor(null);
        server.start();
        System.out.println("MiniType visualizer running at  http://localhost:" + port);
        System.out.println("(press Ctrl+C to stop)");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // =======================================================================
    //  JSON serialisation (hand-rolled, so there are no extra dependencies)
    // =======================================================================

    private static String toJson(Analyzer.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"ok\":").append(r.ok()).append(',');
        sb.append("\"syntaxErrors\":");
        appendStringArray(sb, r.syntaxErrors);
        sb.append(',');
        sb.append("\"typeErrors\":");
        appendStringArray(sb, r.typeErrors);
        sb.append(',');
        sb.append("\"tokens\":");
        appendTokens(sb, r.tokens);
        sb.append(',');
        sb.append("\"tree\":");
        if (r.tree == null) {
            sb.append("null");
        } else {
            appendTree(sb, r.tree);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendStringArray(StringBuilder sb, List<String> items) {
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendQuoted(sb, items.get(i));
        }
        sb.append(']');
    }

    private static void appendTokens(StringBuilder sb, List<Analyzer.TokenInfo> tokens) {
        sb.append('[');
        for (int i = 0; i < tokens.size(); i++) {
            Analyzer.TokenInfo t = tokens.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"type\":");
            appendQuoted(sb, t.type);
            sb.append(",\"text\":");
            appendQuoted(sb, t.text);
            sb.append(",\"line\":").append(t.line);
            sb.append(",\"col\":").append(t.col);
            sb.append('}');
        }
        sb.append(']');
    }

    /** Serialise a parse-tree node: {label, kind: rule|token, children:[...]}. */
    private static void appendTree(StringBuilder sb, ParseTree node) {
        boolean terminal = node instanceof TerminalNode;
        String label = terminal
            ? node.getText()
            : node.getClass().getSimpleName().replace("Context", ""); // the # label, e.g. AddSub
        sb.append("{\"label\":");
        appendQuoted(sb, label);
        sb.append(",\"kind\":").append(terminal ? "\"token\"" : "\"rule\"");
        sb.append(",\"children\":[");
        for (int i = 0; i < node.getChildCount(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendTree(sb, node.getChild(i));
        }
        sb.append("]}");
        return;
    }

    /** Append a JSON string literal with the necessary escaping. */
    private static void appendQuoted(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}

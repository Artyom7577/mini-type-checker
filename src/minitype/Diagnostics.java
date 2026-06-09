package minitype;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostics — collects type-checking errors with their source position, then
 * reports them at the end.  This realises the course requirement (Type-System
 * deck, slide "Սխալի հայտնաբերում"): on a type error the checker must
 * (a) emit a message about the error and (b) CONTINUE working — i.e. it does
 * error recovery rather than aborting on the first mistake.
 */
public final class Diagnostics {

    private final List<String> errors = new ArrayList<>();

    /** Record an error located at the given token (line:column reported 1-based). */
    public void error(Token at, String message) {
        int line = (at == null) ? 0 : at.getLine();
        int col  = (at == null) ? 0 : at.getCharPositionInLine() + 1; // ANTLR columns are 0-based
        errors.add(line + ":" + col + ": error: " + message);
    }

    public boolean hasErrors()  { return !errors.isEmpty(); }
    public int count()          { return errors.size(); }
    public List<String> all()   { return errors; }
}

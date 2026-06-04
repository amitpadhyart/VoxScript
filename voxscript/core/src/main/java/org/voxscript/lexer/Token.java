package org.voxscript.lexer;

/**
 * An immutable token produced by the Lexer.
 *
 * @param type    what kind of token this is
 * @param value   the raw source text for this token
 * @param line    1-based line number in the source file
 * @param column  1-based column number in the source file
 */
public record Token(TokenType type, String value, int line, int column) {

    /** Convenience: is this token a COMMAND with the given name? */
    public boolean isCommand(String name) {
        return type == TokenType.COMMAND && value.equals(name);
    }

    /** Convenience: is this token one of the given types? */
    public boolean is(TokenType... types) {
        for (TokenType t : types) {
            if (this.type == t) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Token[%s %s L%d:C%d]".formatted(type, value, line, column);
    }
}

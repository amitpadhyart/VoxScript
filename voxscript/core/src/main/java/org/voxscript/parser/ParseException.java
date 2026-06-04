package org.voxscript.parser;

import org.voxscript.lexer.Token;

/**
 * Thrown when the parser encounters source it cannot understand.
 *
 * Always includes a human-readable message with line and column.
 * The goal: errors you can actually understand, unlike LaTeX.
 */
public final class ParseException extends RuntimeException {

    private final int line;
    private final int column;

    public ParseException(String message, int line, int column) {
        super("[Line %d, Col %d] %s".formatted(line, column, message));
        this.line   = line;
        this.column = column;
    }

    public ParseException(String message, Token token) {
        this(message, token.line(), token.column());
    }

    public int getLine()   { return line; }
    public int getColumn() { return column; }
}

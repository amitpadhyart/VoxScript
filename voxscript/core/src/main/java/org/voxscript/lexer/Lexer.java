package org.voxscript.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * VoxScript Lexer.
 *
 * Converts raw VoxScript source text into a flat list of {@link Token}s.
 * The parser then consumes those tokens to build the AST.
 *
 * <h2>Key rules</h2>
 * <ul>
 *   <li>{@code \} starts a command name (letters only).</li>
 *   <li>{@code \\} is an escaped backslash — produces a TEXT token containing {@code \}.</li>
 *   <li>{@code [}, {@code ]}, {@code {}, {@code }}, {@code (}, {@code )} are structural.</li>
 *   <li>A blank line (two consecutive newlines) is a paragraph boundary.</li>
 *   <li>Everything else is TEXT.</li>
 * </ul>
 */
public final class Lexer {

    private final String source;
    private int pos;
    private int line;
    private int column;

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tokenize the entire source and return the token list.
     * The last token is always {@link TokenType#EOF}.
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (!atEnd()) {
            skipInlineWhitespace();
            if (atEnd()) break;

            int tokLine = line;
            int tokCol  = column;
            char c = peek();

            if (c == '\n') {
                tokens.add(readNewline(tokLine, tokCol));
            } else if (c == '\\') {
                tokens.add(readBackslash(tokLine, tokCol));
            } else if (c == '[') {
                advance(); tokens.add(token(TokenType.LBRACKET, "[", tokLine, tokCol));
            } else if (c == ']') {
                advance(); tokens.add(token(TokenType.RBRACKET, "]", tokLine, tokCol));
            } else if (c == '{') {
                advance(); tokens.add(token(TokenType.LBRACE, "{", tokLine, tokCol));
            } else if (c == '}') {
                advance(); tokens.add(token(TokenType.RBRACE, "}", tokLine, tokCol));
            } else if (c == '(') {
                advance(); tokens.add(token(TokenType.LPAREN, "(", tokLine, tokCol));
            } else if (c == ')') {
                advance(); tokens.add(token(TokenType.RPAREN, ")", tokLine, tokCol));
            } else if (c == ',') {
                advance(); tokens.add(token(TokenType.COMMA, ",", tokLine, tokCol));
            } else if (c == '=') {
                advance(); tokens.add(token(TokenType.EQUALS, "=", tokLine, tokCol));
            } else if (c == '&') {
                advance(); tokens.add(token(TokenType.AMPERSAND, "&", tokLine, tokCol));
            } else if (c == '"') {
                tokens.add(readString(tokLine, tokCol));
            } else if (Character.isDigit(c)) {
                tokens.add(readNumber(tokLine, tokCol));
            } else {
                tokens.add(readText(tokLine, tokCol));
            }
        }

        tokens.add(token(TokenType.EOF, "", line, column));
        return tokens;
    }

    // ── Readers ───────────────────────────────────────────────────────────────

    private Token readBackslash(int tokLine, int tokCol) {
        advance(); // consume '\'

        if (atEnd()) {
            return token(TokenType.TEXT, "\\", tokLine, tokCol);
        }

        char next = peek();

        // \\ — escaped backslash
        if (next == '\\') {
            advance();
            return token(TokenType.TEXT, "\\", tokLine, tokCol);
        }

        // \commandName — letters only
        if (Character.isLetter(next) || Character.isDigit(next)) {
            StringBuilder name = new StringBuilder();
            while (!atEnd() && (Character.isLetter(peek()) || Character.isDigit(peek()))) {
                name.append(advance());
            }
            return token(TokenType.COMMAND, name.toString(), tokLine, tokCol);
        }

        // Anything else after \ is a TEXT token containing the literal backslash
        return token(TokenType.TEXT, "\\", tokLine, tokCol);
    }

    private Token readNewline(int tokLine, int tokCol) {
        advance(); // consume first '\n'

        // Check for blank line (paragraph boundary)
        int savedPos    = pos;
        int savedLine   = line;
        int savedColumn = column;

        // Skip spaces/tabs on the next line
        while (!atEnd() && (peek() == ' ' || peek() == '\t')) advance();

        if (!atEnd() && peek() == '\n') {
            advance(); // consume second '\n'
            return token(TokenType.BLANK_LINE, "\n\n", tokLine, tokCol);
        }

        // Not a blank line — restore and emit single NEWLINE
        pos    = savedPos;
        line   = savedLine;
        column = savedColumn;
        return token(TokenType.NEWLINE, "\n", tokLine, tokCol);
    }

    private Token readString(int tokLine, int tokCol) {
        advance(); // consume opening "
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && peek() != '"') {
            char c = advance();
            if (c == '\\' && !atEnd() && peek() == '"') {
                sb.append(advance()); // escaped quote
            } else {
                sb.append(c);
            }
        }
        if (!atEnd()) advance(); // consume closing "
        return token(TokenType.STRING, sb.toString(), tokLine, tokCol);
    }

    private Token readNumber(int tokLine, int tokCol) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && Character.isDigit(peek())) {
            sb.append(advance());
        }
        return token(TokenType.NUMBER, sb.toString(), tokLine, tokCol);
    }

    private Token readText(int tokLine, int tokCol) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd()) {
            char c = peek();
            // Stop at any structural character or backslash
            if (c == '\\' || c == '[' || c == ']' || c == '{' || c == '}'
                    || c == '(' || c == ')' || c == ',' || c == '='
                    || c == '&' || c == '"' || c == '\n') {
                break;
            }
            sb.append(advance());
        }
        // Classify as IDENT if purely alphanumeric (used in option keys/values)
        String val = sb.toString();
        boolean isIdent = !val.isEmpty() && val.chars().allMatch(ch ->
                Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.');
        return token(isIdent ? TokenType.IDENT : TokenType.TEXT, val, tokLine, tokCol);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void skipInlineWhitespace() {
        while (!atEnd() && (peek() == ' ' || peek() == '\t')) advance();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') { line++; column = 1; }
        else           { column++; }
        return c;
    }

    private boolean atEnd() {
        return pos >= source.length();
    }

    private static Token token(TokenType type, String value, int line, int col) {
        return new Token(type, value, line, col);
    }
}

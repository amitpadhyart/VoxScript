package org.voxscript.lexer;

/**
 * Every token type the VoxScript lexer can produce.
 *
 * Grouped by category for readability.
 * The parser consumes these in sequence to build the AST.
 */
public enum TokenType {

    // ── Structural ────────────────────────────────────────────────────────────
    /** A \commandName token */
    COMMAND,
    /** [ — start of options block */
    LBRACKET,
    /** ] — end of options block */
    RBRACKET,
    /** { — start of block content */
    LBRACE,
    /** } — end of block content */
    RBRACE,
    /** ( — start of inline content */
    LPAREN,
    /** ) — end of inline content */
    RPAREN,
    /** , — separator inside [...] or {...} */
    COMMA,
    /** = — key=value inside [...] */
    EQUALS,
    /** & — alignment separator inside math */
    AMPERSAND,

    // ── Content ───────────────────────────────────────────────────────────────
    /** Plain text — any run of characters that isn't a special token */
    TEXT,
    /** A key or identifier inside options: python, align, r, c, intro … */
    IDENT,
    /** A quoted string value inside options: "My Title" */
    STRING,
    /** An integer literal inside options: 2, 12 */
    NUMBER,

    // ── Whitespace / layout ───────────────────────────────────────────────────
    /** A blank line — paragraph boundary */
    BLANK_LINE,
    /** A single newline (within a paragraph) */
    NEWLINE,

    // ── Sentinels ─────────────────────────────────────────────────────────────
    /** End of input */
    EOF
}

package org.voxscript.lexer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private List<Token> lex(String source) {
        return new Lexer(source).tokenize();
    }

    @Test
    void simpleCommand() {
        var tokens = lex("\\h1{Hello}");
        assertEquals(TokenType.COMMAND, tokens.get(0).type());
        assertEquals("h1",             tokens.get(0).value());
        assertEquals(TokenType.LBRACE, tokens.get(1).type());
        assertEquals(TokenType.IDENT,  tokens.get(2).type());
        assertEquals("Hello",          tokens.get(2).value());
        assertEquals(TokenType.RBRACE, tokens.get(3).type());
        assertEquals(TokenType.EOF,    tokens.get(4).type());
    }

    @Test
    void escapedBackslash() {
        var tokens = lex("\\\\");
        assertEquals(TokenType.TEXT, tokens.get(0).type());
        assertEquals("\\",           tokens.get(0).value());
    }

    @Test
    void blankLineParagraphBoundary() {
        var tokens = lex("hello\n\nworld");
        boolean hasBlankLine = tokens.stream()
                .anyMatch(t -> t.type() == TokenType.BLANK_LINE);
        assertTrue(hasBlankLine, "Blank line between paragraphs should produce BLANK_LINE token");
    }

    @Test
    void optionsBlock() {
        var tokens = lex("\\code[python]{}");
        assertEquals(TokenType.COMMAND,  tokens.get(0).type());
        assertEquals("code",             tokens.get(0).value());
        assertEquals(TokenType.LBRACKET, tokens.get(1).type());
        assertEquals(TokenType.IDENT,    tokens.get(2).type());
        assertEquals("python",           tokens.get(2).value());
        assertEquals(TokenType.RBRACKET, tokens.get(3).type());
    }

    @Test
    void inlineParens() {
        var tokens = lex("\\formula(E=mc^2)");
        assertEquals(TokenType.COMMAND, tokens.get(0).type());
        assertEquals(TokenType.LPAREN,  tokens.get(1).type());
        assertEquals(TokenType.RPAREN,  tokens.get(tokens.size() - 2).type());
    }

    @Test
    void quotedString() {
        var tokens = lex("\\meta[title=\"My Doc\"]");
        boolean hasString = tokens.stream()
                .anyMatch(t -> t.type() == TokenType.STRING && t.value().equals("My Doc"));
        assertTrue(hasString);
    }

    @Test
    void lineAndColumnTracking() {
        var tokens = lex("\\h1{A}\n\\h2{B}");
        Token h2 = tokens.stream()
                .filter(t -> t.type() == TokenType.COMMAND && t.value().equals("h2"))
                .findFirst().orElseThrow();
        assertEquals(2, h2.line());
        assertEquals(1, h2.column());
    }

    @Test
    void emptySource() {
        var tokens = lex("");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type());
    }

    @Test
    void numberToken() {
        var tokens = lex("\\table[r=3,c=2]{}");
        boolean has3 = tokens.stream()
                .anyMatch(t -> t.type() == TokenType.NUMBER && t.value().equals("3"));
        assertTrue(has3);
    }
}

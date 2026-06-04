package org.voxscript.parser;

import org.junit.jupiter.api.Test;
import org.voxscript.ast.Node;
import org.voxscript.ast.Node.*;
import org.voxscript.lexer.Lexer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Node.Document parse(String source) {
        var tokens = new Lexer(source).tokenize();
        return new Parser(tokens).parse();
    }

    @Test
    void headingLevel1() {
        var doc = parse("\\h1{Introduction}");
        assertEquals(1, doc.children().size());
        assertInstanceOf(Heading.class, doc.children().get(0));
        Heading h = (Heading) doc.children().get(0);
        assertEquals(1, h.level());
        assertTrue(h.numbered());
    }

    @Test
    void unnumberedHeading() {
        var doc = parse("\\numnone\\h2{Preface}");
        Heading h = (Heading) doc.children().get(0);
        assertFalse(h.numbered());
        assertEquals(2, h.level());
    }

    @Test
    void paragraphFromText() {
        var doc = parse("Hello world");
        assertEquals(1, doc.children().size());
        assertInstanceOf(Paragraph.class, doc.children().get(0));
    }

    @Test
    void twoParagraphsFromBlankLine() {
        var doc = parse("First paragraph.\n\nSecond paragraph.");
        long paraCount = doc.children().stream()
                .filter(n -> n instanceof Paragraph).count();
        assertEquals(2, paraCount);
    }

    @Test
    void boldInline() {
        var doc = parse("\\b{important}");
        var para = (Paragraph) doc.children().get(0);
        assertInstanceOf(Bold.class, para.children().get(0));
    }

    @Test
    void unorderedList() {
        var doc = parse("""
                \\ul {
                    \\li{First}
                    \\li{Second}
                }
                """);
        assertInstanceOf(UnorderedList.class, doc.children().get(0));
        UnorderedList ul = (UnorderedList) doc.children().get(0);
        assertEquals(2, ul.items().size());
    }

    @Test
    void codeBlock() {
        var doc = parse("\\code[python]{print('hello')}");
        assertInstanceOf(CodeBlock.class, doc.children().get(0));
        CodeBlock cb = (CodeBlock) doc.children().get(0);
        assertEquals("python", cb.language());
        assertTrue(cb.content().contains("print"));
    }

    @Test
    void inlineFormula() {
        var doc = parse("Energy is \\formula(E=mc^2) always.");
        var para = (Paragraph) doc.children().get(0);
        boolean hasFormula = para.children().stream()
                .anyMatch(n -> n instanceof Formula f && f.mode() == Formula.FormulaMode.INLINE);
        assertTrue(hasFormula);
    }

    @Test
    void displayFormula() {
        var doc = parse("\\formula{x = {-b} / {2a}}");
        assertInstanceOf(Formula.class, doc.children().get(0));
        Formula f = (Formula) doc.children().get(0);
        assertEquals(Formula.FormulaMode.DISPLAY, f.mode());
    }

    @Test
    void matrixNode() {
        var doc = parse("\\matrix[r=2,c=2]{[a,b],[c,d]}");
        assertInstanceOf(Matrix.class, doc.children().get(0));
        Matrix mx = (Matrix) doc.children().get(0);
        assertEquals(2, mx.rows());
        assertEquals(2, mx.cols());
        assertEquals("a", mx.cells().get(0).get(0));
    }

    @Test
    void linkNode() {
        var doc = parse("\\link[https://example.com]{Click here}");
        var para = (Paragraph) doc.children().get(0);
        assertInstanceOf(Link.class, para.children().get(0));
        Link lk = (Link) para.children().get(0);
        assertEquals("https://example.com", lk.url());
    }

    @Test
    void metaAtTop() {
        var doc = parse("\\meta[title=\"My Doc\", author=\"Jane\"]\n\\h1{Start}");
        assertNotNull(doc.meta());
        assertEquals("My Doc", doc.meta().attributes().get("title"));
        assertEquals("Jane",   doc.meta().attributes().get("author"));
    }

    @Test
    void tableOfContents() {
        var doc = parse("\\tableofcontent");
        assertInstanceOf(TableOfContents.class, doc.children().get(0));
    }

    @Test
    void labelAndReference() {
        var doc = parse("\\label[intro]\\h1{Introduction}\n\nSee \\ref[intro].");
        boolean hasLabel = doc.children().stream().anyMatch(n -> n instanceof Label);
        assertTrue(hasLabel);
    }

    @Test
    void parseErrorHasPosition() {
        var ex = assertThrows(ParseException.class, () ->
                parse("\\numnone plain text"));  // \numnone not followed by heading
        assertTrue(ex.getMessage().contains("Line"));
    }
}

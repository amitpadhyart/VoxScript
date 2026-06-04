package org.voxscript.parser;

import org.voxscript.ast.Node;
import org.voxscript.ast.Node.*;
import org.voxscript.lexer.Token;
import org.voxscript.lexer.TokenType;

import java.util.*;

/**
 * VoxScript Parser.
 *
 * Consumes a flat {@link Token} list produced by the {@link org.voxscript.lexer.Lexer}
 * and builds a {@link Document} AST.
 *
 * <h2>Grammar summary (informal)</h2>
 * <pre>
 * document     ::= meta? block*
 * block        ::= heading | list | table | codeblock | formula
 *                | image | toc | pagebreak | paragraph | blankline
 * inline        ::= text | bold | italic | underline | inlinecode
 *                | link | ref | label | footnote | formula | linebreak
 * command      ::= '\' IDENT options? (block_body | inline_body)?
 * options      ::= '[' option (',' option)* ']'
 * option       ::= IDENT ('=' (STRING | NUMBER | IDENT))?
 * block_body   ::= '{' block* '}'
 * inline_body  ::= '{' inline* '}' | '(' inline* ')'
 * </pre>
 */
public final class Parser {

    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Document parse() {
        Meta meta = null;

        // Optional \meta at top of document
        if (check(TokenType.COMMAND) && current().value().equals("meta")) {
            meta = parseMeta();
        }

        List<Node> children = parseBlocks();
        return new Document(meta, Collections.unmodifiableList(children));
    }

    // ── Block-level parsing ───────────────────────────────────────────────────

    private List<Node> parseBlocks() {
        List<Node> nodes = new ArrayList<>();
        while (!check(TokenType.EOF) && !check(TokenType.RBRACE)) {
            skipBlankLines();
            if (check(TokenType.EOF) || check(TokenType.RBRACE)) break;

            Node node = parseBlock();
            if (node != null) nodes.add(node);
        }
        return nodes;
    }

    private Node parseBlock() {
        if (check(TokenType.COMMAND)) {
            String cmd = current().value();
            return switch (cmd) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> parseHeading(true);
                case "numnone"  -> parseNumnone();
                case "ul"       -> parseUnorderedList();
                case "ol"       -> parseOrderedList();
                case "code"     -> parseCodeBlock();
                case "table"    -> parseTable();
                case "img"      -> parseImage();
                case "tableofcontent" -> { advance(); yield new TableOfContents(); }
                case "newpage"  -> { advance(); yield new PageBreak(); }
                case "quote"    -> { advance(); yield new Blockquote(parseBlockBody()); }
                case "meta"     -> parseMeta();
                case "date"     -> parseDate();
                case "formula"  -> parseFormula();
                case "matrix"   -> parseMatrix();
                case "label"    -> parseLabel();
                // Inline commands at block level become a paragraph
                default         -> parseParagraph();
            };
        }
        return parseParagraph();
    }

    // ── Headings ──────────────────────────────────────────────────────────────

    private Node parseHeading(boolean numbered) {
        Token cmd   = advance(); // consume \h1...\h6
        int level   = cmd.value().charAt(1) - '0';
        String id   = autoId(level);
        List<Node> children = parseInlineBody();
        return new Heading(level, numbered, id, Collections.unmodifiableList(children));
    }

    private Node parseNumnone() {
        advance(); // consume \numnone
        if (check(TokenType.COMMAND)) {
            String cmd = current().value();
            if (cmd.matches("h[1-6]")) return parseHeading(false);
        }
        throw new ParseException("\\numnone must be followed by a heading command (\\h1–\\h6)",
                current());
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    private Node parseUnorderedList() {
        advance(); // consume ul command
        expect(TokenType.LBRACE);
        List<ListItem> items = parseListItems();
        expect(TokenType.RBRACE);
        return new UnorderedList(Collections.unmodifiableList(items));
    }

    private Node parseOrderedList() {
        advance(); // consume \ol
        expect(TokenType.LBRACE);
        List<ListItem> items = parseListItems();
        expect(TokenType.RBRACE);
        return new OrderedList(Collections.unmodifiableList(items));
    }

    private List<ListItem> parseListItems() {
        List<ListItem> items = new ArrayList<>();
        skipBlankLines();
        while (check(TokenType.COMMAND) && current().value().equals("li")) {
            advance(); // consume \li
            List<Node> children = parseInlineBody();
            items.add(new ListItem(Collections.unmodifiableList(children)));
            skipBlankLines();
        }
        return items;
    }

    // ── Code block ───────────────────────────────────────────────────────────

    private Node parseCodeBlock() {
        advance(); // consume \code
        String lang = "";
        if (check(TokenType.LBRACKET)) {
            Map<String, String> opts = parseOptions();
            lang = opts.getOrDefault("", opts.getOrDefault("lang", ""));
        }
        expect(TokenType.LBRACE);
        StringBuilder sb = new StringBuilder();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            sb.append(current().value());
            advance();
        }
        expect(TokenType.RBRACE);
        return new CodeBlock(lang, sb.toString());
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private Node parseTable() {
        advance(); // consume \table
        Map<String, String> opts = check(TokenType.LBRACKET) ? parseOptions() : Map.of();

        int rows = Integer.parseInt(opts.getOrDefault("r", "0"));
        int cols = Integer.parseInt(opts.getOrDefault("c", "0"));
        String alignStr = opts.getOrDefault("align", "");

        List<String> alignment = parseAlignmentSpec(alignStr, cols);

        expect(TokenType.LBRACE);
        List<List<List<Node>>> cells = parseTableCells();
        expect(TokenType.RBRACE);

        // Look-ahead for optional \tablecaption
        List<Node> caption = null;
        skipBlankLines();
        if (check(TokenType.COMMAND) && current().value().equals("tablecaption")) {
            advance();
            caption = Collections.unmodifiableList(parseInlineBody());
        }

        return new Table(rows, cols, alignment, cells, caption);
    }

    private List<List<List<Node>>> parseTableCells() {
        List<List<List<Node>>> rows = new ArrayList<>();
        skipBlankLines();
        while (check(TokenType.LBRACKET)) {
            advance(); // consume [
            List<List<Node>> row = new ArrayList<>();
            while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
                List<Node> cell = parseInlineUntil(TokenType.COMMA, TokenType.RBRACKET);
                row.add(Collections.unmodifiableList(cell));
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.RBRACKET);
            rows.add(Collections.unmodifiableList(row));
            if (check(TokenType.COMMA)) advance();
            skipBlankLines();
        }
        return rows;
    }

    private List<String> parseAlignmentSpec(String spec, int cols) {
        if (spec.isEmpty()) {
            return Collections.nCopies(Math.max(cols, 1), "l");
        }
        return Arrays.asList(spec.split("\\|"));
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    private Node parseImage() {
        advance(); // consume \img
        Map<String, String> opts = check(TokenType.LBRACKET) ? parseOptions() : Map.of();
        String placement = opts.getOrDefault("pos", opts.getOrDefault("", "h"));
        String width     = opts.getOrDefault("w", "100%");

        expect(TokenType.LBRACE);
        String src = collectUntil(TokenType.RBRACE);
        expect(TokenType.RBRACE);

        // Look-ahead for optional \imgcaption
        List<Node> caption = null;
        skipBlankLines();
        if (check(TokenType.COMMAND) && current().value().equals("imgcaption")) {
            advance();
            caption = Collections.unmodifiableList(parseInlineBody());
        }

        return new Image(placement, width, src.trim(), caption, null);
    }

    // ── Meta ─────────────────────────────────────────────────────────────────

    private Meta parseMeta() {
        advance(); // consume \meta
        Map<String, String> attrs = check(TokenType.LBRACKET) ? parseOptions() : Map.of();
        return new Meta(Collections.unmodifiableMap(attrs));
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    private Node.Date parseDate() {
        advance(); // consume \date
        // Expect \date{\today} or \date{\today[D,M,Y]}
        expect(TokenType.LBRACE);
        // consume \today
        if (check(TokenType.COMMAND) && current().value().equals("today")) advance();
        List<String> fmt = List.of("D", "M", "Y"); // default
        if (check(TokenType.LBRACKET)) {
            advance();
            List<String> parts = new ArrayList<>();
            while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
                if (check(TokenType.IDENT) || check(TokenType.TEXT)) parts.add(current().value());
                advance();
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.RBRACKET);
            if (!parts.isEmpty()) fmt = Collections.unmodifiableList(parts);
        }
        expect(TokenType.RBRACE);
        return new Node.Date(fmt);
    }

    // ── Label ─────────────────────────────────────────────────────────────────

    private Label parseLabel() {
        advance(); // consume \label
        expect(TokenType.LBRACKET);
        String id = "";
        if (check(TokenType.IDENT) || check(TokenType.TEXT)) {
            id = current().value();
            advance();
        }
        expect(TokenType.RBRACKET);
        return new Label(id);
    }

    // ── Paragraph ────────────────────────────────────────────────────────────

    private Node parseParagraph() {
        List<Node> children = new ArrayList<>();
        while (!check(TokenType.EOF) && !check(TokenType.BLANK_LINE) && !check(TokenType.RBRACE)) {
            if (check(TokenType.COMMAND) && isBlockCommand(current().value())) break;
            Node inline = parseInline();
            if (inline != null) children.add(inline);
        }
        if (children.isEmpty()) return null;
        return new Paragraph(Collections.unmodifiableList(children));
    }

    // ── Inline-level parsing ──────────────────────────────────────────────────

    private List<Node> parseInlineBody() {
        if (check(TokenType.LBRACE)) {
            advance();
            List<Node> nodes = parseInlineUntil(TokenType.RBRACE);
            expect(TokenType.RBRACE);
            return nodes;
        }
        if (check(TokenType.LPAREN)) {
            advance();
            List<Node> nodes = parseInlineUntil(TokenType.RPAREN);
            expect(TokenType.RPAREN);
            return nodes;
        }
        return List.of();
    }

    private List<Node> parseBlockBody() {
        expect(TokenType.LBRACE);
        List<Node> nodes = parseBlocks();
        expect(TokenType.RBRACE);
        return Collections.unmodifiableList(nodes);
    }

    private List<Node> parseInlineUntil(TokenType... stops) {
        List<Node> nodes = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            for (TokenType stop : stops) {
                if (check(stop)) return nodes;
            }
            Node n = parseInline();
            if (n != null) nodes.add(n);
        }
        return nodes;
    }

    private Node parseInline() {
        if (check(TokenType.COMMAND)) {
            return switch (current().value()) {
                case "b"       -> { advance(); yield new Bold(parseInlineBody()); }
                case "i"       -> { advance(); yield new Italic(parseInlineBody()); }
                case "u"       -> { advance(); yield new Underline(parseInlineBody()); }
                case "c"       -> { advance(); yield new InlineCode(inlineBodyRaw()); }
                case "br"      -> { advance(); yield new LineBreak(); }
                case "link"    -> parseLink();
                case "ref"     -> parseRef();
                case "label"   -> parseLabel();
                case "fn"      -> { advance(); yield new Footnote(parseInlineBody()); }
                case "formula" -> parseFormula();
                case "matrix"  -> parseMatrix();
                default        -> {
                    Token t = advance();
                    yield new Text("\\" + t.value(), new SourcePos(t.line(), t.column()));
                }
            };
        }
        if (check(TokenType.TEXT) || check(TokenType.IDENT) || check(TokenType.NUMBER)
                || check(TokenType.STRING)) {
            Token t = advance();
            return new Text(t.value(), new SourcePos(t.line(), t.column()));
        }
        if (check(TokenType.NEWLINE)) {
            advance();
            return new Text(" ", new SourcePos(0, 0));
        }
        // Skip anything else
        advance();
        return null;
    }

    // ── Link / Ref ────────────────────────────────────────────────────────────

    private Node parseLink() {
        advance(); // consume \link
        String url = "";
        if (check(TokenType.LBRACKET)) {
            advance();
            url = collectUntil(TokenType.RBRACKET);
            expect(TokenType.RBRACKET);
        }
        List<Node> label = parseInlineBody();
        return new Link(url.trim(), Collections.unmodifiableList(label));
    }

    private Node parseRef() {
        advance(); // consume \ref
        expect(TokenType.LBRACKET);
        String id = collectUntil(TokenType.RBRACKET).trim();
        expect(TokenType.RBRACKET);
        return new Reference(id);
    }

    // ── Formula ───────────────────────────────────────────────────────────────

    private Node parseFormula() {
        advance(); // consume \formula

        // \formula[align]{...}
        if (check(TokenType.LBRACKET)) {
            Map<String, String> opts = parseOptions();
            String mode = opts.getOrDefault("", "display");
            FormulaMode fmode = mode.equals("align") ? FormulaMode.ALIGN : FormulaMode.DISPLAY;
            expect(TokenType.LBRACE);
            String content = collectUntil(TokenType.RBRACE);
            expect(TokenType.RBRACE);
            return new Formula(fmode, content.trim());
        }

        // \formula{...} — display
        if (check(TokenType.LBRACE)) {
            advance();
            String content = collectUntil(TokenType.RBRACE);
            expect(TokenType.RBRACE);
            return new Formula(FormulaMode.DISPLAY, content.trim());
        }

        // \formula(...) — inline
        if (check(TokenType.LPAREN)) {
            advance();
            String content = collectUntil(TokenType.RPAREN);
            expect(TokenType.RPAREN);
            return new Formula(FormulaMode.INLINE, content.trim());
        }

        throw new ParseException("\\formula must be followed by (, {, or [", current());
    }

    // ── Matrix ────────────────────────────────────────────────────────────────

    private Node parseMatrix() {
        advance(); // consume \matrix
        Map<String, String> opts = check(TokenType.LBRACKET) ? parseOptions() : Map.of();
        int rows = Integer.parseInt(opts.getOrDefault("r", "2"));
        int cols = Integer.parseInt(opts.getOrDefault("c", "2"));

        expect(TokenType.LBRACE);
        List<List<String>> cells = new ArrayList<>();
        while (check(TokenType.LBRACKET)) {
            advance();
            List<String> row = new ArrayList<>();
            while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
                row.add(collectUntil(TokenType.COMMA, TokenType.RBRACKET).trim());
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.RBRACKET);
            cells.add(Collections.unmodifiableList(row));
            if (check(TokenType.COMMA)) advance();
        }
        expect(TokenType.RBRACE);
        return new Matrix(rows, cols, Collections.unmodifiableList(cells));
    }

    // ── Options parser ────────────────────────────────────────────────────────

    /**
     * Parses {@code [key=value, key2="string", positional, ...]}
     * Positional options (no key) are stored under "".
     */
    private Map<String, String> parseOptions() {
        expect(TokenType.LBRACKET);
        Map<String, String> opts = new LinkedHashMap<>();
        int positional = 0;

        while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
            if (check(TokenType.IDENT) || check(TokenType.TEXT)) {
                String key = current().value();
                advance();
                if (check(TokenType.EQUALS)) {
                    advance(); // consume =
                    String val = "";
                    if (check(TokenType.STRING) || check(TokenType.IDENT)
                            || check(TokenType.NUMBER) || check(TokenType.TEXT)) {
                        val = current().value();
                        advance();
                    }
                    opts.put(key, val);
                } else {
                    // positional — first positional stored under ""
                    opts.put(positional == 0 ? "" : String.valueOf(positional), key);
                    positional++;
                }
            } else {
                advance(); // skip unexpected tokens
            }
            if (check(TokenType.COMMA)) advance();
        }
        expect(TokenType.RBRACKET);
        return opts;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String inlineBodyRaw() {
        if (check(TokenType.LBRACE)) {
            advance();
            String raw = collectUntil(TokenType.RBRACE);
            expect(TokenType.RBRACE);
            return raw;
        }
        if (check(TokenType.LPAREN)) {
            advance();
            String raw = collectUntil(TokenType.RPAREN);
            expect(TokenType.RPAREN);
            return raw;
        }
        return "";
    }

    private String collectUntil(TokenType... stops) {
        StringBuilder sb = new StringBuilder();
        int depth = 0; // track brace nesting
        outer:
        while (!check(TokenType.EOF)) {
            // Only stop at stop-tokens when we are at depth 0
            if (depth == 0) {
                for (TokenType stop : stops) {
                    if (check(stop)) break outer;
                }
            }
            TokenType cur = current().type();
            if (cur == TokenType.LBRACE) depth++;
            if (cur == TokenType.RBRACE) {
                if (depth == 0) break; // safety — don't consume an unmatched }
                depth--;
            }
            sb.append(current().value());
            advance();
        }
        return sb.toString();
    }

    private void skipBlankLines() {
        while (check(TokenType.BLANK_LINE) || check(TokenType.NEWLINE)) advance();
    }

    private boolean isBlockCommand(String name) {
        return switch (name) {
            case "h1","h2","h3","h4","h5","h6","numnone",
                 "ul","ol","code","table","img","tableofcontent",
                 "newpage","quote","meta","formula","matrix","date" -> true;
            default -> false;
        };
    }

    // counter for auto-generating heading IDs
    private int headingCounter = 0;
    private String autoId(int level) {
        return "sec-" + level + "-" + (++headingCounter);
    }

    private Token current() { return tokens.get(pos); }

    private Token advance() {
        Token t = tokens.get(pos);
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    private boolean check(TokenType type) {
        return tokens.get(pos).type() == type;
    }

    private Token expect(TokenType type) {
        if (!check(type)) {
            throw new ParseException(
                    "Expected %s but found %s (%s)".formatted(type, current().type(), current().value()),
                    current()
            );
        }
        return advance();
    }
}

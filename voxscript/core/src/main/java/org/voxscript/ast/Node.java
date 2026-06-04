package org.voxscript.ast;

import java.util.List;
import java.util.Map;

/**
 * VoxScript Abstract Syntax Tree.
 *
 * Every node in the tree is an instance of {@link Node}.
 * The sealed hierarchy means the compiler enforces exhaustive
 * pattern matching in the renderers — add a new node type and
 * every renderer fails to compile until it handles it.
 *
 * <h2>Design rules</h2>
 * <ul>
 *   <li>All nodes are immutable records.</li>
 *   <li>{@code List<Node>} children are always unmodifiable.</li>
 *   <li>Position info (line/col) lives in {@link SourcePos}, attached to leaf nodes.</li>
 * </ul>
 */
public sealed interface Node permits
        Node.Document,
        Node.Heading,
        Node.Paragraph,
        Node.Text,
        Node.Bold,
        Node.Italic,
        Node.Underline,
        Node.InlineCode,
        Node.LineBreak,
        Node.PageBreak,
        Node.Blockquote,
        Node.UnorderedList,
        Node.OrderedList,
        Node.ListItem,
        Node.CodeBlock,
        Node.Link,
        Node.Label,
        Node.Reference,
        Node.Footnote,
        Node.Image,
        Node.Table,
        Node.TableOfContents,
        Node.Formula,
        Node.Matrix,
        Node.Date,
        Node.Meta,
        Node.RawHtml
{

    // ── Source position ───────────────────────────────────────────────────────

    record SourcePos(int line, int column) {}

    // ── Document root ─────────────────────────────────────────────────────────

    /** The root of every VoxScript document. */
    record Document(Meta meta, List<Node> children) implements Node {}

    // ── Metadata / preamble ───────────────────────────────────────────────────

    /**
     * \meta[title="...", author="...", layout="A4", size="12pt"]
     * Appears at most once, at the top of the document.
     */
    record Meta(Map<String, String> attributes) implements Node {}

    // ── Structure ─────────────────────────────────────────────────────────────

    /**
     * \h1{} through \h6{}.
     * @param level     1–6
     * @param numbered  false when prefixed with \numnone
     * @param id        from a preceding \label[id], or auto-generated
     * @param children  inline content
     */
    record Heading(int level, boolean numbered, String id, List<Node> children) implements Node {}

    /** \p{} or an implicit paragraph from blank-line separation. */
    record Paragraph(List<Node> children) implements Node {}

    /** \tableofcontent — auto-generated from heading nodes */
    record TableOfContents() implements Node {}

    // ── Inline formatting ─────────────────────────────────────────────────────

    /** Plain text run. */
    record Text(String value, SourcePos pos) implements Node {}

    /** \b{} */
    record Bold(List<Node> children) implements Node {}

    /** \i{} */
    record Italic(List<Node> children) implements Node {}

    /** Underline node */
    record Underline(List<Node> children) implements Node {}

    /** \c{} — inline code */
    record InlineCode(String value) implements Node {}

    /** \br */
    record LineBreak() implements Node {}

    /** \newpage */
    record PageBreak() implements Node {}

    // ── Block elements ────────────────────────────────────────────────────────

    /** \quote{} */
    record Blockquote(List<Node> children) implements Node {}

    /** UnorderedList node */
    record UnorderedList(List<ListItem> items) implements Node {}

    /** \ol{ \li{} \li{} } */
    record OrderedList(List<ListItem> items) implements Node {}

    /** \li{} */
    record ListItem(List<Node> children) implements Node {}

    /**
     * \code[lang]{...}
     * @param language  e.g. "python", "java", "voxscript" — empty string if unspecified
     * @param content   raw code text, preserving whitespace
     */
    record CodeBlock(String language, String content) implements Node {}

    // ── Links, labels, references ─────────────────────────────────────────────

    /**
     * \link[url]{label}
     * @param url      the href target
     * @param children inline content for the link label
     */
    record Link(String url, List<Node> children) implements Node {}

    /**
     * \label[id] — marks a location for cross-reference.
     * Typically placed before a heading or figure.
     */
    record Label(String id) implements Node {}

    /**
     * \ref[id] — cross-reference to a \label.
     * The resolver phase replaces the id with the rendered number.
     */
    record Reference(String id) implements Node {}

    /** \fn{} — footnote. Collected and rendered at the bottom of the document. */
    record Footnote(List<Node> children) implements Node {}

    // ── Media ─────────────────────────────────────────────────────────────────

    /**
     * \img[placement, width]{path}
     * @param placement  e.g. "h", "t", "b" — LaTeX-style placement hint
     * @param width      width as a CSS-compatible string, e.g. "50%", "300px"
     * @param src        file path or URL
     * @param caption    from a following \imgcaption{} — may be null
     * @param id         from a preceding \label[id] — may be null
     */
    record Image(String placement, String width, String src, List<Node> caption, String id) implements Node {}

    // ── Tables ────────────────────────────────────────────────────────────────

    /**
     * \table[r=n, c=m, align="c|l|r"]{[H1,H2],[r1c1,r1c2],...}
     *
     * @param rows      declared row count (including header)
     * @param cols      declared column count
     * @param alignment per-column alignment: "l", "c", "r" — list length == cols
     * @param cells     row-major: cells.get(0) is the header row
     * @param caption   from a following \tablecaption{} — may be null
     */
    record Table(
            int rows,
            int cols,
            List<String> alignment,
            List<List<List<Node>>> cells,
            List<Node> caption
    ) implements Node {}

    // ── Mathematics ───────────────────────────────────────────────────────────

    /**
     * \formula(...) or \formula{...} or \formula[mode]{...}
     *
     * @param mode     how to render the formula
     * @param content  raw math content (passed to KaTeX)
     */
    record Formula(FormulaMode mode, String content) implements Node {}

    /**
     * \matrix[r=n, c=m]{[a,b],[c,d]}
     *
     * @param rows    row count
     * @param cols    column count
     * @param cells   row-major cell content as raw math strings
     */
    record Matrix(int rows, int cols, List<List<String>> cells) implements Node {}

    // ── Date ─────────────────────────────────────────────────────────────────

    /**
     * \date{\today[D,M,Y]} or \date{\today}
     * @param format  order of components, e.g. ["D","M","Y"]
     */
    record Date(List<String> format) implements Node {}

    // ── Escape hatch ─────────────────────────────────────────────────────────

    /** Raw HTML passthrough — for when VoxScript has no equivalent yet. */
    record RawHtml(String html) implements Node {}

    // ── Enums ─────────────────────────────────────────────────────────────────

    enum FormulaMode {
        /** \formula(E=mc^2) — rendered inline */
        INLINE,
        /** \formula{x = ...} — displayed on its own line */
        DISPLAY,
        /** \formula[align]{...} — aligned multi-line equations */
        ALIGN
    }
}

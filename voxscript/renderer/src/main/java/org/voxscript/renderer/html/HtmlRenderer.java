package org.voxscript.renderer.html;

import org.voxscript.ast.Node;
import org.voxscript.ast.Node.*;
import org.voxscript.resolver.Resolver.ResolvedDoc;
import org.voxscript.resolver.Resolver.FootnoteEntry;
import org.voxscript.resolver.Resolver.TocEntry;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders a resolved VoxScript AST to a complete, standalone HTML document.
 *
 * Math is rendered via KaTeX loaded from CDN — no server-side rendering needed.
 * The output is clean, semantic HTML5.
 */
public final class HtmlRenderer {

    private final ResolvedDoc resolved;
    private final StringBuilder sb = new StringBuilder();

    // Footnote counter for inline superscript numbering
    private int footnoteIndex = 0;

    public HtmlRenderer(ResolvedDoc resolved) {
        this.resolved = resolved;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String render() {
        Document doc  = resolved.document();
        Meta     meta = doc.meta();

        String title  = meta != null ? meta.attributes().getOrDefault("title", "Untitled") : "Untitled";
        String author = meta != null ? meta.attributes().getOrDefault("author", "") : "";
        String size   = meta != null ? meta.attributes().getOrDefault("size", "12pt") : "12pt";

        htmlHead(title, size);
        sb.append("<body>\n<article class=\"vox-document\">\n");

        if (!title.equals("Untitled") || !author.isEmpty()) {
            sb.append("<header class=\"doc-header\">\n");
            if (!title.isEmpty())  sb.append("  <h1 class=\"doc-title\">").append(esc(title)).append("</h1>\n");
            if (!author.isEmpty()) sb.append("  <p class=\"doc-author\">").append(esc(author)).append("</p>\n");
            sb.append("</header>\n");
        }

        renderNodes(doc.children());
        renderFootnotes();

        sb.append("</article>\n</body>\n</html>\n");
        return sb.toString();
    }

    // ── Node dispatch ─────────────────────────────────────────────────────────

    private void renderNodes(List<Node> nodes) {
        for (Node node : nodes) renderNode(node);
    }

    private void renderNode(Node node) {
        switch (node) {
            case Heading h          -> renderHeading(h);
            case Paragraph p        -> renderParagraph(p);
            case Text t             -> sb.append(esc(t.value()));
            case Bold b             -> { sb.append("<strong>"); renderNodes(b.children()); sb.append("</strong>"); }
            case Italic i           -> { sb.append("<em>"); renderNodes(i.children()); sb.append("</em>"); }
            case Underline u        -> { sb.append("<u>"); renderNodes(u.children()); sb.append("</u>"); }
            case InlineCode ic      -> sb.append("<code>").append(esc(ic.value())).append("</code>");
            case LineBreak lb       -> sb.append("<br>\n");
            case PageBreak pb       -> sb.append("<div class=\"page-break\"></div>\n");
            case Blockquote bq      -> { sb.append("<blockquote>\n"); renderNodes(bq.children()); sb.append("</blockquote>\n"); }
            case UnorderedList ul   -> renderList("ul", ul.items());
            case OrderedList ol     -> renderList("ol", ol.items());
            case CodeBlock cb       -> renderCodeBlock(cb);
            case Link lk            -> renderLink(lk);
            case Label lbl          -> sb.append("<span id=\"").append(esc(lbl.id())).append("\"></span>");
            case Reference ref      -> renderReference(ref);
            case Footnote fn        -> renderFootnoteRef(fn);
            case Image img          -> renderImage(img);
            case Table tbl          -> renderTable(tbl);
            case TableOfContents tc -> renderToc();
            case Formula f          -> renderFormula(f);
            case Matrix mx          -> renderMatrix(mx);
            case Date d             -> renderDate(d);
            case Meta m             -> { /* already handled at document level */ }
            case RawHtml rh         -> sb.append(rh.html());
            case Document doc       -> renderNodes(doc.children()); // shouldn't happen but safe
            default -> { /* unknown node type — skip */ }
        }
    }

    // ── Headings ─────────────────────────────────────────────────────────────

    private void renderHeading(Heading h) {
        String tag = "h" + h.level();
        sb.append("<").append(tag).append(" id=\"").append(esc(h.id())).append("\">");
        if (h.numbered()) {
            String num = resolved.labelNumbers().getOrDefault(h.id(), "");
            // look up by id in TOC
            String tocNum = resolved.toc().stream()
                    .filter(e -> e.id().equals(h.id()))
                    .map(TocEntry::number)
                    .findFirst().orElse("");
            if (!tocNum.isEmpty()) sb.append("<span class=\"heading-num\">").append(tocNum).append(". </span>");
        }
        renderNodes(h.children());
        sb.append("</").append(tag).append(">\n");
    }

    // ── Paragraph ────────────────────────────────────────────────────────────

    private void renderParagraph(Paragraph p) {
        sb.append("<p>");
        renderNodes(p.children());
        sb.append("</p>\n");
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    private void renderList(String tag, List<ListItem> items) {
        sb.append("<").append(tag).append(">\n");
        for (ListItem item : items) {
            sb.append("  <li>");
            renderNodes(item.children());
            sb.append("</li>\n");
        }
        sb.append("</").append(tag).append(">\n");
    }

    // ── Code block ───────────────────────────────────────────────────────────

    private void renderCodeBlock(CodeBlock cb) {
        String lang = cb.language().isEmpty() ? "" : " class=\"language-" + esc(cb.language()) + "\"";
        sb.append("<pre><code").append(lang).append(">")
          .append(esc(cb.content()))
          .append("</code></pre>\n");
    }

    // ── Link ─────────────────────────────────────────────────────────────────

    private void renderLink(Link lk) {
        sb.append("<a href=\"").append(esc(lk.url())).append("\">");
        renderNodes(lk.children());
        sb.append("</a>");
    }

    // ── Reference ────────────────────────────────────────────────────────────

    private void renderReference(Reference ref) {
        String num = resolved.labelNumbers().getOrDefault(ref.id(), "?");
        sb.append("<a href=\"#").append(esc(ref.id())).append("\">").append(num).append("</a>");
    }

    // ── Footnotes ────────────────────────────────────────────────────────────

    private void renderFootnoteRef(Footnote fn) {
        footnoteIndex++;
        sb.append("<sup><a href=\"#fn").append(footnoteIndex)
          .append("\" id=\"fnref").append(footnoteIndex)
          .append("\">").append(footnoteIndex).append("</a></sup>");
    }

    private void renderFootnotes() {
        List<FootnoteEntry> notes = resolved.footnotes();
        if (notes.isEmpty()) return;
        sb.append("<footer class=\"footnotes\">\n<hr>\n<ol>\n");
        for (FootnoteEntry fn : notes) {
            sb.append("  <li id=\"fn").append(fn.index()).append("\">");
            renderNodes(fn.children());
            sb.append(" <a href=\"#fnref").append(fn.index()).append("\">↩</a></li>\n");
        }
        sb.append("</ol>\n</footer>\n");
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    private void renderImage(Image img) {
        String captionText = img.caption() != null ? extractPlainText(img.caption()) : img.src();
        String idAttr = img.id() != null ? " id=\"" + esc(img.id()) + "\"" : "";
        sb.append("<figure").append(idAttr).append(">\n");
        sb.append("  <img src=\"").append(esc(img.src()))
          .append("\" alt=\"").append(esc(captionText))
          .append("\" style=\"width:").append(esc(img.width())).append("\">\n");
        if (img.caption() != null) {
            sb.append("  <figcaption>");
            renderNodes(img.caption());
            sb.append("</figcaption>\n");
        }
        sb.append("</figure>\n");
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private void renderTable(Table tbl) {
        sb.append("<table>\n");
        if (tbl.caption() != null) {
            sb.append("  <caption>");
            renderNodes(tbl.caption());
            sb.append("</caption>\n");
        }
        List<List<List<Node>>> cells = tbl.cells();
        List<String> align = tbl.alignment();

        for (int r = 0; r < cells.size(); r++) {
            sb.append("  <tr>\n");
            List<List<Node>> row = cells.get(r);
            String cellTag = (r == 0) ? "th" : "td";
            for (int c = 0; c < row.size(); c++) {
                String a = c < align.size() ? alignClass(align.get(c)) : "";
                sb.append("    <").append(cellTag).append(a).append(">");
                renderNodes(row.get(c));
                sb.append("</").append(cellTag).append(">\n");
            }
            sb.append("  </tr>\n");
        }
        sb.append("</table>\n");
    }

    private String alignClass(String spec) {
        return switch (spec.trim()) {
            case "c" -> " class=\"align-center\"";
            case "r" -> " class=\"align-right\"";
            default  -> " class=\"align-left\"";
        };
    }

    // ── Table of contents ─────────────────────────────────────────────────────

    private void renderToc() {
        sb.append("<nav class=\"toc\">\n  <h2>Table of Contents</h2>\n  <ul>\n");
        int prevLevel = 1;
        for (TocEntry entry : resolved.toc()) {
            if (entry.level() > prevLevel) {
                sb.append("  <ul>\n");
            } else if (entry.level() < prevLevel) {
                for (int i = entry.level(); i < prevLevel; i++) sb.append("  </ul>\n");
            }
            sb.append("    <li><a href=\"#").append(esc(entry.id())).append("\">")
              .append(esc(entry.number())).append(entry.number().isEmpty() ? "" : ". ")
              .append(esc(entry.text())).append("</a></li>\n");
            prevLevel = entry.level();
        }
        for (int i = 1; i < prevLevel; i++) sb.append("  </ul>\n");
        sb.append("  </ul>\n</nav>\n");
    }

    // ── Math ─────────────────────────────────────────────────────────────────

    private void renderFormula(Formula f) {
        String content = f.content();
        // Convert VoxScript fraction syntax {a}/{b} to \frac{a}{b} for KaTeX
        content = convertFractions(content);

        switch (f.mode()) {
            case INLINE  -> sb.append("\\(").append(content).append("\\)");
            case DISPLAY -> sb.append("\n\\[\n").append(content).append("\n\\]\n");
            case ALIGN   -> sb.append("\n\\[\n\\begin{aligned}\n")
                                .append(content.replace(",", " \\\\\n"))
                                .append("\n\\end{aligned}\n\\]\n");
        }
    }

    private String convertFractions(String content) {
        // Simple {numerator}/{denominator} → \frac{numerator}{denominator}
        return content.replaceAll("\\{([^}]+)\\}\\s*/\\s*\\{([^}]+)\\}", "\\\\frac{$1}{$2}");
    }

    private void renderMatrix(Matrix mx) {
        sb.append("\n\\[\n\\begin{bmatrix}\n");
        for (int r = 0; r < mx.cells().size(); r++) {
            List<String> row = mx.cells().get(r);
            sb.append(String.join(" & ", row));
            if (r < mx.cells().size() - 1) sb.append(" \\\\");
            sb.append("\n");
        }
        sb.append("\\end{bmatrix}\n\\]\n");
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    private void renderDate(Date d) {
        LocalDate today = LocalDate.now();
        String formatted = formatDate(today, d.format());
        String iso = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        sb.append("<time datetime=\"").append(iso).append("\">").append(formatted).append("</time>");
    }

    private String formatDate(LocalDate date, List<String> format) {
        Map<String, String> parts = Map.of(
                "D", String.valueOf(date.getDayOfMonth()),
                "M", date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
                "Y", String.valueOf(date.getYear())
        );
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < format.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(parts.getOrDefault(format.get(i), format.get(i)));
        }
        return sb.toString();
    }

    // ── HTML boilerplate ──────────────────────────────────────────────────────

    private void htmlHead(String title, String fontSize) {
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%%s</title>
                <!-- KaTeX for math rendering -->
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/contrib/auto-render.min.js"
                        onload="renderMathInElement(document.body);"></script>
                <!-- Highlight.js for code blocks -->
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <script>document.addEventListener('DOMContentLoaded', () => hljs.highlightAll());</script>
                <style>
                  :root { --font-size: %%s; }
                  * { box-sizing: border-box; }
                  body { font-family: Georgia, 'Times New Roman', serif; font-size: var(--font-size);
                         max-width: 800px; margin: 0 auto; padding: 48px 32px; line-height: 1.7; color: #1a1a1a; }
                  .doc-header { margin-bottom: 2em; border-bottom: 1px solid #ccc; padding-bottom: 1em; }
                  .doc-title { font-size: 2em; margin: 0 0 0.2em; }
                  .doc-author { color: #555; margin: 0; }
                  h1,h2,h3,h4,h5,h6 { font-family: 'Helvetica Neue', Arial, sans-serif; margin: 1.4em 0 0.4em; }
                  .heading-num { color: #888; font-weight: normal; }
                  p { margin: 0.8em 0; }
                  blockquote { border-left: 3px solid #ccc; margin: 1em 0; padding: 0.5em 1em; color: #555; }
                  .toc { background: #f8f8f8; border: 1px solid #ddd; border-radius: 6px;
                         padding: 1.2em 1.6em; margin: 1.5em 0; }
                  .toc h2 { margin-top: 0; font-size: 1.1em; }
                  .toc ul { margin: 0; padding-left: 1.4em; }
                  .toc li { margin: 0.3em 0; }
                  .toc a { color: #2a5db0; text-decoration: none; }
                  .toc a:hover { text-decoration: underline; }
                  table { border-collapse: collapse; width: 100%%; margin: 1em 0; }
                  th, td { border: 1px solid #ddd; padding: 8px 12px; }
                  th { background: #f4f4f4; }
                  .align-center { text-align: center; }
                  .align-right  { text-align: right; }
                  .align-left   { text-align: left; }
                  figure { margin: 1.5em 0; text-align: center; }
                  figcaption { color: #666; font-size: 0.9em; margin-top: 0.4em; }
                  pre { background: #f6f8fa; border-radius: 6px; padding: 1em; overflow-x: auto; }
                  code { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 0.9em; }
                  .page-break { page-break-after: always; }
                  .footnotes { margin-top: 3em; font-size: 0.9em; color: #555; }
                  .footnotes ol { padding-left: 1.4em; }
                  sup a { color: #2a5db0; text-decoration: none; font-size: 0.75em; }
                  time { font-style: italic; }
                </style>
                </head>
                """.formatted(esc(title), esc(fontSize)));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String extractPlainText(List<Node> nodes) {
        StringBuilder out = new StringBuilder();
        for (Node n : nodes) {
            if (n instanceof Text t) out.append(t.value());
        }
        return out.toString();
    }
}

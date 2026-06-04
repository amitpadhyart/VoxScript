package org.voxscript.renderer.latex;

import org.voxscript.ast.Node;
import org.voxscript.ast.Node.*;
import org.voxscript.resolver.Resolver.ResolvedDoc;

import java.util.List;

/**
 * Renders a VoxScript AST back to valid LaTeX.
 *
 * This is the compatibility layer — it lets users publish to journals
 * that only accept .tex files without ever writing LaTeX by hand.
 *
 * The output targets article documentclass by default.
 * Template selection (IEEE, ACM, Elsevier) is a future milestone.
 */
public final class LatexRenderer {

    private final ResolvedDoc resolved;
    private final StringBuilder sb = new StringBuilder();
    private int footnoteIndex = 0;

    public LatexRenderer(ResolvedDoc resolved) {
        this.resolved = resolved;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String render() {
        Document doc  = resolved.document();
        Meta     meta = doc.meta();

        String title  = meta != null ? meta.attributes().getOrDefault("title", "") : "";
        String author = meta != null ? meta.attributes().getOrDefault("author", "") : "";
        String size   = meta != null ? meta.attributes().getOrDefault("size", "12pt") : "12pt";
        String layout = meta != null ? meta.attributes().getOrDefault("layout", "a4paper") : "a4paper";

        preamble(title, author, size, layout);
        sb.append("\\begin{document}\n\n");

        if (!title.isEmpty())  sb.append("\\maketitle\n\n");

        renderNodes(doc.children());

        sb.append("\n\\end{document}\n");
        return sb.toString();
    }

    // ── Preamble ──────────────────────────────────────────────────────────────

    private void preamble(String title, String author, String size, String layout) {
        sb.append("\\documentclass[").append(size).append(", ").append(layout.toLowerCase()).append("]{article}\n\n");
        sb.append("% Packages\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage[T1]{fontenc}\n");
        sb.append("\\usepackage{amsmath, amssymb}\n");
        sb.append("\\usepackage{graphicx}\n");
        sb.append("\\usepackage{hyperref}\n");
        sb.append("\\usepackage{booktabs}\n");
        sb.append("\\usepackage{listings}\n");
        sb.append("\\usepackage{geometry}\n");
        sb.append("\\geometry{").append(layout.toLowerCase()).append(", margin=1in}\n\n");
        if (!title.isEmpty())  sb.append("\\title{").append(latexEsc(title)).append("}\n");
        if (!author.isEmpty()) sb.append("\\author{").append(latexEsc(author)).append("}\n");
        sb.append("\\date{\\today}\n\n");
    }

    // ── Node dispatch ─────────────────────────────────────────────────────────

    private void renderNodes(List<Node> nodes) {
        for (Node node : nodes) renderNode(node);
    }

    private void renderNode(Node node) {
        switch (node) {
            case Heading h          -> renderHeading(h);
            case Paragraph p        -> { sb.append("\n"); renderNodes(p.children()); sb.append("\n\n"); }
            case Text t             -> sb.append(latexEsc(t.value()));
            case Bold b             -> { sb.append("\\textbf{"); renderNodes(b.children()); sb.append("}"); }
            case Italic i           -> { sb.append("\\textit{"); renderNodes(i.children()); sb.append("}"); }
            case Underline u        -> { sb.append("\\underline{"); renderNodes(u.children()); sb.append("}"); }
            case InlineCode ic      -> sb.append("\\texttt{").append(latexEsc(ic.value())).append("}");
            case LineBreak lb       -> sb.append("\\\\\n");
            case PageBreak pb       -> sb.append("\\newpage\n");
            case Blockquote bq      -> { sb.append("\\begin{quote}\n"); renderNodes(bq.children()); sb.append("\\end{quote}\n"); }
            case UnorderedList ul   -> renderList("itemize", ul.items());
            case OrderedList ol     -> renderList("enumerate", ol.items());
            case CodeBlock cb       -> renderCodeBlock(cb);
            case Link lk            -> renderLink(lk);
            case Label lbl          -> sb.append("\\label{").append(lbl.id()).append("}");
            case Reference ref      -> sb.append("\\ref{").append(ref.id()).append("}");
            case Footnote fn        -> { sb.append("\\footnote{"); renderNodes(fn.children()); sb.append("}"); }
            case Image img          -> renderImage(img);
            case Table tbl          -> renderTable(tbl);
            case TableOfContents tc -> sb.append("\\tableofcontents\n\n");
            case Formula f          -> renderFormula(f);
            case Matrix mx          -> renderMatrix(mx);
            case Date d             -> sb.append("\\today");
            case Meta m             -> { /* handled in preamble */ }
            case RawHtml rh         -> sb.append("% [raw html — skipped in LaTeX export]\n");
            case Document doc       -> renderNodes(doc.children());
            default -> { /* unknown node type */ }
        }
    }

    // ── Headings ─────────────────────────────────────────────────────────────

    private void renderHeading(Heading h) {
        String cmd = switch (h.level()) {
            case 1 -> "section";
            case 2 -> "subsection";
            case 3 -> "subsubsection";
            case 4 -> "paragraph";
            case 5 -> "subparagraph";
            default -> "subparagraph";
        };
        String star = h.numbered() ? "" : "*";
        sb.append("\\").append(cmd).append(star).append("{");
        renderNodes(h.children());
        sb.append("}\n");
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    private void renderList(String env, List<ListItem> items) {
        sb.append("\\begin{").append(env).append("}\n");
        for (ListItem item : items) {
            sb.append("  \\item ");
            renderNodes(item.children());
            sb.append("\n");
        }
        sb.append("\\end{").append(env).append("}\n");
    }

    // ── Code ─────────────────────────────────────────────────────────────────

    private void renderCodeBlock(CodeBlock cb) {
        String lang = cb.language().isEmpty() ? "" : ",language=" + cb.language();
        sb.append("\\begin{lstlisting}[basicstyle=\\ttfamily\\small").append(lang).append("]\n");
        sb.append(cb.content());
        sb.append("\n\\end{lstlisting}\n");
    }

    // ── Link ─────────────────────────────────────────────────────────────────

    private void renderLink(Link lk) {
        sb.append("\\href{").append(lk.url()).append("}{");
        renderNodes(lk.children());
        sb.append("}");
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    private void renderImage(Image img) {
        String width = img.width().replace("%", "").trim();
        String widthLatex;
        try {
            double pct = Double.parseDouble(width) / 100.0;
            widthLatex = pct + "\\textwidth";
        } catch (NumberFormatException e) {
            widthLatex = "0.8\\textwidth";
        }

        sb.append("\\begin{figure}[").append(img.placement()).append("]\n");
        sb.append("  \\centering\n");
        sb.append("  \\includegraphics[width=").append(widthLatex).append("]{").append(img.src()).append("}\n");
        if (img.caption() != null) {
            sb.append("  \\caption{");
            renderNodes(img.caption());
            sb.append("}\n");
        }
        if (img.id() != null) {
            sb.append("  \\label{").append(img.id()).append("}\n");
        }
        sb.append("\\end{figure}\n");
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private void renderTable(Table tbl) {
        String colSpec = String.join("|", tbl.alignment());
        sb.append("\\begin{table}[h]\n\\centering\n");
        if (tbl.caption() != null) {
            sb.append("\\caption{");
            renderNodes(tbl.caption());
            sb.append("}\n");
        }
        sb.append("\\begin{tabular}{|").append(colSpec).append("|}\n\\hline\n");
        for (int r = 0; r < tbl.cells().size(); r++) {
            List<List<Node>> row = tbl.cells().get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) sb.append(" & ");
                if (r == 0) sb.append("\\textbf{");
                renderNodes(row.get(c));
                if (r == 0) sb.append("}");
            }
            sb.append(" \\\\\n\\hline\n");
        }
        sb.append("\\end{tabular}\n\\end{table}\n");
    }

    // ── Math ─────────────────────────────────────────────────────────────────

    private void renderFormula(Formula f) {
        String content = f.content();
        // Convert VoxScript {a}/{b} fractions back to \frac{a}{b}
        content = content.replaceAll("\\{([^}]+)\\}\\s*/\\s*\\{([^}]+)\\}", "\\\\frac{$1}{$2}");

        switch (f.mode()) {
            case INLINE  -> sb.append("$").append(content).append("$");
            case DISPLAY -> sb.append("\n$$\n").append(content).append("\n$$\n");
            case ALIGN   -> {
                sb.append("\n\\begin{align*}\n");
                sb.append(content.replace(",", " \\\\\n"));
                sb.append("\n\\end{align*}\n");
            }
        }
    }

    private void renderMatrix(Matrix mx) {
        sb.append("$$ \\begin{bmatrix}\n");
        for (int r = 0; r < mx.cells().size(); r++) {
            sb.append(String.join(" & ", mx.cells().get(r)));
            if (r < mx.cells().size() - 1) sb.append(" \\\\");
            sb.append("\n");
        }
        sb.append("\\end{bmatrix} $$\n");
    }

    // ── Escaping ─────────────────────────────────────────────────────────────

    private static String latexEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\textbackslash{}")
                .replace("&",  "\\&")
                .replace("%",  "\\%")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("_",  "\\_")
                .replace("{",  "\\{")
                .replace("}",  "\\}")
                .replace("~",  "\\textasciitilde{}")
                .replace("^",  "\\textasciicircum{}");
    }
}

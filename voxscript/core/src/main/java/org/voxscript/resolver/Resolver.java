package org.voxscript.resolver;

import org.voxscript.ast.Node;
import org.voxscript.ast.Node.*;

import java.util.*;

/**
 * The Resolver performs a pre-pass over the AST before rendering.
 *
 * It collects:
 * <ul>
 *   <li>All {@link Label} nodes → id → number mapping</li>
 *   <li>All {@link Heading} nodes → TOC entry list</li>
 *   <li>All {@link Footnote} nodes → ordered footnote list</li>
 * </ul>
 *
 * Renderers query the ResolvedDoc instead of walking the tree twice.
 */
public final class Resolver {

    public record TocEntry(int level, String id, String text, String number) {}
    public record FootnoteEntry(int index, List<Node> children) {}

    public record ResolvedDoc(
            Document document,
            Map<String, String> labelNumbers,   // label-id → display number
            List<TocEntry> toc,
            List<FootnoteEntry> footnotes
    ) {}

    public ResolvedDoc resolve(Document doc) {
        Map<String, String> labelNumbers = new LinkedHashMap<>();
        List<TocEntry>      toc          = new ArrayList<>();
        List<FootnoteEntry> footnotes    = new ArrayList<>();

        // Heading counters: index = level-1
        int[] counters = new int[6];

        walkNodes(doc.children(), counters, labelNumbers, toc, footnotes);
        return new ResolvedDoc(doc, Collections.unmodifiableMap(labelNumbers),
                Collections.unmodifiableList(toc),
                Collections.unmodifiableList(footnotes));
    }

    private void walkNodes(List<Node> nodes, int[] counters,
                           Map<String, String> labelNumbers,
                           List<TocEntry> toc,
                           List<FootnoteEntry> footnotes) {
        String pendingLabel = null;

        for (Node node : nodes) {
            switch (node) {
                case Label lbl -> pendingLabel = lbl.id();

                case Heading h -> {
                    if (h.numbered()) {
                        counters[h.level() - 1]++;
                        // Reset deeper levels
                        Arrays.fill(counters, h.level(), 6, 0);
                    }
                    String number = buildNumber(counters, h.level(), h.numbered());
                    String id = pendingLabel != null ? pendingLabel : h.id();
                    if (pendingLabel != null) {
                        labelNumbers.put(pendingLabel, number);
                        pendingLabel = null;
                    }
                    String text = extractText(h.children());
                    toc.add(new TocEntry(h.level(), id, text, number));
                    walkNodes(h.children(), counters, labelNumbers, toc, footnotes);
                }

                case Footnote fn -> {
                    int idx = footnotes.size() + 1;
                    footnotes.add(new FootnoteEntry(idx, fn.children()));
                }

                case Paragraph p   -> walkNodes(p.children(), counters, labelNumbers, toc, footnotes);
                case Blockquote bq -> walkNodes(bq.children(), counters, labelNumbers, toc, footnotes);
                case Bold b        -> walkNodes(b.children(), counters, labelNumbers, toc, footnotes);
                case Italic i      -> walkNodes(i.children(), counters, labelNumbers, toc, footnotes);
                case Underline u   -> walkNodes(u.children(), counters, labelNumbers, toc, footnotes);
                case UnorderedList ul -> ul.items().forEach(item ->
                        walkNodes(item.children(), counters, labelNumbers, toc, footnotes));
                case OrderedList ol -> ol.items().forEach(item ->
                        walkNodes(item.children(), counters, labelNumbers, toc, footnotes));
                default -> { /* leaf node, nothing to walk */ }
            }
        }
    }

    private String buildNumber(int[] counters, int level, boolean numbered) {
        if (!numbered) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) sb.append('.');
            sb.append(counters[i]);
        }
        return sb.toString();
    }

    private String extractText(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            switch (n) {
                case Text t    -> sb.append(t.value());
                case Bold b    -> sb.append(extractText(b.children()));
                case Italic i  -> sb.append(extractText(i.children()));
                default        -> {}
            }
        }
        return sb.toString();
    }
}

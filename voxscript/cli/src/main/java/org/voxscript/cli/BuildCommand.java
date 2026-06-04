package org.voxscript.cli;

import org.voxscript.lexer.Lexer;
import org.voxscript.parser.ParseException;
import org.voxscript.parser.Parser;
import org.voxscript.ast.Node.Document;
import org.voxscript.resolver.Resolver;
import org.voxscript.renderer.html.HtmlRenderer;
import org.voxscript.renderer.latex.LatexRenderer;

import java.io.IOException;
import java.nio.file.*;

public final class BuildCommand {

    public static void run(String[] args) throws IOException {
        // Parse args: build <file> [--out fmt] [--dest path] [-v]
        String inputPath   = null;
        String outputFormat = "html";
        String destPath    = null;
        boolean verbose    = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--out"     -> outputFormat = args[++i];
                case "--dest"    -> destPath     = args[++i];
                case "-v", "--verbose" -> verbose = true;
                default -> { if (!args[i].startsWith("-")) inputPath = args[i]; }
            }
        }

        if (inputPath == null) {
            System.err.println("Error: no input file specified.");
            System.err.println("Usage: vox build <file.vox>");
            System.exit(1);
        }

        int result = build(Path.of(inputPath), outputFormat,
                destPath != null ? Path.of(destPath) : null, verbose);
        if (result != 0) System.exit(result);
    }

    public static int build(Path input, String outputFormat, Path dest, boolean verbose) throws IOException {
        if (!Files.exists(input)) {
            System.err.println("File not found: " + input);
            return 1;
        }

        long t0 = System.currentTimeMillis();
        String source = Files.readString(input);

        long t1 = System.currentTimeMillis();
        var tokens = new Lexer(source).tokenize();
        if (verbose) timing("Lexed " + tokens.size() + " tokens", t1);

        long t2 = System.currentTimeMillis();
        Document doc;
        try {
            doc = new Parser(tokens).parse();
        } catch (ParseException e) {
            System.err.println("\u001B[31m✗ Parse error: " + e.getMessage() + "\u001B[0m");
            return 1;
        }
        if (verbose) timing("Parsed AST (" + doc.children().size() + " nodes)", t2);

        long t3 = System.currentTimeMillis();
        var resolved = new Resolver().resolve(doc);
        if (verbose) timing("Resolved (TOC: " + resolved.toc().size() + " entries)", t3);

        long t4 = System.currentTimeMillis();
        String output = switch (outputFormat.toLowerCase()) {
            case "html"         -> new HtmlRenderer(resolved).render();
            case "latex", "tex" -> new LatexRenderer(resolved).render();
            case "pdf" -> {
                System.err.println("PDF renderer not yet available. Use --out html.");
                yield null;
            }
            default -> {
                System.err.println("Unknown format: " + outputFormat + " (html | latex | pdf)");
                yield null;
            }
        };
        if (output == null) return 1;
        if (verbose) timing("Rendered " + output.length() + " chars", t4);

        Path outPath = dest != null ? dest : deriveOutputPath(input, outputFormat);
        Files.writeString(outPath, output);

        System.out.printf("\u001B[32m✓ %s → %s (%dms)\u001B[0m%n",
                input.getFileName(), outPath.getFileName(), elapsed(t0));
        return 0;
    }

    static Path deriveOutputPath(Path input, String format) {
        String name = input.getFileName().toString();
        if (name.endsWith(".vox")) name = name.substring(0, name.length() - 4);
        String ext = switch (format.toLowerCase()) {
            case "latex", "tex" -> ".tex";
            case "pdf"          -> ".pdf";
            default             -> ".html";
        };
        return input.resolveSibling(name + ext);
    }

    static void timing(String label, long start) {
        System.out.printf("  [%4dms] %s%n", elapsed(start), label);
    }

    static long elapsed(long start) { return System.currentTimeMillis() - start; }
}

package org.voxscript.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.voxscript.lexer.Lexer;
import org.voxscript.parser.Parser;
import org.voxscript.resolver.Resolver;
import org.voxscript.renderer.html.HtmlRenderer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live preview server for VoxScript documents.
 *
 * Serves the rendered HTML on localhost and uses Server-Sent Events (SSE)
 * to push a reload signal to the browser whenever the source file changes.
 *
 * No external web server dependency — uses Java's built-in HttpServer.
 *
 * Usage:
 *   new PreviewServer(path, 4242).start();
 *   → open http://localhost:4242 in your browser
 */
public final class PreviewServer {

    private final Path sourcePath;
    private final int port;
    private final AtomicReference<String> renderedHtml = new AtomicReference<>("");
    private final CopyOnWriteArrayList<PrintWriter> sseClients = new CopyOnWriteArrayList<>();

    public PreviewServer(Path sourcePath, int port) {
        this.sourcePath = sourcePath;
        this.port       = port;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void start() throws IOException {
        // Initial build
        rebuild();

        // HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/",        this::handleDocument);
        server.createContext("/reload",  this::handleSse);
        server.createContext("/health",  ex -> respond(ex, 200, "text/plain", "ok"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.printf("VoxScript preview: \u001B[36mhttp://localhost:%d\u001B[0m%n", port);
        System.out.printf("Watching: %s%n", sourcePath.toAbsolutePath());

        // File watcher thread
        startWatcher();
    }

    // ── File watcher ──────────────────────────────────────────────────────────

    private void startWatcher() {
        Thread watcher = new Thread(() -> {
            FileTime last = FileTime.fromMillis(0);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    FileTime current = Files.getLastModifiedTime(sourcePath);
                    if (current.compareTo(last) > 0) {
                        last = current;
                        rebuild();
                        notifyClients();
                    }
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    System.err.println("Watcher error: " + e.getMessage());
                }
            }
        }, "vox-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void rebuild() {
        try {
            String source   = Files.readString(sourcePath);
            var tokens      = new Lexer(source).tokenize();
            var doc         = new Parser(tokens).parse();
            var resolved    = new Resolver().resolve(doc);
            String html     = new HtmlRenderer(resolved).render();

            // Inject SSE auto-reload script before </body>
            String injected = html.replace("</body>", RELOAD_SCRIPT + "</body>");
            renderedHtml.set(injected);

            System.out.println("\u001B[32m✓ Rebuilt\u001B[0m " + sourcePath.getFileName());
        } catch (Exception e) {
            // On error, show the error in the browser rather than crashing
            renderedHtml.set(errorPage(e.getMessage()));
            System.err.println("\u001B[31mBuild error: " + e.getMessage() + "\u001B[0m");
        }
    }

    // ── HTTP handlers ─────────────────────────────────────────────────────────

    private void handleDocument(HttpExchange ex) throws IOException {
        byte[] body = renderedHtml.get().getBytes();
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    }

    private void handleSse(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type",  "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection",    "keep-alive");
        ex.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(ex.getResponseBody()));
        sseClients.add(writer);
        writer.write("data: connected\n\n");
        writer.flush();

        // Keep connection open until client disconnects
        try {
            while (!writer.checkError()) {
                Thread.sleep(1000);
                writer.write(": ping\n\n");
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sseClients.remove(writer);
        }
    }

    private void notifyClients() {
        sseClients.removeIf(writer -> {
            writer.write("data: reload\n\n");
            writer.flush();
            return writer.checkError();
        });
    }

    private static void respond(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        try (var os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private static final String RELOAD_SCRIPT = """
            <script>
              (function() {
                const es = new EventSource('/reload');
                es.onmessage = e => { if (e.data === 'reload') location.reload(); };
                es.onerror   = ()  => setTimeout(() => location.reload(), 1000);
              })();
            </script>
            """;

    private static String errorPage(String message) {
        return """
                <!DOCTYPE html><html><head><title>VoxScript Error</title>
                <style>body{font-family:monospace;background:#1a0000;color:#ff6b6b;padding:2em;}
                pre{background:#2a0000;padding:1em;border-radius:6px;white-space:pre-wrap;}</style>
                </head><body>
                <h2>⚠ Build Error</h2>
                <pre>%s</pre>
                </body></html>
                """.formatted(message == null ? "Unknown error" : message
                        .replace("&", "&amp;").replace("<", "&lt;"));
    }
}

package org.voxscript.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;

public final class WatchCommand {

    public static void run(String[] args) throws Exception {
        String inputPath    = null;
        String outputFormat = "html";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> outputFormat = args[++i];
                default      -> { if (!args[i].startsWith("-")) inputPath = args[i]; }
            }
        }

        if (inputPath == null) {
            System.err.println("Usage: vox watch <file.vox>");
            System.exit(1);
        }

        Path input = Path.of(inputPath);
        String fmt = outputFormat;

        System.out.printf("Watching \u001B[36m%s\u001B[0m for changes (Ctrl+C to stop)...%n",
                input.toAbsolutePath());

        // Initial build
        BuildCommand.build(input, fmt, null, false);

        FileTime lastModified = Files.getLastModifiedTime(input);

        while (true) {
            try {
                Thread.sleep(300);
                FileTime current = Files.getLastModifiedTime(input);
                if (current.compareTo(lastModified) > 0) {
                    lastModified = current;
                    BuildCommand.build(input, fmt, null, false);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                System.err.println("Watch error: " + e.getMessage());
            }
        }
    }
}

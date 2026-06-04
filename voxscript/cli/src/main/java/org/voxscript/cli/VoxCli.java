package org.voxscript.cli;

/**
 * VoxScript CLI — zero external dependencies.
 *
 * Usage:
 *   java -jar vox.jar build  <file.vox> [--out html|latex] [--dest path] [-v]
 *   java -jar vox.jar watch  <file.vox> [--out html|latex]
 *   java -jar vox.jar init   [project-name]
 *   java -jar vox.jar help
 */
public final class VoxCli {

    static final String VERSION = "0.1.0";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { printHelp(); return; }

        switch (args[0]) {
            case "build"   -> BuildCommand.run(args);
            case "watch"   -> WatchCommand.run(args);
            case "init"    -> InitCommand.run(args);
            case "version" -> System.out.println("VoxScript " + VERSION);
            case "help"    -> printHelp();
            default        -> { System.err.println("Unknown command: " + args[0]); printHelp(); System.exit(1); }
        }
    }

    static void printHelp() {
        System.out.println("""
            VoxScript — The modern document compiler

            Usage:
              vox build  <file.vox>              Compile to HTML (default)
              vox build  <file.vox> --out latex  Compile to LaTeX
              vox build  <file.vox> --out html --dest out/index.html
              vox watch  <file.vox>              Watch and rebuild on change
              vox init   [name]                  Create a new VoxScript project
              vox version                        Print version
              vox help                           Show this help

            Flags:
              --out   html | latex | pdf   Output format (default: html)
              --dest  <path>               Output file path
              -v      --verbose            Show timing info
            """);
    }
}

package org.voxscript.cli;

import java.io.IOException;
import java.nio.file.*;

public final class InitCommand {

    public static void run(String[] args) throws IOException {
        String name = args.length > 1 ? args[1] : "my-voxdoc";
        Path dir = Path.of(name);

        if (Files.exists(dir)) {
            System.err.println("Directory already exists: " + name);
            System.exit(1);
        }

        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("assets"));

        Files.writeString(dir.resolve("main.vox"), sampleDoc(name));
        Files.writeString(dir.resolve(".gitignore"), "*.html\n*.pdf\n*.tex\n");
        Files.writeString(dir.resolve("README.md"),
            "# " + name + "\n\nA VoxScript document.\n\n## Build\n\n```\nvox build main.vox\n```\n");

        System.out.printf("""
            \u001B[32m✓ Created project: %s/\u001B[0m
              main.vox     \u2190 your document
              assets/      \u2190 images go here

            \u001B[36mNext steps:\u001B[0m
              cd %s
              vox build main.vox
              vox watch main.vox
            %n""", name, name);
    }

    static String sampleDoc(String name) {
        return """
            \\meta[title="%s", author="Your Name", layout="A4", size="12pt"]

            \\tableofcontent

            \\h1{Introduction}

            Welcome to \\b{VoxScript} — a modern document language.
            This replaces LaTeX with clean, readable syntax that compiles to HTML, PDF, and LaTeX.

            \\h2{Text Formatting}

            You can write \\b{bold}, \\i{italic}, and \\u{underlined} text inline.
            Inline code looks like this: \\c{x = 1 + 2}.

            \\h2{Mathematics}

            Inline math: \\formula(E=mc^2). Display math on its own line:

            \\formula{x = {-b \\pm \\sqrt{b^2 - 4ac}} / {2a}}

            A matrix:

            \\matrix[r=2,c=2]{[\\alpha,\\beta],[\\gamma,\\delta]}

            \\h2{Lists}

            \\ul {
                \\li{Clean, readable syntax}
                \\li{Outputs HTML, LaTeX, and PDF}
                \\li{Live preview with \\c{vox watch}}
            }

            \\h2{Code}

            \\code[java]{
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello, VoxScript!");
                }
            }
            }

            \\h2{Tables}

            \\table[r=3,c=3,align="c|l|r"] {
                [ID, Name, Score],
                [1, Alice, 95.5],
                [2, Bob, 88.0]
            }
            \\tablecaption{Sample test results.}

            \\h1{Conclusion}

            Learn more at \\link[https://github.com]{GitHub}.

            Built on \\date{\\today[D,M,Y]}.
            """.formatted(name);
    }
}

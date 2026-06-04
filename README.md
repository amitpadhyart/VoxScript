# VoxScript

**The modern document language.** Clean syntax, semantic structure, outputs HTML + LaTeX + PDF.

```
\meta[title="My Paper", author="Jane Doe", layout="A4"]

\tableofcontent

\h1{Introduction}

VoxScript is \b{simpler} than LaTeX and \i{more powerful} than Markdown.
Inline math: \formula(E=mc^2). Display math:

\formula{x = {-b \pm \sqrt{b^2 - 4ac}} / {2a}}

\h2{A Table}

\table[r=3,c=3,align="c|l|r"] {
    [ID, Name, Score],
    [1, Alice, 95.5],
    [2, Bob,   88.0]
}
```

## Build

Requires Java 21+.

```bash
./build.sh
java -jar dist/vox.jar help
```

## Usage

```bash
vox init my-paper          # scaffold new project
vox build doc.vox          # → doc.html
vox build doc.vox --out latex  # → doc.tex
vox watch doc.vox          # rebuild on save
```

## Project Structure

```
voxscript/
├── core/          # Lexer → Parser → AST → Resolver  (zero deps)
├── renderer/      # HtmlRenderer, LatexRenderer        (depends on core)
├── cli/           # `vox` command-line tool            (depends on both)
├── server/        # Live preview server with hot reload
└── dist/vox.jar   # Fat JAR — everything in one file
```

## Architecture

```
Source (.vox)
    ↓  Lexer          → Token stream
    ↓  Parser         → AST (sealed Node hierarchy)
    ↓  Resolver       → Labels, TOC, footnote numbering
    ↓  Renderer       → HTML | LaTeX | (PDF coming)
```

## Language Reference

| Concept         | VoxScript                          |
|-----------------|-------------------------------------|
| Heading         | `\h1{Title}` … `\h6{Title}`        |
| No numbering    | `\numnone\h2{Title}`               |
| Bold/Italic     | `\b{bold}` `\i{italic}` `\u{...}` |
| Inline code     | `\c{code}`                         |
| Link            | `\link[url]{label}`                |
| Image           | `\img[h,50%]{file.png}`            |
| Figure caption  | `\imgcaption{...}`                 |
| Unordered list  | `\ul { \li{...} \li{...} }`       |
| Ordered list    | `\ol { \li{...} \li{...} }`       |
| Code block      | `\code[python]{...}`               |
| Table           | `\table[r=3,c=2]{[H1,H2],[v,v]}`  |
| Table caption   | `\tablecaption{...}`               |
| Inline math     | `\formula(E=mc^2)`                 |
| Display math    | `\formula{x = {a}/{b}}`           |
| Aligned eqns    | `\formula[align]{a &= b, c &= d}` |
| Matrix          | `\matrix[r=2,c=2]{[a,b],[c,d]}`   |
| Blockquote      | `\quote{...}`                      |
| Footnote        | `\fn{...}`                         |
| Label           | `\label[id]`                       |
| Cross-reference | `\ref[id]`                         |
| Date            | `\date{\today[D,M,Y]}`            |
| Page break      | `\newpage`                         |
| Line break      | `\br`                              |
| TOC             | `\tableofcontent`                  |
| Metadata        | `\meta[title="...", author="..."]` |

## Roadmap

- [ ] PDF renderer (Apache FOP or headless Chrome)
- [ ] VS Code extension (syntax highlighting + live preview)
- [ ] Bibliography / `\cite` + `\bibliography`
- [ ] LaTeX template system (IEEE, ACM, Elsevier)
- [ ] `\include{file.vox}` for multi-file documents
- [ ] Custom command definitions (`\define`)
- [ ] GraalVM native binary (no JVM required)

## License

MIT

# JOL Memory Analyser

A practical exploration of JVM object memory layout using [Java Object Layout (JOL)](https://openjdk.org/projects/code-tools/jol/). Compares shallow vs retained sizes, field alignment and padding, boxing overhead, and per-element costs across common Java data structures.

Built and tested on Java 17+.

## What it covers

- **ClassLayout** — object header, field offsets, and alignment gaps for any class
- **GraphLayout** — full retained size (object + all reachable references)
- **Primitive vs boxed collections** — quantified cost of boxing
- **Field reordering** — how the JVM reorders fields to minimize padding regardless of source order
- **Scaling behaviour** — how bytes/element converges as collection size grows
- **Project scanner** — scans compiled classes, detects boxed fields and alignment gaps, and writes two Excel workbooks (technical + developer-friendly)

## Project structure

```
src/main/java/com/asraf/jol/
├── Main.java                        — Introductory demo: ClassLayout, GraphLayout, Node chains
├── MemoryAnalyser.java              — Data structure comparison and deep-dive analysis
├── MemoryReport.java                — Structured tabular report with key findings and projections
├── ProjectScanner.java              — CLI: scans compiled classes; writes two .xlsx reports
├── Node.java                        — Linked node used in the shallow vs retained demonstration
├── BadOrder.java                    — Fields declared small-first (JVM reorders at runtime)
├── GoodOrder.java                   — Fields declared large-first (same layout as BadOrder)
├── scanner/
│   ├── ClassScanner.java            — Walks a classes directory and loads each concrete class
│   ├── LayoutAnalyser.java          — Analyses a single class: padding, boxed fields, root cause
│   └── LayoutAnalysis.java          — Immutable result record for one analysed class
└── report/
    ├── ExcelReporter.java           — Technical workbook (JOL-oriented columns)
    └── DevFriendlyReporter.java     — Summary, Analysis, Legend sheets (definitions and glossary)

src/test/java/com/asraf/jol/
└── MemoryReportTest.java            — Parameterised and focused assertions on memory guarantees
```

## Requirements

- Java 17+
- Maven 3.6+

## Use as a Maven dependency

After the artifact is published to Maven Central, add:

```xml
<dependency>
  <groupId>io.github.mm-asraf</groupId>
  <artifactId>jol-memory-analyser</artifactId>
  <version>1.0.0</version>
</dependency>
```

The scanner entry point is **`com.asraf.jol.ProjectScanner`**. It analyses **compiled** classes (`target/classes` or another output directory). Run **`mvn compile`** (or your build) on the host project before scanning.

### Runnable standalone JAR

`mvn package` also builds an uber-JAR with classifier **`standalone`** and `Main-Class` set to `ProjectScanner`:

```bash
java -jar target/jol-memory-analyser-1.0.0-standalone.jar
java -jar target/jol-memory-analyser-1.0.0-standalone.jar --dir /path/to/other/target/classes
java -jar target/jol-memory-analyser-1.0.0-standalone.jar --file src/main/java/com/example/MyClass.java
```

Use this JAR from any directory; pass **`--dir`**, **`--file`**, **`--class`**, **`--output`**, and **`--output-dev`** as documented below.

### IDE integration

#### IntelliJ IDEA plugin (editor context menu)

The **`intellij-plugin/`** module is a small IntelliJ Platform plugin that adds:

- **Editor → right-click → JOL Memory Analyser → Scan with JOL Memory Analyser** (current `.java` file)
- The same submenu: **Scan whole project with JOL Memory Analyser**
- **Tools → Scan project with JOL Memory Analyser…**
- **Settings → Tools → JOL Memory Analyser** to set an explicit path to `jol-memory-analyser-*-standalone.jar`

The plugin runs `java -jar …/jol-memory-analyser-*-standalone.jar` in your project root. It finds the JAR by, in order: environment variable **`JOL_MEMORY_ANALYSER_JAR`**, the optional setting above, **`target/*-standalone.jar`** in the open project, then **`~/.m2/repository/io/github/mm-asraf/jol-memory-analyser/`**. Build **`mvn package`** in this repo first, or install the standalone artifact from Maven Central before scanning other projects.

See **`intellij-plugin/README.md`** for how to run **`runIde`** and package the plugin ZIP.

#### IntelliJ without the plugin (External Tools)

If you prefer not to install the plugin, use **External Tools**:

1. **Settings → Tools → External Tools → +**
2. **Name:** `JOL Memory Analyser — scan project`
3. **Program:** `mvn`
4. **Arguments:** `-q compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner`
5. **Working directory:** `$ProjectFileDir$`
6. Save. Run via **Tools → External Tools** after compile.

For the **current Java file**, use **Arguments:**  
`-q compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner -Dexec.args="--file $FilePath$"`  
(note: the **host** project must declare the `exec-maven-plugin` / dependency on this library, or use the **standalone JAR** approach instead.)

#### VS Code

This repository includes **`.vscode/tasks.json`** with:

- **JOL Memory Analyser: scan project**
- **JOL Memory Analyser: scan active Java file**

Run **Tasks: Run Task** from the Command Palette. The terminal lists the generated workbook paths; open them from the project root (or paths shown in the log).

## Running

```bash
# Build
mvn package

# Full structured report (tabular output + key findings + 1M projections)
mvn exec:java -Dexec.mainClass=com.asraf.jol.MemoryReport

# Data structure comparison with field reordering and scale demos
mvn exec:java -Dexec.mainClass=com.asraf.jol.MemoryAnalyser

# Introductory demo (ClassLayout, GraphLayout, Node chain)
mvn exec:java -Dexec.mainClass=com.asraf.jol.Main

# Run tests — measurements are printed to console alongside pass/fail
mvn test
```

## Project scanner — Excel reports

`ProjectScanner` walks compiled `.class` files, analyses each class with JOL, and writes **two** workbooks:

| File (defaults) | Contents |
|-----------------|----------|
| `memory-report.xlsx` | Technical sheet: instance size, padding from JOL, boxed fields, root cause, suggestions |
| `memory-report-developer.xlsx` | Developer workbook: **Summary**, **Analysis** (status, MB columns, finding and recommendation), **Legend** (column reference and glossary) |

Use `--output path/to/report.xlsx` for the technical file. If you omit `--output-dev`, the second file is named by inserting `-developer` before `.xlsx` (e.g. `report.xlsx` → `report-developer.xlsx`).

**Row colours** (both workbooks use the same severity rules):

| Colour | Meaning |
|--------|---------|
| Green | No avoidable internal gaps and no boxed instance fields |
| Yellow | Either avoidable alignment gaps **or** boxed fields |
| Red | Avoidable gaps **and** boxed fields |

Mandatory trailing alignment (object rounded to 8 bytes) does not turn the row yellow by itself.

### Scan commands

```bash
# Scan this project's compiled classes → memory-report.xlsx + memory-report-developer.xlsx
mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner

# Scan a specific class by fully-qualified name
mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
    -Dexec.args="--class com.example.User"

# Derive class name from a source file path
mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
    -Dexec.args="--file src/main/java/com/example/User.java"

# Scan an external project's compiled classes
mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
    -Dexec.args="--dir /path/to/other-project/target/classes"

# Custom technical + developer output paths
mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
    -Dexec.args="--dir /path/to/other-project/target/classes --output analysis.xlsx --output-dev analysis-guide.xlsx"
```

### Sample console output

```
Analysing 10 class(es)...

Class                                     Size(B) Padding(B)  Boxed  Root Cause
────────────────────────────────────────────────────────────────────────────────
GoodOrder                                      32          6      0  6 bytes mandatory object-alignment padding
BadOrder                                       32          6      0  6 bytes mandatory object-alignment padding
Node                                           24          4      0  4 bytes mandatory object-alignment padding
MemoryAnalyser                                 16          4      0  None — layout is optimal
Main                                           16          4      0  None — layout is optimal

Technical workbook written: /path/to/project/memory-report.xlsx
Developer workbook written: /path/to/project/memory-report-developer.xlsx
Review the generated Excel workbooks for detailed layout findings.
```

## Sample MemoryReport output

```
════════════════════════════════════════════════════════════════════════════════
  MEMORY LAYOUT REPORT — 1000 elements per structure
════════════════════════════════════════════════════════════════════════════════
Structure              Category    Shallow   Retained    Bytes/elem  Overhead
────────────────────────────────────────────────────────────────────────────────
  -- arrays --
  int[1000]            arrays     4,016 B    4,016 B       4.0 B      1.0x
  long[1000]           arrays     8,016 B    8,016 B       8.0 B      2.0x
  double[1000]         arrays     8,016 B    8,016 B       8.0 B      2.0x
  -- boxed --
  Integer[1000]        boxed      4,016 B   20,016 B      20.0 B      5.0x
  -- lists --
  ArrayList<Int>       lists         16 B   20,432 B      20.4 B      5.1x
  LinkedList<Int>      lists         24 B   56,024 B      56.0 B     14.0x
  -- maps --
  HashMap<I,I>         maps          48 B   88,600 B      88.6 B     22.2x
```

## Key takeaways

| Structure             | Bytes / element | vs `int[]` |
|-----------------------|-----------------|------------|
| `int[]`               | ~4 B            | 1×         |
| `ArrayList<Integer>`  | ~20 B           | ~5×        |
| `LinkedList<Integer>` | ~56 B           | ~14×       |
| `HashMap<K,V>`        | ~88 B           | ~22×       |

- `LinkedList` carries a 24-byte `Node` wrapper per element — rarely justified.
- Field declaration order does not affect memory footprint; the JVM reorders by alignment requirement.
- Retained size can be orders of magnitude larger than shallow size once references are involved.
- Empty collections still allocate internal arrays or sentinel nodes — overhead exists at N=0.

## Publishing to Maven Central

To publish a signed release:

1. Register on [Maven Central](https://central.sonatype.com/) and claim the namespace `io.github.mm-asraf` (must match your verified GitHub handle).
2. Update `groupId` in `pom.xml` to your verified namespace.
3. Update the `<scm>` and `<url>` sections to point at your repository.
4. Generate a GPG key (`gpg --gen-key`) and upload the public key to a keyserver.
5. Add your Central portal token to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

6. Run the release:

```bash
mvn deploy -Prelease
```

## License

[Apache License 2.0](LICENSE)

# JOL Memory Analyser — IntelliJ IDEA plugin

Adds **Scan with JOL Memory Analyser** to the editor context menu (for `.java` files), a **scan whole project** action, and **Tools → Scan project with JOL Memory Analyser…**.

## Prerequisites

- **IntelliJ IDEA / IC 2023.2 or newer** (build `232`+; the plugin is compiled against the 2023.2 platform API).
- **JDK 17** (toolchain in `build.gradle.kts`)
- **Gradle** (use IntelliJ’s bundled Gradle or install Gradle and generate a wrapper)
- A **`jol-memory-analyser-*-standalone.jar`** on disk — build the main Maven module with `mvn package`, or resolve the **standalone** classifier from Maven Central after release.

The plugin resolves the JAR automatically when possible (including IntelliJ’s **effective Maven local repository** from **Settings → Build, Execution, Deployment → Build Tools → Maven**). Override with **Settings → Tools → JOL Memory Analyser** or **`JOL_MEMORY_ANALYSER_JAR`**. The configured path applies to **every project and every IDE window**; terminal-only env vars are often invisible to the IDE when it is started from the Dock or Spotlight.

## Develop and debug

1. Open the **`intellij-plugin`** directory in IntelliJ IDEA (Gradle project).
2. Sync Gradle; wait for the IntelliJ Platform SDK to download.
3. Run the Gradle task **`runIde`** (or **Run Plugin** if using the Gradle tool window).

A sandbox IDE opens with the plugin loaded.

## Package a plugin ZIP

```bash
cd intellij-plugin
gradle buildPlugin   # or: ./gradlew buildPlugin
```

The ZIP is under **`build/distributions/`**. Install with **Settings → Plugins → ⚙ → Install Plugin from Disk…**.

## Behaviour

- Working directory: **project base path** (where `memory-report.xlsx` / `memory-report-developer.xlsx` are written).
- **Compile** the host project before scanning so `target/classes` exists (`Build → Build Project` or `mvn compile`).
- Full **stdout/stderr** from the scanner streams live into the **JOL Memory Analyser** tool window (**View → Tool Windows** or it opens automatically at the bottom). If the standalone JAR is missing, the same tool window shows setup instructions instead of a modal dialog.

Plugin ID: **`io.github.mm-asraf.jol-memory-analyser`**.

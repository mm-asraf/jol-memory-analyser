package com.asraf.jol;

import com.asraf.jol.report.DevFriendlyReporter;
import com.asraf.jol.report.ExcelReporter;
import com.asraf.jol.scanner.ClassScanner;
import com.asraf.jol.scanner.LayoutAnalyser;
import com.asraf.jol.scanner.LayoutAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for scanning compiled Java classes and generating a memory-layout Excel report.
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner
 *       → scan target/classes, write memory-report.xlsx
 *
 *   mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
 *       -Dexec.args="--class com.example.User"
 *       → analyse one class by fully-qualified name
 *
 *   mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
 *       -Dexec.args="--file src/main/java/com/example/User.java"
 *       → derive class name from source file path, then analyse
 *
 *   mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner \
 *       -Dexec.args="--dir /path/to/other/project/target/classes --output report.xlsx"
 *       → scan an external project's compiled classes
 *
 * Writes two workbooks: a technical sheet ({@code memory-report.xlsx}) and a developer guide
 * ({@code memory-report-developer.xlsx} by default). Override with {@code --output} and
 * {@code --output-dev}.
 */
public class ProjectScanner {

    private static final Logger log = LoggerFactory.getLogger(ProjectScanner.class);

    public static void main(String[] args) throws Exception {
        Config config = parseArgs(args);

        ClassScanner        scanner     = new ClassScanner();
        LayoutAnalyser      analyser    = new LayoutAnalyser();
        ExcelReporter       techReporter = new ExcelReporter();
        DevFriendlyReporter devReporter  = new DevFriendlyReporter();

        List<Class<?>> classes = resolveClasses(scanner, config);
        log.info("Analysing {} class(es)...", classes.size());

        List<LayoutAnalysis> results = new ArrayList<>();
        int skipped = 0;
        for (Class<?> clazz : classes) {
            try {
                results.add(analyser.analyse(clazz));
            } catch (Throwable e) {
                skipped++;
                log.warn("Skipping {} — {}{}", clazz.getName(), deepestMessage(e), classpathHint(e));
            }
        }
        if (skipped > 0) {
            log.warn("{} class(es) skipped (see warnings above). For Spring / multi-module apps run from project root: mvn -q compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner", skipped);
        }

        printConsoleTable(results);

        Path technicalOut = Paths.get(config.outputFile);
        Path developerOut = Paths.get(
                config.outputDevFile != null ? config.outputDevFile : deriveDeveloperPath(config.outputFile));

        techReporter.write(results, technicalOut);
        devReporter.write(results, developerOut);
        log.info("Review the generated Excel workbooks for detailed layout findings.");
    }

    /** Turns {@code report.xlsx} into {@code report-developer.xlsx}. */
    static String deriveDeveloperPath(String technicalOutputFile) {
        if (technicalOutputFile.endsWith(".xlsx")) {
            return technicalOutputFile.substring(0, technicalOutputFile.length() - 5) + "-developer.xlsx";
        }
        return technicalOutputFile + "-developer.xlsx";
    }

    private static List<Class<?>> resolveClasses(ClassScanner scanner, Config config) throws Exception {
        if (config.className != null) {
            Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            log.info("Loading class: {} (from target/classes and target/test-classes under {})",
                    config.className, projectRoot);
            return List.of(scanner.loadSingleFromProject(config.className, projectRoot));
        }
        Path dir = config.scanDir != null ? Paths.get(config.scanDir) : Paths.get("target/classes");
        log.info("Scanning: {}", dir.toAbsolutePath());
        return scanner.scan(dir);
    }

    private static void printConsoleTable(List<LayoutAnalysis> results) {
        log.info("");
        log.info(String.format("%-40s %8s %10s %6s  %s",
                "Class", "Size(B)", "Padding(B)", "Boxed", "Root Cause"));
        log.info("─".repeat(100));
        results.forEach(a -> log.info(String.format("%-40s %8d %10d %6d  %s",
                truncate(a.simpleClassName(), 40),
                a.instanceSize(),
                a.paddingBytes(),
                a.boxedFieldNames().size(),
                truncate(a.rootCause(), 50))));
        log.info("");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String deepestMessage(Throwable e) {
        Throwable t = e;
        String last = t.getClass().getSimpleName();
        while (t != null) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                last = t.getMessage();
            }
            if (t.getCause() == t) {
                break;
            }
            t = t.getCause();
        }
        return last;
    }

    /**
     * Standalone JAR only has {@code target/classes} on the URLClassLoader; types from
     * {@code spring-web} and other dependencies are missing → {@link LinkageError}.
     */
    private static String classpathHint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof LinkageError) {
                return " — add compile dependencies to the JVM classpath (e.g. mvn compile exec:java -Dexec.mainClass=com.asraf.jol.ProjectScanner from the host project).";
            }
        }
        return "";
    }

    private static Config parseArgs(String[] args) {
        Config config = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--class"  -> { if (i + 1 < args.length) config.className  = args[++i]; }
                case "--file"   -> { if (i + 1 < args.length) config.className  = sourceToClass(args[++i]); }
                case "--dir"    -> { if (i + 1 < args.length) config.scanDir    = args[++i]; }
                case "--output" -> { if (i + 1 < args.length) config.outputFile = args[++i]; }
                case "--output-dev" -> { if (i + 1 < args.length) config.outputDevFile = args[++i]; }
            }
        }
        return config;
    }

    // Converts "src/main/java/com/example/Foo.java" → "com.example.Foo"
    private static String sourceToClass(String filePath) {
        return filePath
                .replaceAll(".*src[/\\\\](main|test)[/\\\\]java[/\\\\]", "")
                .replace('/', '.')
                .replace('\\', '.')
                .replaceAll("\\.java$", "");
    }

    private static class Config {
        String className    = null;
        String scanDir      = null;
        String outputFile   = "memory-report.xlsx";
        String outputDevFile = null;
    }
}

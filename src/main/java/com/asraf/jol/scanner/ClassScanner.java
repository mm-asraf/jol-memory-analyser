package com.asraf.jol.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ClassScanner {

    private static final Logger log = LoggerFactory.getLogger(ClassScanner.class);

    /**
     * Walks {@code classesDir}, loads every concrete class file, and returns
     * the list. Anonymous and inner classes (containing {@code $}) are skipped.
     */
    public List<Class<?>> scan(Path classesDir) throws IOException {
        // Parent loader ensures JOL, SLF4J, etc. are still resolvable from the scanned classes.
        URLClassLoader loader = new URLClassLoader(
                new URL[]{classesDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        );

        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesDir)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .forEach(p -> {
                    String name = toClassName(classesDir, p);
                    try {
                        Class<?> clazz = loader.loadClass(name);
                        if (!clazz.isInterface() && !clazz.isAnnotation()
                                && !clazz.isEnum() && !clazz.isRecord()) {
                            classes.add(clazz);
                        }
                    } catch (Throwable e) {
                        // LinkageError (e.g. NoClassDefFoundError) if a class references missing deps
                        log.warn("Skipping {} — {}", name, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    }
                });
        }
        return classes;
    }

    /**
     * Loads a single class by fully-qualified name using the current thread's classloader.
     */
    public Class<?> loadSingle(String className) throws ClassNotFoundException {
        return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Loads a class from {@code projectRoot/target/classes} and {@code projectRoot/target/test-classes}.
     * Required when running from the standalone uber-jar: test types are not inside the JAR, and the
     * default class loader cannot see {@code target/test-classes}.
     */
    public Class<?> loadSingleFromProject(String className, Path projectRoot) throws ClassNotFoundException {
        Path mainOut = projectRoot.resolve("target/classes");
        Path testOut = projectRoot.resolve("target/test-classes");
        List<URL> urls = new ArrayList<>(2);
        try {
            if (Files.isDirectory(mainOut)) {
                urls.add(mainOut.toUri().toURL());
            }
            if (Files.isDirectory(testOut)) {
                urls.add(testOut.toUri().toURL());
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        if (urls.isEmpty()) {
            return loadSingle(className);
        }
        URLClassLoader loader = new URLClassLoader(
                urls.toArray(URL[]::new),
                Thread.currentThread().getContextClassLoader()
        );
        return Class.forName(className, false, loader);
    }

    private static String toClassName(Path base, Path classFile) {
        return base.relativize(classFile)
                   .toString()
                   .replace(File.separatorChar, '.')
                   .replace('/', '.')
                   .replaceAll("\\.class$", "");
    }
}

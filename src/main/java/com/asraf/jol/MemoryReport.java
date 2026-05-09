package com.asraf.jol;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.*;

public class MemoryReport {

    private static final Logger log = LoggerFactory.getLogger(MemoryReport.class);

    static final int N = 1000;

    record Measurement(String label, String category,
                       long shallow, long retained, int elements) {

        double bytesPerElement() {
            return elements > 0 ? (double) retained / elements : retained;
        }

        double overhead() {
            return (double) retained / Math.max(1, elements * 4L);
        }
    }

    public static void main(String[] args) {
        log.info("{}", VM.current().details());

        List<Measurement> results = new ArrayList<>();

        // Primitive arrays
        results.add(measure("int[1000]",    "arrays", new int[N],    N));
        results.add(measure("long[1000]",   "arrays", new long[N],   N));
        results.add(measure("double[1000]", "arrays", new double[N], N));

        // Boxed arrays
        Integer[] boxed = new Integer[N];
        Arrays.fill(boxed, 999);
        results.add(measure("Integer[1000]", "boxed", boxed, N));

        // Lists
        results.add(measure("ArrayList<Int>",  "lists", filledArrayList(N),  N));
        results.add(measure("LinkedList<Int>", "lists", filledLinkedList(N), N));

        // Maps
        results.add(measure("HashMap<I,I>",   "maps", filledHashMap(N),       N));
        results.add(measure("TreeMap<I,I>",   "maps", filledTreeMap(N),       N));
        results.add(measure("LinkedHashMap",  "maps", filledLinkedHashMap(N), N));

        // Sets
        results.add(measure("HashSet<Int>", "sets", filledHashSet(N), N));
        results.add(measure("TreeSet<Int>", "sets", filledTreeSet(N), N));

        printReport(results);
        printStringReport();
        printFindings(results);
    }

    static void printReport(List<Measurement> results) {
        log.info("\n" + "═".repeat(80));
        log.info("  MEMORY LAYOUT REPORT — {} elements per structure", N);
        log.info("═".repeat(80));
        log.info(String.format("%-22s %-8s %10s %10s %13s %9s",
                "Structure", "Category", "Shallow", "Retained", "Bytes/elem", "Overhead"));
        log.info("─".repeat(80));

        Map<String, List<Measurement>> grouped = results.stream()
                .collect(Collectors.groupingBy(
                        Measurement::category, LinkedHashMap::new, Collectors.toList()));

        grouped.forEach((cat, ms) -> {
            log.info("  -- {} --", cat);
            ms.forEach(m -> log.info(String.format(
                    "  %-20s %-8s %,8d B  %,8d B  %,10.1f B  %6.1fx",
                    m.label(), m.category(),
                    m.shallow(), m.retained(),
                    m.bytesPerElement(), m.overhead())));
        });

        log.info("─".repeat(80));
    }

    static void printStringReport() {
        log.info("\n" + "═".repeat(60));
        log.info("  STRING MEMORY REPORT");
        log.info("═".repeat(60));
        log.info(String.format("%-28s %6s %10s %10s", "String", "chars", "Shallow", "Retained"));
        log.info("─".repeat(60));

        measureAndPrintString("\"\"  (empty)",       "");
        measureAndPrintString("\"hi\" (2 chars)",    "hi");
        measureAndPrintString("\"hello\" (5 chars)", "hello");
        measureAndPrintString("\"héllo\" (unicode)", "héllo");
        measureAndPrintString("50 ASCII chars",      "a".repeat(50));
        measureAndPrintString("50 unicode chars",    "é".repeat(50));
        measureAndPrintString("100 ASCII chars",     "a".repeat(100));

        log.info("─".repeat(60));
    }

    static void printFindings(List<Measurement> results) {
        log.info("\n" + "═".repeat(80));
        log.info("  KEY FINDINGS");
        log.info("═".repeat(80));

        Measurement baseline = results.stream()
                .filter(m -> m.label().startsWith("int["))
                .findFirst().orElseThrow();

        log.info(String.format("  Baseline: %-20s → %,d bytes (%,.1f bytes/element)%n",
                baseline.label(), baseline.retained(), baseline.bytesPerElement()));

        log.info("  Most expensive vs int[] baseline (descending):");
        results.stream()
                .sorted(Comparator.comparingDouble(Measurement::overhead).reversed())
                .forEach(m -> {
                    String bar = "█".repeat(Math.min(40, (int) (m.overhead() * 1.5)));
                    log.info(String.format("  %6.1fx  %-22s %s", m.overhead(), m.label(), bar));
                });

        log.info("");
        log.info("  RULES TO REMEMBER:");
        log.info("  " + "─".repeat(39));

        results.stream().filter(m -> m.label().equals("LinkedList<Int>")).findFirst()
                .ifPresent(ll -> log.info(String.format(
                        "  LinkedList costs %.1fx more than int[] — almost never worth it",
                        ll.overhead())));

        results.stream().filter(m -> m.label().equals("HashMap<I,I>")).findFirst()
                .ifPresent(hm -> log.info(String.format(
                        "  HashMap costs %.1fx more than int[] — justify every one you create",
                        hm.overhead())));

        results.stream().filter(m -> m.label().equals("ArrayList<Int>")).findFirst()
                .ifPresent(al -> log.info(String.format(
                        "  ArrayList costs %.1fx more than int[] — use int[] in hot paths",
                        al.overhead())));

        log.info("");
        log.info("  1M element cost estimates:");
        log.info("  " + "─".repeat(39));
        results.forEach(m -> log.info(String.format(
                "  %-22s → ~%,.0f MB for 1M elements",
                m.label(), m.bytesPerElement() * 1_000_000 / (1024.0 * 1024.0))));

        log.info("\n" + "═".repeat(80));
    }

    static Measurement measure(String label, String category, Object obj, int elements) {
        long shallow  = ClassLayout.parseInstance(obj).instanceSize();
        long retained = GraphLayout.parseInstance(obj).totalSize();
        return new Measurement(label, category, shallow, retained, elements);
    }

    static void measureAndPrintString(String label, String s) {
        long shallow  = ClassLayout.parseInstance(s).instanceSize();
        long retained = GraphLayout.parseInstance(s).totalSize();
        log.info(String.format("  %-28s %6d %,8d B  %,8d B", label, s.length(), shallow, retained));
    }

    static List<Integer> filledArrayList(int n) {
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(i + 200);
        return l;
    }

    static List<Integer> filledLinkedList(int n) {
        List<Integer> l = new LinkedList<>();
        for (int i = 0; i < n; i++) l.add(i + 200);
        return l;
    }

    static Map<Integer, Integer> filledHashMap(int n) {
        Map<Integer, Integer> m = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) m.put(i + 200, i);
        return m;
    }

    static Map<Integer, Integer> filledTreeMap(int n) {
        Map<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < n; i++) m.put(i + 200, i);
        return m;
    }

    static Map<Integer, Integer> filledLinkedHashMap(int n) {
        Map<Integer, Integer> m = new LinkedHashMap<>(n * 2);
        for (int i = 0; i < n; i++) m.put(i + 200, i);
        return m;
    }

    static Set<Integer> filledHashSet(int n) {
        Set<Integer> s = new HashSet<>(n * 2);
        for (int i = 0; i < n; i++) s.add(i + 200);
        return s;
    }

    static Set<Integer> filledTreeSet(int n) {
        Set<Integer> s = new TreeSet<>();
        for (int i = 0; i < n; i++) s.add(i + 200);
        return s;
    }
}

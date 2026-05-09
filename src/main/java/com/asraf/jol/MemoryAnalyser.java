package com.asraf.jol;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MemoryAnalyser {

    private static final Logger log = LoggerFactory.getLogger(MemoryAnalyser.class);

    static final int N = 100;

    public static void main(String[] args) {
        printHeader("PRIMITIVE ARRAYS");
        measure("int[100]",           createIntArray());
        measure("long[100]",          createLongArray());
        measure("double[100]",        createDoubleArray());
        measure("Integer[100]",       createBoxedArray());

        printHeader("LISTS");
        measure("ArrayList<Integer>",  createArrayList());
        measure("LinkedList<Integer>", createLinkedList());

        printHeader("MAPS");
        measure("HashMap<Int,Int>",    createHashMap());
        measure("TreeMap<Int,Int>",    createTreeMap());
        measure("LinkedHashMap",       createLinkedHashMap());

        printHeader("SETS");
        measure("HashSet<Integer>",    createHashSet());
        measure("TreeSet<Integer>",    createTreeSet());

        printHeader("STRINGS");
        measureString("ASCII only  : \"hello\"",  "hello");
        measureString("Unicode     : \"héllo\"",  "héllo");
        measureString("Empty       : \"\"",        "");
        measureString("Long ASCII  : 50 chars",    "a".repeat(50));

        printHeader("FIELD REORDERING & PADDING");
        fieldReorderingDemo();

        printHeader("NESTED OBJECT GRAPH");
        nestedGraphDemo();

        printHeader("BYTES PER ELEMENT AT SCALE");
        scaleDemo();

        printHeader("ARRAYLIST vs LINKEDLIST PER ELEMENT COST");
        perElementCostDemo();
    }

    static void measure(String label, Object obj) {
        long shallow  = ClassLayout.parseInstance(obj).instanceSize();
        long retained = GraphLayout.parseInstance(obj).totalSize();
        double overhead = (double) retained / Math.max(1, N * 4L);
        log.info(String.format("%-30s  shallow=%5d B  retained=%6d B  overhead=%.1fx",
                label, shallow, retained, overhead));
    }

    static void measureString(String label, String s) {
        long shallow  = ClassLayout.parseInstance(s).instanceSize();
        long retained = GraphLayout.parseInstance(s).totalSize();
        log.info(String.format("%-30s  chars=%3d  shallow=%3d B  retained=%3d B",
                label, s.length(), shallow, retained));
    }

    static void printHeader(String title) {
        log.info("");
        log.info("─".repeat(60));
        log.info("  {}", title);
        log.info("─".repeat(60));
    }

    static int[]    createIntArray()    { return new int[N]; }
    static long[]   createLongArray()   { return new long[N]; }
    static double[] createDoubleArray() { return new double[N]; }

    static Integer[] createBoxedArray() {
        Integer[] a = new Integer[N];
        Arrays.fill(a, 999); // outside Integer cache (-128..127), so each element is a distinct object
        return a;
    }

    static List<Integer> createArrayList() {
        List<Integer> l = new ArrayList<>(N);
        for (int i = 0; i < N; i++) l.add(i + 200);
        return l;
    }

    static List<Integer> createLinkedList() {
        List<Integer> l = new LinkedList<>();
        for (int i = 0; i < N; i++) l.add(i + 200);
        return l;
    }

    static Map<Integer, Integer> createHashMap() {
        Map<Integer, Integer> m = new HashMap<>(N * 2);
        for (int i = 0; i < N; i++) m.put(i + 200, i);
        return m;
    }

    static Map<Integer, Integer> createTreeMap() {
        Map<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < N; i++) m.put(i + 200, i);
        return m;
    }

    static Map<Integer, Integer> createLinkedHashMap() {
        Map<Integer, Integer> m = new LinkedHashMap<>(N * 2);
        for (int i = 0; i < N; i++) m.put(i + 200, i);
        return m;
    }

    static Set<Integer> createHashSet() {
        Set<Integer> s = new HashSet<>(N * 2);
        for (int i = 0; i < N; i++) s.add(i + 200);
        return s;
    }

    static Set<Integer> createTreeSet() {
        Set<Integer> s = new TreeSet<>();
        for (int i = 0; i < N; i++) s.add(i + 200);
        return s;
    }

    static void fieldReorderingDemo() {
        // JVM sorts fields by alignment requirement: long(8) > int(4) > short(2) > byte/bool(1) > refs.
        // BadOrder and GoodOrder have different source ordering but end up with identical layouts.
        log.info("{}", ClassLayout.parseClass(BadOrder.class).toPrintable());
        log.info("{}", ClassLayout.parseClass(GoodOrder.class).toPrintable());
    }

    static void nestedGraphDemo() {
        Map<String, List<String>> data = new HashMap<>();
        data.put("users",    List.of("alice", "bob", "charlie"));
        data.put("roles",    List.of("admin", "viewer"));
        data.put("features", List.of("search", "export", "audit"));

        long shallow  = ClassLayout.parseInstance(data).instanceSize();
        long retained = GraphLayout.parseInstance(data).totalSize();

        log.info("HashMap<String,List<String>> with 3 keys:");
        log.info(String.format("  Shallow  (HashMap object only) : %,d bytes", shallow));
        log.info(String.format("  Retained (full object graph)   : %,d bytes", retained));
        log.info("");
        log.info("{}", GraphLayout.parseInstance(data).toFootprint());
    }

    static void scaleDemo() {
        // Fixed header overhead amortises as N grows — bytes/element converges toward the true per-element cost.
        log.info(String.format("%-20s  %10s  %15s", "Structure", "Retained", "Bytes/element"));
        log.info("─".repeat(50));

        for (int n : new int[]{10, 100, 1_000, 10_000}) {
            List<Integer> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(i + 200);
            long retained = GraphLayout.parseInstance(list).totalSize();
            log.info(String.format("ArrayList(%6d elems)  %,8d B  %,.1f bytes/elem",
                    n, retained, (double) retained / n));
        }

        log.info("");

        for (int n : new int[]{10, 100, 1_000, 10_000}) {
            Object arr = new int[n]; // typed as Object to avoid vararg ambiguity
            long retained = GraphLayout.parseInstance(arr).totalSize();
            log.info(String.format("int[]    (%6d elems)  %,8d B  %,.1f bytes/elem",
                    n, retained, (double) retained / n));
        }
    }

    static void perElementCostDemo() {
        int n = 1_000;

        List<Integer> arrayList  = new ArrayList<>(n);
        List<Integer> linkedList = new LinkedList<>();
        Object        intArray   = new int[n];

        for (int i = 0; i < n; i++) {
            arrayList.add(i + 200);
            linkedList.add(i + 200);
        }

        long alSize  = GraphLayout.parseInstance(arrayList).totalSize();
        long llSize  = GraphLayout.parseInstance(linkedList).totalSize();
        long arrSize = GraphLayout.parseInstance(intArray).totalSize();

        log.info(String.format("int[1000]            : %,6d bytes  → %.1f bytes/element",
                arrSize,  (double) arrSize  / n));
        log.info(String.format("ArrayList<Int>(1000) : %,6d bytes  → %.1f bytes/element",
                alSize,   (double) alSize   / n));
        log.info(String.format("LinkedList<Int>(1000): %,6d bytes  → %.1f bytes/element",
                llSize,   (double) llSize   / n));
        log.info(String.format("%nLinkedList costs %.1fx more than int[] per element",
                (double) llSize / arrSize));
    }
}

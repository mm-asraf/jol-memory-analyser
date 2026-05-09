package com.asraf.jol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryReportTest {

    private static final Logger log = LoggerFactory.getLogger(MemoryReportTest.class);

    static final int N = 1000;

    static Stream<Arguments> structures() {
        return Stream.of(
                Arguments.of("int[1000]",
                        (Supplier<?>) () -> new int[N], N),
                Arguments.of("long[1000]",
                        (Supplier<?>) () -> new long[N], N),
                Arguments.of("ArrayList<Integer>",
                        (Supplier<?>) () -> {
                            List<Integer> l = new ArrayList<>(N);
                            for (int i = 0; i < N; i++) l.add(i + 200);
                            return l;
                        }, N),
                Arguments.of("LinkedList<Integer>",
                        (Supplier<?>) () -> {
                            List<Integer> l = new LinkedList<>();
                            for (int i = 0; i < N; i++) l.add(i + 200);
                            return l;
                        }, N),
                Arguments.of("HashMap<Int,Int>",
                        (Supplier<?>) () -> {
                            Map<Integer, Integer> m = new HashMap<>(N * 2);
                            for (int i = 0; i < N; i++) m.put(i + 200, i);
                            return m;
                        }, N),
                Arguments.of("HashSet<Integer>",
                        (Supplier<?>) () -> {
                            Set<Integer> s = new HashSet<>(N * 2);
                            for (int i = 0; i < N; i++) s.add(i + 200);
                            return s;
                        }, N)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("structures")
    void reportMemoryLayout(String label, Supplier<?> factory, int elements) {
        Object obj     = factory.get();
        long shallow   = ClassLayout.parseInstance(obj).instanceSize();
        long retained  = GraphLayout.parseInstance(obj).totalSize();
        double perElem = (double) retained / elements;
        double overhead = (double) retained / Math.max(1, elements * 4L);

        log.info(String.format("%-25s  shallow=%,6d B  retained=%,8d B  %.1f B/elem  %.1fx overhead",
                label, shallow, retained, perElem, overhead));

        assertTrue(shallow  > 0,  label + ": shallow must be > 0");
        assertTrue(retained > 0,  label + ": retained must be > 0");
        assertTrue(retained >= shallow, label + ": retained must be >= shallow");
        assertTrue(perElem  > 0,  label + ": bytes per element must be > 0");
    }

    @Test
    void intArrayCheaperThanArrayList() {
        Object        primitive = new int[N];
        List<Integer> boxed     = new ArrayList<>(N);
        for (int i = 0; i < N; i++) boxed.add(i + 200);

        long primitiveSize = GraphLayout.parseInstance(primitive).totalSize();
        long boxedSize     = GraphLayout.parseInstance(boxed).totalSize();

        log.info("");
        log.info(String.format("int[%d]       : %,d bytes", N, primitiveSize));
        log.info(String.format("ArrayList<%d> : %,d bytes", N, boxedSize));
        log.info(String.format("Boxing overhead : %.1fx", (double) boxedSize / primitiveSize));

        assertTrue(primitiveSize < boxedSize, "int[] must be cheaper than ArrayList<Integer>");
    }

    @Test
    void linkedListMoreExpensiveThanArrayList() {
        List<Integer> arrayList  = new ArrayList<>(N);
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < N; i++) {
            arrayList.add(i + 200);
            linkedList.add(i + 200);
        }

        long alSize = GraphLayout.parseInstance(arrayList).totalSize();
        long llSize = GraphLayout.parseInstance(linkedList).totalSize();

        log.info("");
        log.info(String.format("ArrayList(%d)  : %,d bytes", N, alSize));
        log.info(String.format("LinkedList(%d) : %,d bytes", N, llSize));
        log.info(String.format("LinkedList costs %.1fx more", (double) llSize / alSize));

        assertTrue(llSize > alSize, "LinkedList must cost more memory than ArrayList");
    }

    @Test
    void retainedAlwaysGreaterThanOrEqualToShallow() {
        List<Object> objects = List.of(
                new Object(),
                "hello",
                new ArrayList<>(List.of(1, 2, 3)),
                new HashMap<>(Map.of("a", 1, "b", 2))
        );

        objects.forEach(obj -> {
            long shallow  = ClassLayout.parseInstance(obj).instanceSize();
            long retained = GraphLayout.parseInstance(obj).totalSize();
            assertTrue(retained >= shallow,
                    obj.getClass().getSimpleName() + ": retained must always be >= shallow");
        });
    }

    @Test
    void emptyCollectionStillHasOverhead() {
        Object[] empties = {
                new ArrayList<>(),
                new LinkedList<>(),
                new HashMap<>(),
                new HashSet<>(),
                new TreeMap<>()
        };

        log.info("");
        log.info("Empty collection overhead:");
        for (Object obj : empties) {
            long retained = GraphLayout.parseInstance(obj).totalSize();
            log.info(String.format("  %-20s : %,d bytes", obj.getClass().getSimpleName(), retained));
            assertTrue(retained > 0, obj.getClass().getSimpleName() + " empty but has overhead");
        }
    }
}

package com.asraf.jol;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("{}", VM.current().details());

        log.info("=== ClassLayout: Object ===");
        log.info("{}", ClassLayout.parseInstance(new Object()).toPrintable());

        log.info("=== ClassLayout: Integer ===");
        Integer sampleInt = 999; // outside Integer cache (-128..127), so it's a distinct heap object
        log.info("{}", ClassLayout.parseInstance(sampleInt).toPrintable());

        log.info("=== ClassLayout: String ===");
        log.info("{}", ClassLayout.parseInstance("hello").toPrintable());

        log.info("=== Shallow vs Retained: String ===");
        String s = "hello world";
        long shallow  = ClassLayout.parseInstance(s).instanceSize();
        long retained = GraphLayout.parseInstance(s).totalSize();
        log.info("Shallow  (String object only) : {} bytes", shallow);
        log.info("Retained (String + byte[])    : {} bytes", retained);

        log.info("\n=== GraphLayout footprint: String ===");
        log.info("{}", GraphLayout.parseInstance(s).toFootprint());

        log.info("=== Primitive vs Boxed: 100 elements ===");
        Object        primitiveArr = new int[100]; // typed as Object so vararg isn't ambiguous
        List<Integer> boxedList    = new ArrayList<>();
        for (int i = 0; i < 100; i++) boxedList.add(i + 200); // 200+ avoids Integer cache

        long primitiveSize = GraphLayout.parseInstance(primitiveArr).totalSize();
        long boxedSize     = GraphLayout.parseInstance(boxedList).totalSize();
        log.info("int[100]           : {} bytes", primitiveSize);
        log.info("List<Integer>(100) : {} bytes", boxedSize);
        log.info(String.format("Overhead           : %.1fx", (double) boxedSize / primitiveSize));

        log.info("\n=== Shallow vs Retained: linked nodes ===");
        Node n3 = new Node(3, null);
        Node n2 = new Node(2, n3);
        Node n1 = new Node(1, n2);

        long n1Shallow  = ClassLayout.parseInstance(n1).instanceSize();
        long n1Retained = GraphLayout.parseInstance(n1).totalSize();
        log.info("n1 shallow  (just n1)      : {} bytes", n1Shallow);
        log.info("n1 retained (n1 + n2 + n3) : {} bytes", n1Retained);
        log.info("{}", GraphLayout.parseInstance(n1).toFootprint());
    }
}

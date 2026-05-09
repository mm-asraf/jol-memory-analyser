package com.asraf.jol;

/**
 * Fields declared in programmer-intuitive order (small first).
 * The JVM reorders them by alignment requirement at runtime, so the
 * actual memory layout is identical to GoodOrder despite different
 * source-level ordering.
 */
class BadOrder {
    boolean flag;
    long    value;
    int     count;
    byte    type;
}

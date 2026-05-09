package com.asraf.jol;

/**
 * Fields declared largest-to-smallest (long → int → bool/byte).
 * The JVM's field reordering produces an identical layout to BadOrder,
 * showing that source order does not determine memory footprint.
 */
class GoodOrder {
    long    value;
    int     count;
    boolean flag;
    byte    type;
}

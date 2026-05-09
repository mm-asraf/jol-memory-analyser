package com.asraf.jol.scanner;

import org.openjdk.jol.info.ClassLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class LayoutAnalyser {

    private static final Set<Class<?>> BOXED_TYPES = Set.of(
            Boolean.class, Byte.class, Character.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class
    );

    public LayoutAnalysis analyse(Class<?> clazz) {
        ClassLayout layout = ClassLayout.parseClass(clazz);

        // JOL 0.17 computes gap entries on-the-fly inside toPrintable() and does not
        // store them in fields(). Parse the rendered layout to extract gap sizes.
        long paddingBytes = extractPaddingBytes(layout.toPrintable());

        List<Field> instanceFields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .collect(Collectors.toList());

        long fieldBytes = instanceFields.stream()
                .mapToLong(f -> typeSize(f.getType()))
                .sum();

        long instanceSize = layout.instanceSize();

        List<String> boxedFieldNames = instanceFields.stream()
                .filter(f -> BOXED_TYPES.contains(f.getType()))
                .map(f -> f.getName() + ": " + f.getType().getSimpleName())
                .collect(Collectors.toList());

        long avoidablePaddingBytes = instanceFields.isEmpty()
                ? 0L
                : Math.max(0L, instanceSize - roundUp8(12L + fieldBytes));
        long potentialSavingBytes  = avoidablePaddingBytes + (long) boxedFieldNames.size() * 16;

        return new LayoutAnalysis(
                clazz.getName(),
                clazz.getSimpleName(),
                clazz.getPackageName(),
                instanceSize,
                instanceFields.size(),
                paddingBytes,
                avoidablePaddingBytes,
                potentialSavingBytes,
                boxedFieldNames,
                buildRootCause(paddingBytes, boxedFieldNames, instanceFields, fieldBytes, instanceSize),
                buildSuggestion(instanceFields, paddingBytes, boxedFieldNames, fieldBytes, instanceSize)
        );
    }

    /**
     * Parses JOL's toPrintable() output for lines containing "gap" or "loss",
     * which mark alignment/padding bytes, and sums their sizes.
     *
     * Example line formats:
     *   " 26   6         (object alignment gap)    "
     *   " 15   1         (alignment/padding gap)   "
     *
     * The second whitespace-delimited token on each such line is the byte count.
     */
    private static long extractPaddingBytes(String printable) {
        long total = 0;
        for (String line : printable.split("\n")) {
            if (line.contains("gap") || line.contains("loss")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        total += Long.parseLong(parts[1]);
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return total;
    }

    private String buildRootCause(long paddingBytes, List<String> boxedFields,
                                  List<Field> instanceFields, long fieldBytes, long instanceSize) {
        List<String> issues = new ArrayList<>();

        if (paddingBytes > 0 && !instanceFields.isEmpty()) {
            // Compute the theoretical minimum size (header + fields, rounded to 8-byte boundary).
            // If actual size equals minimum, all padding is mandatory object-alignment rounding.
            // If actual size is larger, there are internal field-alignment gaps that may be avoidable.
            long minSize = roundUp8(12L + fieldBytes);
            long avoidableBytes = instanceSize - minSize;
            if (avoidableBytes > 0) {
                issues.add(avoidableBytes + " bytes of avoidable field-alignment gaps");
            } else {
                issues.add(paddingBytes + " bytes mandatory object-alignment padding (unavoidable)");
            }
        }

        if (!boxedFields.isEmpty()) {
            String label = boxedFields.size() == 1 ? "boxed field" : "boxed fields";
            issues.add(label + ": " + String.join(", ", boxedFields));
        }

        return issues.isEmpty() ? "None — layout is optimal" : String.join("; ", issues);
    }

    private String buildSuggestion(List<Field> fields, long paddingBytes, List<String> boxedFields,
                                   long fieldBytes, long instanceSize) {
        List<String> suggestions = new ArrayList<>();

        // Only suggest reordering when there are actual internal gaps (not just trailing alignment)
        long minSize = roundUp8(12L + fieldBytes);
        long avoidableBytes = instanceSize - minSize;
        if (avoidableBytes > 0 && !fields.isEmpty()) {
            String optimal = fields.stream()
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .sorted(Comparator.comparingLong((Field f) -> typeSize(f.getType())).reversed())
                    .map(f -> f.getType().getSimpleName() + " " + f.getName())
                    .collect(Collectors.joining(", "));
            suggestions.add("Declare fields largest-to-smallest to eliminate internal gaps: " + optimal);
        }

        if (!boxedFields.isEmpty()) {
            suggestions.add("Replace boxed fields with primitives to save ~16 B each: "
                    + String.join(", ", boxedFields));
        }

        return suggestions.isEmpty()
                ? "No changes needed — layout is already optimal"
                : String.join(". ", suggestions);
    }

    private static long roundUp8(long n) {
        return (n + 7L) & ~7L;
    }

    // Returns the natural alignment size of a primitive type (references count as 4 with compressed oops).
    private static long typeSize(Class<?> type) {
        if (type == long.class    || type == double.class)  return 8;
        if (type == int.class     || type == float.class)   return 4;
        if (type == char.class    || type == short.class)   return 2;
        if (type == boolean.class || type == byte.class)    return 1;
        return 4; // reference (compressed oops)
    }
}

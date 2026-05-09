package com.asraf.jol.scanner;

import java.util.List;

public record LayoutAnalysis(
        String       className,
        String       simpleClassName,
        String       packageName,
        long         instanceSize,
        int          fieldCount,
        long         paddingBytes,
        long         avoidablePaddingBytes,
        long         potentialSavingBytes,
        List<String> boxedFieldNames,
        String       rootCause,
        String       suggestion
) {}

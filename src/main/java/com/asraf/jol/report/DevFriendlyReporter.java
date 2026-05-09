package com.asraf.jol.report;

import com.asraf.jol.scanner.LayoutAnalysis;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DevFriendlyReporter {

    private static final Logger log = LoggerFactory.getLogger(DevFriendlyReporter.class);

    private static final byte[] NAVY   = {(byte) 31,  (byte) 73,  (byte) 125};
    private static final byte[] GREEN  = {(byte) 198, (byte) 239, (byte) 206};
    private static final byte[] YELLOW = {(byte) 255, (byte) 235, (byte) 156};
    private static final byte[] RED    = {(byte) 255, (byte) 199, (byte) 206};
    private static final byte[] GREY   = {(byte) 242, (byte) 242, (byte) 242};
    private static final byte[] WHITE  = {(byte) 255, (byte) 255, (byte) 255};

    private static final String[] ANALYSIS_HEADERS = {
        "Status", "Class Name", "Package",
        "Object Size (bytes)", "Object Size (MB)",
        "Wasted Bytes", "Potential Saving (bytes)", "Potential Saving (MB)",
        "Boxed Fields (use primitives)", "Finding", "Recommendation"
    };

    public void write(List<LayoutAnalysis> analyses, Path outputPath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            writeSummarySheet(wb, analyses);
            writeAnalysisSheet(wb, analyses);
            writeLegendSheet(wb);

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                wb.write(out);
            }
        }
        log.info("Developer workbook written: {}", outputPath.toAbsolutePath());
    }

    private void writeSummarySheet(XSSFWorkbook wb, List<LayoutAnalysis> analyses) {
        XSSFSheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 10000);
        sheet.setColumnWidth(1, 10000);

        long optimal     = analyses.stream().filter(a -> "Optimal".equals(status(a))).count();
        long review      = analyses.stream().filter(a -> "Review".equals(status(a))).count();
        long action      = analyses.stream().filter(a -> "Action Needed".equals(status(a))).count();
        long totalBytes  = analyses.stream().mapToLong(LayoutAnalysis::instanceSize).sum();
        long totalSaving = analyses.stream().mapToLong(LayoutAnalysis::potentialSavingBytes).sum();

        XSSFCellStyle titleStyle  = buildStyle(wb, NAVY,   true,  14, WHITE, null);
        XSSFCellStyle labelStyle  = buildStyle(wb, GREY,   true,  11, null,  null);
        XSSFCellStyle valueStyle  = buildStyle(wb, WHITE,  false, 11, null,  null);
        XSSFCellStyle greenStyle  = buildStyle(wb, GREEN,  false, 11, null,  null);
        XSSFCellStyle yellowStyle = buildStyle(wb, YELLOW, false, 11, null,  null);
        XSSFCellStyle redStyle    = buildStyle(wb, RED,    false, 11, null,  null);

        int r = 0;

        addMergedCell(sheet, r++, "Memory layout analysis — summary", titleStyle);
        r++;

        addKV(sheet, r++, "Classes scanned",        String.valueOf(analyses.size()),                         labelStyle, valueStyle);
        addKV(sheet, r++, "Optimal",                String.valueOf(optimal),                                 labelStyle, greenStyle);
        addKV(sheet, r++, "Review",                 String.valueOf(review),                                  labelStyle, yellowStyle);
        addKV(sheet, r++, "Action Needed",          String.valueOf(action),                                  labelStyle, redStyle);
        r++;
        addKV(sheet, r++, "Total object size",      totalBytes  + " bytes  /  " + toMbStr(totalBytes)  + " MB", labelStyle, valueStyle);
        addKV(sheet, r++, "Total potential saving", totalSaving + " bytes  /  " + toMbStr(totalSaving) + " MB", labelStyle, valueStyle);
        r++;
        addKV(sheet, r++, "Next step", "Open the Analysis sheet for per-class details.", labelStyle, valueStyle);
        addKV(sheet, r,   "Reference", "See the Legend sheet for column definitions and glossary terms.", labelStyle, valueStyle);
    }

    private void writeAnalysisSheet(XSSFWorkbook wb, List<LayoutAnalysis> analyses) {
        XSSFSheet sheet = wb.createSheet("Analysis");
        sheet.createFreezePane(0, 1);

        XSSFCellStyle headerStyle = buildStyle(wb, NAVY,   true,  11, WHITE, null);
        XSSFCellStyle greenStyle  = buildStyle(wb, GREEN,  false, 10, null,  null);
        XSSFCellStyle yellowStyle = buildStyle(wb, YELLOW, false, 10, null,  null);
        XSSFCellStyle redStyle    = buildStyle(wb, RED,    false, 10, null,  null);

        String mbFormat = "0.00000000";
        XSSFCellStyle greenMb  = cloneWithFormat(wb, greenStyle,  mbFormat);
        XSSFCellStyle yellowMb = cloneWithFormat(wb, yellowStyle, mbFormat);
        XSSFCellStyle redMb    = cloneWithFormat(wb, redStyle,    mbFormat);

        Row hdrRow = sheet.createRow(0);
        for (int i = 0; i < ANALYSIS_HEADERS.length; i++) {
            Cell c = hdrRow.createCell(i);
            c.setCellValue(ANALYSIS_HEADERS[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (LayoutAnalysis a : analyses) {
            String s = status(a);
            XSSFCellStyle base = switch (s) {
                case "Action Needed" -> redStyle;
                case "Review"        -> yellowStyle;
                default              -> greenStyle;
            };
            XSSFCellStyle mb = switch (s) {
                case "Action Needed" -> redMb;
                case "Review"        -> yellowMb;
                default              -> greenMb;
            };

            Row row = sheet.createRow(rowIdx++);
            setStr(row, 0,  s,                                       base);
            setStr(row, 1,  a.simpleClassName(),                     base);
            setStr(row, 2,  a.packageName(),                         base);
            setNum(row, 3,  a.instanceSize(),                        base);
            setDbl(row, 4,  toMbDouble(a.instanceSize()),            mb);
            setNum(row, 5,  a.paddingBytes(),                        base);
            setNum(row, 6,  a.potentialSavingBytes(),                base);
            setDbl(row, 7,  toMbDouble(a.potentialSavingBytes()),    mb);
            setStr(row, 8,  String.join(", ", a.boxedFieldNames()),  base);
            setStr(row, 9,  findingDescription(a),                  base);
            setStr(row, 10, recommendationDescription(a),           base);
        }

        for (int i = 0; i < ANALYSIS_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 20480) sheet.setColumnWidth(i, 20480);
        }
    }

    private void writeLegendSheet(XSSFWorkbook wb) {
        XSSFSheet sheet = wb.createSheet("Legend");
        sheet.setColumnWidth(0, 11000);
        sheet.setColumnWidth(1, 20000);

        XSSFCellStyle titleStyle   = buildStyle(wb, NAVY,   true,  13, WHITE, null);
        XSSFCellStyle sectionStyle = buildStyle(wb, GREY,   true,  11, null,  null);
        XSSFCellStyle keyStyle     = buildStyle(wb, WHITE,  true,  10, null,  null);
        XSSFCellStyle valStyle     = buildStyle(wb, WHITE,  false, 10, null,  null);
        XSSFCellStyle greenStyle   = buildStyle(wb, GREEN,  false, 10, null,  null);
        XSSFCellStyle yellowStyle  = buildStyle(wb, YELLOW, false, 10, null,  null);
        XSSFCellStyle redStyle     = buildStyle(wb, RED,    false, 10, null,  null);

        int r = 0;

        addMergedCell(sheet, r++, "Legend and glossary", titleStyle);
        r++;

        addMergedCell(sheet, r++, "Status colours", sectionStyle);
        addRow2(sheet, r++, "Status",        "Description",                                                           keyStyle,    keyStyle);
        addRow2(sheet, r++, "Optimal",       "No avoidable field gaps and no boxed instance fields detected.",         greenStyle,  valStyle);
        addRow2(sheet, r++, "Review",        "Either avoidable alignment gaps between fields, or boxed fields.",        yellowStyle, valStyle);
        addRow2(sheet, r++, "Action Needed", "Both avoidable alignment gaps and boxed instance fields are present.",   redStyle,    valStyle);
        r++;

        addMergedCell(sheet, r++, "Column reference", sectionStyle);
        addRow2(sheet, r++, "Column",                       "Description",                                                             keyStyle, keyStyle);
        addRow2(sheet, r++, "Status",                       "Severity for this class: Optimal, Review, or Action needed.",             keyStyle, valStyle);
        addRow2(sheet, r++, "Class name",                   "Simple name of the analysed type.",                                     keyStyle, valStyle);
        addRow2(sheet, r++, "Package",                      "Java package of the analysed type.",                                      keyStyle, valStyle);
        addRow2(sheet, r++, "Object size (bytes)",          "Shallow footprint of one instance (header, fields, internal padding).", keyStyle, valStyle);
        addRow2(sheet, r++, "Object size (MB)",             "Same value in mebibytes; multiply by instance count for rough totals.",   keyStyle, valStyle);
        addRow2(sheet, r++, "Wasted bytes",                 "Padding bytes reported by the layout tool (includes mandatory tail alignment).", keyStyle, valStyle);
        addRow2(sheet, r++, "Potential saving (bytes)",     "Estimated per-instance reduction if recommendations are applied.",          keyStyle, valStyle);
        addRow2(sheet, r++, "Potential saving (MB)",        "Same estimate in mebibytes.",                                             keyStyle, valStyle);
        addRow2(sheet, r++, "Boxed fields (use primitives)","Wrapper-typed fields (e.g. Integer) that could be primitive.",            keyStyle, valStyle);
        addRow2(sheet, r++, "Finding",                      "Explanation of the observed layout (see glossary).",                      keyStyle, valStyle);
        addRow2(sheet, r++, "Recommendation",               "Suggested change, or none if already optimal.",                           keyStyle, valStyle);
        r++;

        addMergedCell(sheet, r++, "Glossary", sectionStyle);
        addRow2(sheet, r++, "Term",              "Definition",                                                              keyStyle, keyStyle);
        addRow2(sheet, r++, "Object header",     "Fixed prefix on each heap object used by the JVM (identity hash, GC, locking metadata). Typical size is 12 bytes on 64-bit HotSpot with compressed class pointers.", keyStyle, valStyle);
        addRow2(sheet, r++, "Alignment padding", "Bytes inserted so fields and total object size satisfy alignment rules; the object size is typically rounded up to a multiple of 8 bytes.", keyStyle, valStyle);
        addRow2(sheet, r++, "Boxed type",        "Reference type that wraps a primitive (for example Integer for int). Each distinct boxed value may allocate a separate object on the heap.", keyStyle, valStyle);
        addRow2(sheet, r++, "Compressed OOPs", "Ordinary object pointers stored as 32-bit references when the heap is below the compressed-OOP threshold (often around 32 GB), reducing reference footprint.", keyStyle, valStyle);
        addRow2(sheet, r++, "Shallow size",    "Memory for this object alone: header, fields, and padding. Excludes objects referenced by fields.", keyStyle, valStyle);
        addRow2(sheet, r,   "Retained size",   "Total memory that would be reclaimed if this object were unreachable, including referenced objects only reachable through it.", keyStyle, valStyle);
    }

    private String status(LayoutAnalysis a) {
        boolean hasAvoidable = a.avoidablePaddingBytes() > 0;
        boolean hasBoxed     = !a.boxedFieldNames().isEmpty();
        if (hasAvoidable && hasBoxed) return "Action Needed";
        if (hasAvoidable || hasBoxed) return "Review";
        return "Optimal";
    }

    private String findingDescription(LayoutAnalysis a) {
        List<String> parts = new ArrayList<>();
        if (a.avoidablePaddingBytes() > 0) {
            parts.add("Avoidable alignment gaps between fields (" + a.avoidablePaddingBytes()
                    + " bytes); field layout may be improved.");
        } else if (a.paddingBytes() > 0) {
            parts.add("Padding matches normal object alignment (multiple of 8 bytes); no further reduction expected for this shape.");
        }
        if (!a.boxedFieldNames().isEmpty()) {
            parts.add("One or more fields use wrapper types (e.g. Integer, Long); each may allocate a separate heap object.");
        }
        return parts.isEmpty()
                ? "No layout issues detected under this analysis."
                : String.join(" ", parts);
    }

    private String recommendationDescription(LayoutAnalysis a) {
        List<String> parts = new ArrayList<>();
        if (a.avoidablePaddingBytes() > 0) {
            parts.add("Declare fields in descending alignment width (long/double, then int/float, then short/char, then byte/boolean, then references) to reduce internal gaps.");
        }
        if (!a.boxedFieldNames().isEmpty()) {
            parts.add("Prefer primitive fields where null is not required: "
                    + String.join(", ", a.boxedFieldNames())
                    + ". Typical saving on the order of 16 bytes per boxed field per instance.");
        }
        return parts.isEmpty() ? "No change required." : String.join(" ", parts);
    }

    private XSSFCellStyle buildStyle(XSSFWorkbook wb, byte[] bg, boolean bold, int fontSize,
                                     byte[] fontColor, String numFormat) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(bg, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        if (fontColor != null) font.setColor(new XSSFColor(fontColor, null));
        style.setFont(font);
        if (numFormat != null) style.setDataFormat(wb.createDataFormat().getFormat(numFormat));
        return style;
    }

    private XSSFCellStyle cloneWithFormat(XSSFWorkbook wb, XSSFCellStyle base, String numFormat) {
        XSSFCellStyle clone = wb.createCellStyle();
        clone.cloneStyleFrom(base);
        clone.setDataFormat(wb.createDataFormat().getFormat(numFormat));
        return clone;
    }

    private void addMergedCell(XSSFSheet sheet, int r, String value, XSSFCellStyle style) {
        Row row = sheet.createRow(r);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
    }

    private void addKV(XSSFSheet sheet, int r, String key, String value,
                       XSSFCellStyle keyStyle, XSSFCellStyle valStyle) {
        Row row = sheet.createRow(r);
        Cell kc = row.createCell(0); kc.setCellValue(key);   kc.setCellStyle(keyStyle);
        Cell vc = row.createCell(1); vc.setCellValue(value); vc.setCellStyle(valStyle);
    }

    private void addRow2(XSSFSheet sheet, int r, String col0, String col1,
                         XSSFCellStyle s0, XSSFCellStyle s1) {
        Row row = sheet.createRow(r);
        Cell c0 = row.createCell(0); c0.setCellValue(col0); c0.setCellStyle(s0);
        Cell c1 = row.createCell(1); c1.setCellValue(col1); c1.setCellStyle(s1);
    }

    private void setStr(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setNum(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setDbl(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static double toMbDouble(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static String toMbStr(long bytes) {
        return String.format("%.6f", bytes / (1024.0 * 1024.0));
    }
}

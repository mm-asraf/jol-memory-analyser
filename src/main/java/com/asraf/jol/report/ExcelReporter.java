package com.asraf.jol.report;

import com.asraf.jol.scanner.LayoutAnalysis;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelReporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelReporter.class);

    // Row background colors (RGB)
    private static final byte[] BLUE   = {(byte) 68,  (byte) 114, (byte) 196}; // header
    private static final byte[] GREEN  = {(byte) 198, (byte) 239, (byte) 206}; // optimal
    private static final byte[] YELLOW = {(byte) 255, (byte) 235, (byte) 156}; // minor issue
    private static final byte[] RED    = {(byte) 255, (byte) 199, (byte) 206}; // padding + boxed

    private static final String[] HEADERS = {
        "Class Name", "Full Class Name", "Package",
        "Instance Size (B)", "Fields", "Padding Waste (B)",
        "Boxed Fields", "Root Cause", "Enhancement Suggestion"
    };

    public void write(List<LayoutAnalysis> analyses, Path outputPath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Memory Analysis");
            sheet.createFreezePane(0, 1);

            XSSFCellStyle headerStyle = buildHeaderStyle(wb);
            XSSFCellStyle greenStyle  = buildRowStyle(wb, GREEN);
            XSSFCellStyle yellowStyle = buildRowStyle(wb, YELLOW);
            XSSFCellStyle redStyle    = buildRowStyle(wb, RED);

            writeHeaderRow(sheet, headerStyle);

            int rowIdx = 1;
            for (LayoutAnalysis a : analyses) {
                XSSFCellStyle style = pickStyle(a, greenStyle, yellowStyle, redStyle);
                writeDataRow(sheet, rowIdx++, a, style);
            }

            autoSizeColumns(sheet, HEADERS.length);

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                wb.write(out);
            }
        }
        log.info("Technical workbook written: {}", outputPath.toAbsolutePath());
    }

    private void writeHeaderRow(XSSFSheet sheet, XSSFCellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRow(XSSFSheet sheet, int rowNum, LayoutAnalysis a, XSSFCellStyle style) {
        Row row = sheet.createRow(rowNum);
        setString(row, 0, a.simpleClassName(),                    style);
        setString(row, 1, a.className(),                          style);
        setString(row, 2, a.packageName(),                        style);
        setLong  (row, 3, a.instanceSize(),                       style);
        setLong  (row, 4, a.fieldCount(),                         style);
        setLong  (row, 5, a.paddingBytes(),                       style);
        setString(row, 6, String.join(", ", a.boxedFieldNames()), style);
        setString(row, 7, a.rootCause(),                          style);
        setString(row, 8, a.suggestion(),                         style);
    }

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(BLUE, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.MEDIUM);
        return style;
    }

    private XSSFCellStyle buildRowStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /** Same severity rules as the developer workbook: avoidable gaps vs boxed fields. */
    private XSSFCellStyle pickStyle(LayoutAnalysis a,
                                    XSSFCellStyle green,
                                    XSSFCellStyle yellow,
                                    XSSFCellStyle red) {
        boolean hasAvoidablePadding = a.avoidablePaddingBytes() > 0;
        boolean hasBoxed            = !a.boxedFieldNames().isEmpty();
        if (hasAvoidablePadding && hasBoxed) return red;
        if (hasAvoidablePadding || hasBoxed) return yellow;
        return green;
    }

    private void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            // cap wide columns (suggestion/root-cause) at ~80 characters
            if (sheet.getColumnWidth(i) > 20480) {
                sheet.setColumnWidth(i, 20480);
            }
        }
    }

    private void setString(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setLong(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}

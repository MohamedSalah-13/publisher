package com.hamzatech.publisher.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelServiceTest {

    private final ExcelService excelService = new ExcelService();

    @Test
    void readTitles_skipsHeaderRowAndBlankCells(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("titles.xlsx").toFile();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Title");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("  عنوان أول  ");

            Row blankRow = sheet.createRow(2);
            blankRow.createCell(0).setCellValue("");

            Row numericRow = sheet.createRow(3);
            numericRow.createCell(0).setCellValue(42);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }

        List<String> titles = excelService.readTitles(file);

        assertEquals(List.of("عنوان أول", "42.0"), titles);
    }

    @Test
    void readTitles_returnsEmptyListWhenOnlyHeaderExists(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("empty.xlsx").toFile();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Title");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }

        assertTrue(excelService.readTitles(file).isEmpty());
    }

    @Test
    void writeTitles_roundTripsThroughReadTitles(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("generated.xlsx").toFile();

        excelService.writeTitles(file, List.of("عنوان أول", "  ", "Second title"));

        // صف الرأس يُتخطّى عند القراءة، والفارغ لا يُكتب أصلًا
        assertEquals(List.of("عنوان أول", "Second title"), excelService.readTitles(file));
    }
}

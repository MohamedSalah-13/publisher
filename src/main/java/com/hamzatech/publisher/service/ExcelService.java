package com.hamzatech.publisher.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExcelService {

    /**
     * يكتب العناوين في ملف بالصيغة التي يقرؤها {@link #readTitles}: صف رأس، ثم
     * عنوان في العمود الأول من كل صف.
     */
    public void writeTitles(File file, List<String> titles) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Titles");
            sheet.createRow(0).createCell(0).setCellValue("Title");
            int rowIndex = 1;
            for (String title : titles) {
                if (title != null && !title.isBlank()) {
                    sheet.createRow(rowIndex++).createCell(0).setCellValue(title.trim());
                }
            }
            sheet.setColumnWidth(0, 90 * 256);
            workbook.write(out);
        }
    }

    /**
     * دالة لقراءة العناوين من العمود الأول في ملف الإكسل
     * @param file الملف المختار
     * @return قائمة بالعناوين
     */
    public List<String> readTitles(File file) throws IOException {
        List<String> titles = new ArrayList<>();

        // 1. فتح تدفق الملف
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) { // XSSFWorkbook لملفات .xlsx

            // 2. اختيار الصفحة الأولى (Sheet 1)
            Sheet sheet = workbook.getSheetAt(0);

            // 3. التكرار على الصفوف
            Iterator<Row> rowIterator = sheet.iterator();

            // متغير لتخطي الصف الأول (العناوين مثل "Title") إذا أردت
            boolean isFirstRow = true;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // تخطي صف العناوين (Header)
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                // 4. قراءة الخلية الأولى (العمود A)
                Cell cell = row.getCell(0); // Index 0 = Column A

                if (cell != null) {
                    String title = "";
                    // التعامل مع نوع الخلية (نص أو رقم) لتجنب الأخطاء
                    switch (cell.getCellType()) {
                        case STRING:
                            title = cell.getStringCellValue();
                            break;
                        case NUMERIC:
                            title = String.valueOf(cell.getNumericCellValue());
                            break;
                        default:
                            title = "";
                    }

                    // إضافة العنوان للقائمة إذا لم يكن فارغاً
                    if (!title.trim().isEmpty()) {
                        titles.add(title.trim());
                    }
                }
            }
        }

        return titles;
    }
}
package com.expensetracker.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ExportServiceImpl implements ExportService {

    private static final String[] COLUMNS =
            {"Title", "Category", "Amount", "Date", "Payment Method", "Description"};

    @Override
    public byte[] exportExpensesToPdf(List<Expense> expenses, User user) {
        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            com.lowagie.text.Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            com.lowagie.text.Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

            document.add(new Paragraph("Expense Report", titleFont));
            document.add(new Paragraph("Account: " + user.getFullName() + " (" + user.getEmail() + ")", subFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(COLUMNS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.2f, 1.6f, 1.2f, 1.3f, 1.5f, 2.5f});

            for (String column : COLUMNS) {
                PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
                cell.setBackgroundColor(new Color(33, 37, 41));
                cell.setPadding(6);
                table.addCell(cell);
            }

            double total = 0;
            for (Expense expense : expenses) {
                table.addCell(new Phrase(nullSafe(expense.getTitle()), cellFont));
                table.addCell(new Phrase(expense.getCategory() != null ? expense.getCategory().getName() : "-", cellFont));
                table.addCell(new Phrase(String.format("%.2f", expense.getAmount()), cellFont));
                table.addCell(new Phrase(expense.getExpenseDate() != null ? expense.getExpenseDate().toString() : "-", cellFont));
                table.addCell(new Phrase(nullSafe(expense.getPaymentMethod()), cellFont));
                table.addCell(new Phrase(nullSafe(expense.getDescription()), cellFont));
                total += expense.getAmount() != null ? expense.getAmount() : 0;
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total: " + String.format("%.2f", total),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK)));

            document.close();
            return out.toByteArray();

        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate PDF report", ex);
        }
    }

    @Override
    public byte[] exportExpensesToExcel(List<Expense> expenses, User user) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Expenses");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_80_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            double total = 0;
            for (Expense expense : expenses) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nullSafe(expense.getTitle()));
                row.createCell(1).setCellValue(expense.getCategory() != null ? expense.getCategory().getName() : "-");
                row.createCell(2).setCellValue(expense.getAmount() != null ? expense.getAmount() : 0);
                row.createCell(3).setCellValue(expense.getExpenseDate() != null ? expense.getExpenseDate().toString() : "-");
                row.createCell(4).setCellValue(nullSafe(expense.getPaymentMethod()));
                row.createCell(5).setCellValue(nullSafe(expense.getDescription()));
                total += expense.getAmount() != null ? expense.getAmount() : 0;
            }

            Row totalRow = sheet.createRow(rowIndex + 1);
            totalRow.createCell(1).setCellValue("Total");
            totalRow.createCell(2).setCellValue(total);

            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate Excel report", ex);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

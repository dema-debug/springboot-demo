package com.example.demotestes;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class Excel2Markdown {

    // 设置每列最大字符数（防止Markdown渲染过宽）
    private static final int MAX_COLUMN_WIDTH = 500;
    private static final Set<Character> MD_SPECIAL_CHARS = Set.of('|', '*', '_', '`', '#', '+', '-', '.', '!');


    public static void convertExcelToMarkdown(String excelPath, String markdownPath) throws IOException {
        try (InputStream is = new FileInputStream(excelPath);
             Workbook workbook = excelPath.endsWith(".xlsx")
                     ? new XSSFWorkbook(is)
                     : new HSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> tableData = extractTableData(sheet);

            String markdown = generateMarkdownTable(tableData);
            Files.write(Paths.get(markdownPath), markdown.getBytes());
        }
    }

    private static List<List<String>> extractTableData(Sheet sheet) {
        // 确定最大列数
        int maxColumns = IntStream.range(0, sheet.getLastRowNum() + 1)
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .mapToInt(Row::getLastCellNum)
                .max()
                .orElse(0);

        return IntStream.range(0, sheet.getLastRowNum() + 1)
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .map(row -> processRow(row, maxColumns))
                .collect(Collectors.toList());
    }

    private static List<String> processRow(Row row, int maxColumns) {
        return IntStream.range(0, maxColumns)
                .mapToObj(col -> Optional.ofNullable(row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)))
                .map(optCell -> formatCellValue(optCell.orElse(null)))
                .collect(Collectors.toList());
    }

    private static String generateMarkdownTable(List<List<String>> tableData) {
        if (tableData.isEmpty()) return "";

        // 计算每列最大宽度
        int[] colWidths = calculateColumnWidths(tableData);

        // 生成Markdown表格
        StringBuilder sb = new StringBuilder();

        // 表头行
        generateTableRow(sb, tableData.get(0), colWidths);

        // 分隔线
        sb.append("|");
        Arrays.stream(colWidths)
                .forEach(width -> sb.append(" ").append("-".repeat(width)).append(" |"));
        sb.append("\n");

        // 数据行
        tableData.stream()
                .skip(1)
                .forEach(row -> generateTableRow(sb, row, colWidths));

        return sb.toString();
    }

    private static void generateTableRow(StringBuilder sb, List<String> row, int[] colWidths) {
        sb.append("|");
        IntStream.range(0, colWidths.length)
                .forEach(i -> {
                    String content = i < row.size() ? row.get(i) : "";
                    sb.append(" ")
                            .append(String.format("%-" + colWidths[i] + "s", content))
                            .append(" |");
                });
        sb.append("\n");
    }

    private static int[] calculateColumnWidths(List<List<String>> tableData) {
        int colCount = tableData.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        return IntStream.range(0, colCount)
                .map(col -> tableData.stream()
                        .mapToInt(row ->
                                col < row.size()
                                        ? Math.min(formatCellValueForWidth(row.get(col)).length(), MAX_COLUMN_WIDTH)
                                        : 0)
                        .max()
                        .orElse(3))
                .map(width -> Math.max(width, 3))  // 最小宽度为3
                .toArray();
    }

    private static String formatCellValue(Cell cell) {
        return Optional.ofNullable(cell)
                .map(c -> {
                    switch (c.getCellType()) {
                        case STRING:
                            return c.getStringCellValue().trim();
                        case NUMERIC:
                            return DateUtil.isCellDateFormatted(c)
                                    ? c.getDateCellValue().toString()
                                    : (c.getNumericCellValue() == (long) c.getNumericCellValue()
                                    ? String.valueOf((long) c.getNumericCellValue())
                                    : String.valueOf(c.getNumericCellValue()));
                        case BOOLEAN:
                            return String.valueOf(c.getBooleanCellValue());
                        case FORMULA:
                            return evaluateFormulaCell(c);
                        default:
                            return "";
                    }
                })
                .map(Excel2Markdown::escapeMarkdownChars)
                .orElse("");
    }

    private static String evaluateFormulaCell(Cell cell) {
        try {
            switch (cell.getCachedFormulaResultType()) {
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    }
                    double num = cell.getNumericCellValue();
                    return num == (long) num
                            ? String.valueOf((long) num)
                            : String.valueOf(num);
                default: return cell.getStringCellValue();
            }
        } catch (Exception e) {
            return cell.getCellFormula();
        }
    }

    private static String formatCellValueForWidth(String value) {
        return escapeMarkdownChars(value);
    }

    private static String escapeMarkdownChars(String input) {
        return input.chars()
                .mapToObj(c -> (char) c)
                .map(ch -> MD_SPECIAL_CHARS.contains(ch) ? "\\" + ch : String.valueOf(ch))
                .collect(Collectors.joining());
    }
}
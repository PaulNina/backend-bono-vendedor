package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.sale.Venta;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ninabit.bono.modules.sale.VentaCampana;

@Service
public class SalesExcelGenerator {

    public byte[] generateWeeklySalesReport(List<Venta> sales, Long campanaId, java.time.LocalDate start,
            java.time.LocalDate end, String subjectTarget) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte_Ventas");

            // --- Report Metadata Header ---
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("REPORTE DE VENTAS");
            c0.setCellStyle(titleStyle);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Desde:");
            r1.createCell(1).setCellValue(start != null ? start.toString() : "N/A");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Hasta:");
            r2.createCell(1).setCellValue(end != null ? end.toString() : "N/A");

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Filtro Origen:");
            r3.createCell(1).setCellValue(subjectTarget);

            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("Campaña Id:");
            r4.createCell(1).setCellValue(campanaId != null ? campanaId.toString() : "Todas las Campañas");

            // --- Table Header Row ---
            Row headerRow = sheet.createRow(6);
            String[] headers = { "Fecha Venta", "Vendedor", "Teléfono", "Ciudad", "Tipo", "Modelo", "Serial", "Campaña",
                    "Puntos", "Bono (Bs)" };
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // --- Data Rows ---
            int rowIdx = 7;
            for (Venta sale : sales) {
                if (sale.getDetallesCampanas() != null && !sale.getDetallesCampanas().isEmpty()) {
                    for (VentaCampana vc : sale.getDetallesCampanas()) {
                        if (campanaId != null && vc.getCampana() != null
                                && !campanaId.equals(vc.getCampana().getId())) {
                            continue; // Skip if it doesn't match the selected campaign
                        }

                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(sale.getSaleDate() != null ? sale.getSaleDate().toString() : "");
                        row.createCell(1).setCellValue(sale.getVendorName() != null ? sale.getVendorName() : "");
                        row.createCell(2).setCellValue(sale.getVendorPhone() != null ? sale.getVendorPhone() : "");
                        row.createCell(3).setCellValue(sale.getCiudad() != null ? sale.getCiudad() : "");
                        row.createCell(4).setCellValue(sale.getProductType() != null ? sale.getProductType() : "");
                        row.createCell(5).setCellValue(sale.getProductModel() != null ? sale.getProductModel() : "");
                        row.createCell(6).setCellValue(sale.getSerial() != null ? sale.getSerial() : "");
                        row.createCell(7).setCellValue(vc.getCampana() != null ? vc.getCampana().getNombre() : "");
                        row.createCell(8).setCellValue(vc.getPuntosGanados() != null ? vc.getPuntosGanados() : 0);
                        row.createCell(9).setCellValue(vc.getBonoGanado() != null ? vc.getBonoGanado() : 0);
                    }
                } else {
                    if (campanaId != null) {
                        continue; // If a specific campaign is requested, skip sales with no campaigns
                    }
                    // Fallback for older sales with no campaign details
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(sale.getSaleDate() != null ? sale.getSaleDate().toString() : "");
                    row.createCell(1).setCellValue(sale.getVendorName() != null ? sale.getVendorName() : "");
                    row.createCell(2).setCellValue(sale.getVendorPhone() != null ? sale.getVendorPhone() : "");
                    row.createCell(3).setCellValue(sale.getCiudad() != null ? sale.getCiudad() : "");
                    row.createCell(4).setCellValue(sale.getProductType() != null ? sale.getProductType() : "");
                    row.createCell(5).setCellValue(sale.getProductModel() != null ? sale.getProductModel() : "");
                    row.createCell(6).setCellValue(sale.getSerial() != null ? sale.getSerial() : "");
                    row.createCell(7).setCellValue("Sin Campaña");
                    row.createCell(8).setCellValue(sale.getPuntos() != null ? sale.getPuntos() : 0);
                    row.createCell(9).setCellValue(sale.getBonoBs() != null ? sale.getBonoBs() : 0);
                }
            }

            // Summaries by Vendor and City
            rowIdx += 2;
            Row summaryTitleRow = sheet.createRow(rowIdx++);
            Cell summaryTitleCell = summaryTitleRow.createCell(0);
            summaryTitleCell.setCellValue("Resumen por Vendedor y Ciudad");
            summaryTitleCell.setCellStyle(headerStyle);

            Row summaryHeaderRow = sheet.createRow(rowIdx++);
            summaryHeaderRow.createCell(0).setCellValue("Vendedor");
            summaryHeaderRow.createCell(1).setCellValue("Ciudad");
            summaryHeaderRow.createCell(2).setCellValue("Total Bono (Bs)");

            Map<String, Integer> bonoByVendorCity = sales.stream()
                    .collect(Collectors.groupingBy(
                            v -> (v.getVendorName() != null ? v.getVendorName() : "S/N") + " - "
                                    + (v.getCiudad() != null ? v.getCiudad() : "S/C"),
                            Collectors.summingInt(v -> {
                                if (v.getDetallesCampanas() != null && !v.getDetallesCampanas().isEmpty()) {
                                    return v.getDetallesCampanas().stream()
                                            .filter(vc -> campanaId == null || (vc.getCampana() != null
                                                    && campanaId.equals(vc.getCampana().getId())))
                                            .mapToInt(vc -> vc.getBonoGanado() != null ? vc.getBonoGanado() : 0).sum();
                                }
                                return campanaId == null && v.getBonoBs() != null ? v.getBonoBs() : 0;
                            })));

            for (Map.Entry<String, Integer> entry : bonoByVendorCity.entrySet()) {
                String[] parts = entry.getKey().split(" - ");
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(parts[0]);
                row.createCell(1).setCellValue(parts.length > 1 ? parts[1] : "");
                row.createCell(2).setCellValue(entry.getValue());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el reporte en Excel", e);
        }
    }
}

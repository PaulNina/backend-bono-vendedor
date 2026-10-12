package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.payment.CommissionReportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

@Service
public class PaymentExcelGenerator {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public byte[] generatePaymentsReport(List<CommissionReportDTO> reports, boolean withQr,
            String campanaNombre, String ciudad, java.time.LocalDate startDate, java.time.LocalDate endDate,
            String estadoPago) {
        // Usar SXSSFWorkbook para generación ligera en streaming
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet("Liquidacion_Comisiones");
            sheet.trackAllColumnsForAutoSizing();

            // --- Report Metadata Header ---
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("REPORTE DE COMISIONES Y PAGOS");
            c0.setCellStyle(titleStyle);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Desde:");
            r1.createCell(1).setCellValue(startDate != null ? startDate.toString() : "N/A");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Hasta:");
            r2.createCell(1).setCellValue(endDate != null ? endDate.toString() : "N/A");

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Ciudad:");
            r3.createCell(1).setCellValue(ciudad != null ? ciudad : "Todas");
            r3.createCell(2).setCellValue("Estado Pago:");
            r3.createCell(3).setCellValue(estadoPago != null ? estadoPago : "Todos");

            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("Campaña:");
            r4.createCell(1).setCellValue(campanaNombre);

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Columns definition
            String[] headers = withQr
                    ? new String[] { "Vendedor", "Ciudad", "Tienda", "Talla", "Uds", "Comisión (Bs)", "Estado", "QR" }
                    : new String[] { "Vendedor", "Ciudad", "Tienda", "Talla", "Uds", "Comisión (Bs)", "Estado" };

            Row headerRow = sheet.createRow(6);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // Optional Drawings setup for QRs
            Drawing<?> drawing = null;
            if (withQr) {
                drawing = sheet.createDrawingPatriarch();
                sheet.setColumnWidth(7, 20 * 256); // approx 20 characters wide
            }

            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0.00"));

            int rowIdx = 7;
            for (CommissionReportDTO rp : reports) {
                Row row = sheet.createRow(rowIdx);
                if (withQr) {
                    row.setHeight((short) (100 * 15)); // 100 pixels approx
                }

                row.createCell(0).setCellValue(rp.getVendedorNombre() != null ? rp.getVendedorNombre() : "");
                row.createCell(1).setCellValue(rp.getCiudad() != null ? rp.getCiudad() : "");
                row.createCell(2).setCellValue(rp.getTienda() != null ? rp.getTienda() : "");
                row.createCell(3).setCellValue(rp.getTallaPolera() != null ? rp.getTallaPolera() : "");
                row.createCell(4).setCellValue(rp.getCantidadVentas() != null ? rp.getCantidadVentas() : 0);

                Cell montoCell = row.createCell(5);
                montoCell.setCellValue(rp.getMontoTotal() != null ? rp.getMontoTotal() : 0);
                montoCell.setCellStyle(currencyStyle);

                row.createCell(6).setCellValue(rp.getEstado() != null ? rp.getEstado() : "");

                if (withQr && rp.getFotoQr() != null && !rp.getFotoQr().isBlank()) {
                    try {
                        // Attempt to load the image
                        File imgFile = new File(uploadDir,
                                rp.getFotoQr().replace("uploads/", "").replace("uploads\\\\", ""));
                        if (imgFile.exists()) {
                            try (FileInputStream fis = new FileInputStream(imgFile)) {
                                byte[] bytes = IOUtils.toByteArray(fis);

                                int pictureIdx = workbook.addPicture(bytes, getPictureType(imgFile.getName()));

                                CreationHelper helper = workbook.getCreationHelper();
                                ClientAnchor anchor = helper.createClientAnchor();
                                anchor.setCol1(7);
                                anchor.setRow1(rowIdx);
                                anchor.setCol2(8);
                                anchor.setRow2(rowIdx + 1);

                                // Padding
                                anchor.setDx1(5 * 9525);
                                anchor.setDy1(5 * 9525);
                                anchor.setDx2(-5 * 9525);
                                anchor.setDy2(-5 * 9525);

                                if (drawing != null) {
                                    drawing.createPicture(anchor, pictureIdx);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error loading QR image for vendor: " + rp.getVendedorNombre() + " - "
                                + e.getMessage());
                    }
                }

                rowIdx++;
            }

            for (int i = 0; i < (withQr ? 7 : headers.length); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el reporte de pagos", e);
        }
    }

    public byte[] generateAuditSalesReport(List<com.ninabit.bono.modules.sale.Venta> ventas,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String ciudad, String estado) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet("Auditoria_Ventas");
            sheet.trackAllColumnsForAutoSizing();

            // Metadata Header
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("REPORTE GENERAL DE AUDITORÍA DE VENTAS");
            c0.setCellStyle(titleStyle);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Desde:");
            r1.createCell(1).setCellValue(startDate != null ? startDate.toString() : "N/A");
            r1.createCell(2).setCellValue("Hasta:");
            r1.createCell(3).setCellValue(endDate != null ? endDate.toString() : "N/A");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Ciudad:");
            r2.createCell(1).setCellValue(ciudad != null ? ciudad : "Todas");
            r2.createCell(2).setCellValue("Estado:");
            r2.createCell(3).setCellValue(estado != null ? estado : "Todos");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = new String[] {
                "ID", "Serial", "Fecha Venta", "Fecha Registro", "Vendedor", "CI Vendedor", 
                "Teléfono", "Email", "Tienda", "Ciudad", "Modelo Producto", "Puntos", 
                "Bono (Bs)", "Estado", "Motivo Rechazo"
            };

            Row headerRow = sheet.createRow(4);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 5;
            for (com.ninabit.bono.modules.sale.Venta v : ventas) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(v.getId() != null ? v.getId() : 0);
                row.createCell(1).setCellValue(v.getSerial() != null ? v.getSerial() : "");
                row.createCell(2).setCellValue(v.getSaleDate() != null ? v.getSaleDate().toString() : "");
                row.createCell(3).setCellValue(v.getCreatedAt() != null ? v.getCreatedAt().toString() : "");
                row.createCell(4).setCellValue(v.getVendorName() != null ? v.getVendorName() : "");
                row.createCell(5).setCellValue(v.getVendorCi() != null ? v.getVendorCi() : "");
                row.createCell(6).setCellValue(v.getVendorPhone() != null ? v.getVendorPhone() : "");
                row.createCell(7).setCellValue(v.getVendorEmail() != null ? v.getVendorEmail() : "");
                row.createCell(8).setCellValue(v.getStoreName() != null ? v.getStoreName() : "");
                row.createCell(9).setCellValue(v.getCiudad() != null ? v.getCiudad() : "");
                row.createCell(10).setCellValue(v.getProductModel() != null ? v.getProductModel() : "");
                row.createCell(11).setCellValue(v.getPuntos() != null ? v.getPuntos() : 0);
                row.createCell(12).setCellValue(v.getBonoBs() != null ? v.getBonoBs() : 0);
                row.createCell(13).setCellValue(v.getEstado() != null ? v.getEstado().name() : "");
                row.createCell(14).setCellValue(v.getMotivoRechazo() != null ? v.getMotivoRechazo() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el reporte de auditoría de ventas", e);
        }
    }

    private int getPictureType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png"))
            return Workbook.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return Workbook.PICTURE_TYPE_JPEG;
        return Workbook.PICTURE_TYPE_JPEG; // Fallback
    }
}

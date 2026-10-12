package com.ninabit.bono.modules.damagedequipment;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

@Service
public class EquipoDanadoExcelGenerator {

    private static final Logger log = Logger.getLogger(EquipoDanadoExcelGenerator.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Altura fija para filas con imagen (en unidades de 1/20 de punto = twips)
    // 120px aprox = 120 * 15 = 1800 twips (aprox, POI usa 20 twips = 1pt, 1px ≈ 0.75pt)
    private static final int PHOTO_ROW_HEIGHT = 2400; // ~120px

    @Value("${app.upload.dir}")
    private String uploadDir;

    public byte[] generateExcelReport(List<EquipoDanado> equipos, String hostBaseUrl) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Equipos_Danados");

            // ── Estilos ──────────────────────────────────────────────────────────────
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(headerStyle);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setWrapText(true);
            setBorders(dataStyle);

            CellStyle matchStyle = workbook.createCellStyle();
            Font matchFont = workbook.createFont();
            matchFont.setBold(true);
            matchFont.setColor(IndexedColors.GREEN.getIndex());
            matchStyle.setFont(matchFont);
            matchStyle.setAlignment(HorizontalAlignment.CENTER);
            matchStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(matchStyle);

            CellStyle mismatchStyle = workbook.createCellStyle();
            Font mismatchFont = workbook.createFont();
            mismatchFont.setBold(true);
            mismatchFont.setColor(IndexedColors.RED.getIndex());
            mismatchStyle.setFont(mismatchFont);
            mismatchStyle.setAlignment(HorizontalAlignment.CENTER);
            mismatchStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(mismatchStyle);

            CellStyle photoLabelStyle = workbook.createCellStyle();
            Font photoLabelFont = workbook.createFont();
            photoLabelFont.setItalic(true);
            photoLabelFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            photoLabelStyle.setFont(photoLabelFont);
            photoLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            photoLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(photoLabelStyle);

            // ── Título ───────────────────────────────────────────────────────────────
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(22);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("SKYWORTH - REPORTE DE EQUIPOS DAÑADOS");
            titleCell.setCellStyle(titleStyle);

            Row subRow = sheet.createRow(1);
            subRow.createCell(0).setCellValue("Total Registros: " + (equipos != null ? equipos.size() : 0));

            // ── Cabecera ─────────────────────────────────────────────────────────────
            String[] headers = {
                    "#", "Fecha y Hora", "Cliente / Razón Social", "Ciudad", "Usuario Registro",
                    "Serial Caja", "Serial Televisor", "Validación Serial", "Serial Validado",
                    "Ingreso Manual", "Modelo", "Pulgadas", "Tipo de Falla", "Comentarios",
                    "Foto Tag Caja", "Foto Tag TV", "Foto Daño 1", "Foto Daño 2", "Foto Daño 3"
            };

            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(20);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Drawing patriarch para imágenes ──────────────────────────────────────
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFCreationHelper helper = workbook.getCreationHelper();

            // ── Datos ────────────────────────────────────────────────────────────────
            int rowIdx = 4;
            int counter = 1;

            if (equipos != null) {
                for (EquipoDanado eq : equipos) {
                    Row row = sheet.createRow(rowIdx);
                    row.setHeight((short) PHOTO_ROW_HEIGHT);

                    // Col 0: #
                    Cell c0 = row.createCell(0);
                    c0.setCellValue(counter++);
                    c0.setCellStyle(dataStyle);

                    // Col 1: Fecha y Hora
                    Cell c1 = row.createCell(1);
                    c1.setCellValue(eq.getCreatedAt() != null ? eq.getCreatedAt().format(DATE_TIME_FORMATTER) : "");
                    c1.setCellStyle(dataStyle);

                    // Col 2: Cliente
                    Cell c2 = row.createCell(2);
                    c2.setCellValue(eq.getRegistroCliente() != null ? eq.getRegistroCliente().getClienteNombre() : "");
                    c2.setCellStyle(dataStyle);

                    // Col 3: Ciudad
                    Cell c3 = row.createCell(3);
                    c3.setCellValue(eq.getRegistroCliente() != null ? eq.getRegistroCliente().getCiudad() : "");
                    c3.setCellStyle(dataStyle);

                    // Col 4: Usuario Registro
                    Cell c4 = row.createCell(4);
                    c4.setCellValue(eq.getRegistroCliente() != null && eq.getRegistroCliente().getUsuario() != null
                            ? eq.getRegistroCliente().getUsuario().getEmail() : "");
                    c4.setCellStyle(dataStyle);

                    // Col 5: Serial Caja
                    Cell c5 = row.createCell(5);
                    c5.setCellValue(eq.getSerialCaja() != null ? eq.getSerialCaja() : "");
                    c5.setCellStyle(dataStyle);

                    // Col 6: Serial TV
                    Cell c6 = row.createCell(6);
                    c6.setCellValue(eq.getSerialTv() != null ? eq.getSerialTv() : "");
                    c6.setCellStyle(dataStyle);

                    // Col 7: Validación Serial
                    Cell c7 = row.createCell(7);
                    if (eq.isSerialesCoinciden()) {
                        c7.setCellValue("COINCIDE");
                        c7.setCellStyle(matchStyle);
                    } else {
                        c7.setCellValue("NO COINCIDE");
                        c7.setCellStyle(mismatchStyle);
                    }

                    // Col 8: Serial Validado
                    Cell c8 = row.createCell(8);
                    c8.setCellValue(eq.getSerialValidado() != null ? eq.getSerialValidado() : "");
                    c8.setCellStyle(dataStyle);

                    // Col 9: Ingreso Manual
                    Cell c9 = row.createCell(9);
                    c9.setCellValue(eq.isSerialIngresoManual() ? "SÍ" : "NO");
                    c9.setCellStyle(dataStyle);

                    // Col 10: Modelo
                    Cell c10 = row.createCell(10);
                    c10.setCellValue(eq.getModelo() != null ? eq.getModelo() : "");
                    c10.setCellStyle(dataStyle);

                    // Col 11: Pulgadas
                    Cell c11 = row.createCell(11);
                    c11.setCellValue(eq.getPulgadas() != null ? eq.getPulgadas() + "\"" : "");
                    c11.setCellStyle(dataStyle);

                    // Col 12: Tipo de Falla
                    Cell c12 = row.createCell(12);
                    c12.setCellValue(eq.getTipoFalla() != null ? eq.getTipoFalla() : "");
                    c12.setCellStyle(dataStyle);

                    // Col 13: Comentarios
                    Cell c13 = row.createCell(13);
                    c13.setCellValue(eq.getComentarios() != null ? eq.getComentarios() : "");
                    c13.setCellStyle(dataStyle);

                    // Col 14-18: Fotos incrustadas
                    int[] photoCols = {14, 15, 16, 17, 18};
                    String[] photoPaths = {
                            eq.getFotoTagCaja(), eq.getFotoTagTv(),
                            eq.getFotoFalla1(), eq.getFotoFalla2(), eq.getFotoFalla3()
                    };

                    for (int pi = 0; pi < photoCols.length; pi++) {
                        int col = photoCols[pi];
                        String photoRelPath = photoPaths[pi];
                        Cell photoCell = row.createCell(col);
                        photoCell.setCellStyle(photoLabelStyle);

                        if (photoRelPath != null && !photoRelPath.isBlank()) {
                            try {
                                Path fullPath = Paths.get(uploadDir).resolve(
                                        photoRelPath.replaceFirst("^uploads/", "")
                                );
                                if (Files.exists(fullPath)) {
                                    byte[] imageBytes = Files.readAllBytes(fullPath);
                                    int pictureType = getPictureType(fullPath.toString());
                                    int pictureIdx = workbook.addPicture(imageBytes, pictureType);

                                    XSSFClientAnchor anchor = helper.createClientAnchor();
                                    anchor.setCol1(col);
                                    anchor.setRow1(rowIdx);
                                    anchor.setCol2(col + 1);
                                    anchor.setRow2(rowIdx + 1);
                                    anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

                                    drawing.createPicture(anchor, pictureIdx);
                                    // Cell vacía — la imagen la cubre
                                    photoCell.setCellValue("");
                                } else {
                                    photoCell.setCellValue("(archivo no encontrado)");
                                }
                            } catch (Exception ex) {
                                log.warning("No se pudo incrustar imagen " + photoRelPath + ": " + ex.getMessage());
                                photoCell.setCellValue("(error al leer foto)");
                            }
                        } else {
                            photoCell.setCellValue("");
                        }
                    }

                    rowIdx++;
                }
            }

            // ── Anchos de columna ────────────────────────────────────────────────────
            int[] colWidths = {
                    2000,   // # 
                    5000,   // Fecha
                    9000,   // Cliente
                    5000,   // Ciudad
                    7000,   // Usuario
                    5500,   // Serial Caja
                    5500,   // Serial TV
                    4000,   // Validación
                    5500,   // Serial Validado
                    3500,   // Manual
                    5000,   // Modelo
                    3000,   // Pulgadas
                    5500,   // Tipo Falla
                    9000,   // Comentarios
                    5000,   // Foto Tag Caja
                    5000,   // Foto Tag TV
                    5000,   // Foto Daño 1
                    5000,   // Foto Daño 2
                    5000,   // Foto Daño 3
            };
            for (int i = 0; i < colWidths.length; i++) {
                sheet.setColumnWidth(i, colWidths[i]);
            }

            // Freeze panes: fija cabecera
            sheet.createFreezePane(0, 4);

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error al generar reporte de equipos dañados: " + e.getMessage(), e);
        }
    }

    private void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private int getPictureType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        if (lower.endsWith(".gif")) return Workbook.PICTURE_TYPE_DIB;
        return Workbook.PICTURE_TYPE_JPEG; // jpg, jpeg, heic convertido, etc.
    }
}

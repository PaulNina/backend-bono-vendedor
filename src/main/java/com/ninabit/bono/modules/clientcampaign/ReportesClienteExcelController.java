package com.ninabit.bono.modules.clientcampaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/admin/reportes-cliente")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reportes Excel - Cliente Final")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class ReportesClienteExcelController {

    private final RegistroClienteRepository registroClienteRepository;
    private final InventarioImpulsadorRepository inventarioImpulsadorRepository;
    private final SorteoGanadorRepository sorteoGanadorRepository;
    private final EntregaRuletaRepository entregaRuletaRepository;

    @GetMapping("/exportar-registros")
    @Operation(summary = "Exportar registros de cliente final a Excel")
    public void exportarRegistros(
            @RequestParam(required = false) Long campanaId,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=registros_clientes.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             OutputStream out = response.getOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Registros Clientes");
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Campaña", "Canal", "Impulsador", "Cliente", "C.I.", "Celular", "Email",
                    "Ciudad", "Punto Venta", "Modelo", "Pulgadas", "Serial/TAG", "Fecha Compra",
                    "Estado", "Supervisor", "Fecha Revisión", "Motivo Observación", "Respaldos (Fotos)", "Fecha Registro"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Specification<RegistroCliente> spec = (root, query, cb) -> {
                if (campanaId != null) {
                    return cb.equal(root.get("campanaCliente").get("id"), campanaId);
                }
                return cb.conjunction();
            };

            int pageSize = 1000;
            int pageIdx = 0;
            boolean hasMore = true;
            int rowIdx = 1;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            while (hasMore) {
                Page<RegistroCliente> page = registroClienteRepository.findAll(
                        spec,
                        PageRequest.of(pageIdx, pageSize, Sort.by("id").ascending())
                );
                List<RegistroCliente> batch = page.getContent();
                if (batch.isEmpty()) {
                    break;
                }

                for (RegistroCliente r : batch) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(r.getId());
                    row.createCell(1).setCellValue(r.getCampanaCliente() != null ? r.getCampanaCliente().getNombre() : "");
                    row.createCell(2).setCellValue(r.getCanalRegistro() != null ? r.getCanalRegistro().name() : "");
                    row.createCell(3).setCellValue(r.getImpulsadorNombre() != null ? r.getImpulsadorNombre() : (r.getImpulsadorId() != null ? "ID: " + r.getImpulsadorId() : "—"));
                    row.createCell(4).setCellValue((r.getNombreCliente() != null ? r.getNombreCliente() : "") + " " + (r.getApellidoCliente() != null ? r.getApellidoCliente() : ""));
                    row.createCell(5).setCellValue(r.getCi() != null ? r.getCi() : "");
                    row.createCell(6).setCellValue(r.getCelular() != null ? r.getCelular() : "");
                    row.createCell(7).setCellValue(r.getEmail() != null ? r.getEmail() : "");
                    row.createCell(8).setCellValue(r.getCiudad() != null ? r.getCiudad() : "");
                    row.createCell(9).setCellValue(r.getPuntoVenta() != null ? r.getPuntoVenta() : "");
                    row.createCell(10).setCellValue(r.getProductoModelo() != null ? r.getProductoModelo() : "");
                    row.createCell(11).setCellValue(r.getProductoPulgadas() != null ? r.getProductoPulgadas().toString() : "");
                    row.createCell(12).setCellValue(r.getSerial() != null ? r.getSerial() : "");
                    row.createCell(13).setCellValue(r.getFechaCompra() != null ? r.getFechaCompra().toString() : "");
                    row.createCell(14).setCellValue(r.getEstado() != null ? r.getEstado().name() : "");
                    row.createCell(15).setCellValue(r.getSupervisorNombre() != null ? r.getSupervisorNombre() : "");
                    row.createCell(16).setCellValue(r.getFechaRevision() != null ? r.getFechaRevision().format(dtf) : "");
                    row.createCell(17).setCellValue(r.getMotivoObservacion() != null ? r.getMotivoObservacion() : "");

                    int fotosCount = 0;
                    if (r.getFotoTag() != null && !r.getFotoTag().isBlank()) fotosCount++;
                    if (r.getFotoPoliza() != null && !r.getFotoPoliza().isBlank()) fotosCount++;
                    if (r.getFotoNota() != null && !r.getFotoNota().isBlank()) fotosCount++;
                    if (r.getFotoCiAnverso() != null && !r.getFotoCiAnverso().isBlank()) fotosCount++;
                    if (r.getFotoCiReverso() != null && !r.getFotoCiReverso().isBlank()) fotosCount++;
                    row.createCell(18).setCellValue(fotosCount + "/5 fotos subidas");

                    row.createCell(19).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(dtf) : "");
                }

                if (!page.hasNext()) {
                    hasMore = false;
                } else {
                    pageIdx++;
                }
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    @GetMapping("/exportar-inventario")
    @Operation(summary = "Exportar conciliación de inventario de premios por impulsador")
    public void exportarInventario(
            @RequestParam Long campanaId,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=inventario_premios_impulsadores.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             OutputStream out = response.getOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Inventario Premios");
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Impulsador", "Premio", "Asignado", "Entregado", "Saldo (Físico a Devolver)"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            List<InventarioImpulsador> inventarios = inventarioImpulsadorRepository.findByCampanaClienteId(campanaId);
            int rowIdx = 1;

            for (InventarioImpulsador inv : inventarios) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(inv.getId());
                row.createCell(1).setCellValue(inv.getVendedorNombre() != null ? inv.getVendedorNombre() : "Impulsador " + inv.getVendedorId());
                row.createCell(2).setCellValue(inv.getPremioRuleta() != null ? inv.getPremioRuleta().getNombre() : "");
                row.createCell(3).setCellValue(inv.getCantidadAsignada());
                row.createCell(4).setCellValue(inv.getCantidadEntregada());
                row.createCell(5).setCellValue(inv.getSaldo());
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    @GetMapping("/exportar-entregas")
    @Operation(summary = "Exportar reporte detallado de entregas físicas con datos de clientes y foto de entrega")
    public void exportarEntregas(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) Long inventarioId,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = inventarioId != null 
                ? "entregas_inventario_" + inventarioId + ".xlsx" 
                : "reporte_entregas_clientes.xlsx";
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             OutputStream out = response.getOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Entregas Clientes");
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID Entrega", "Fecha Giro/Entrega", "Campaña", "Operador / Responsable",
                    "Punto de Venta", "Premio / Insumo", "Cliente", "C.I.", "Celular",
                    "Ciudad", "Modelo TV", "Pulgadas", "Serial TV",
                    "Fecha Compra", "Estado Validación",
                    "Tiene Foto Entrega", "Ruta Foto Entrega"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            List<EntregaRuleta> entregas;
            if (inventarioId != null) {
                InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                        .orElse(null);
                if (inv != null) {
                    List<EntregaRuleta> raw = entregaRuletaRepository.findByCampanaClienteIdAndPremioRuletaIdOrderByFechaGiroDesc(
                            inv.getCampanaCliente().getId(),
                            inv.getPremioRuleta().getId()
                    );
                    Long uid = inv.getUsuarioId();
                    Long vid = inv.getVendedorId();
                    String dest = inv.getNombreDestinatario();
                    entregas = raw.stream()
                            .filter(e -> (uid != null && java.util.Objects.equals(e.getImpulsadorId(), uid)) ||
                                         (vid != null && java.util.Objects.equals(e.getImpulsadorId(), vid)) ||
                                         (dest != null && !dest.isBlank() && dest.equalsIgnoreCase(e.getImpulsadorNombre())))
                            .collect(java.util.stream.Collectors.toList());
                } else {
                    entregas = java.util.Collections.emptyList();
                }
            } else if (campanaId != null) {
                entregas = entregaRuletaRepository.findByCampanaClienteIdOrderByFechaGiroDesc(campanaId);
            } else {
                entregas = entregaRuletaRepository.findAll();
            }

            int rowIdx = 1;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (EntregaRuleta e : entregas) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(e.getId());
                row.createCell(1).setCellValue(e.getFechaGiro() != null ? e.getFechaGiro().format(dtf) : "");
                row.createCell(2).setCellValue(e.getCampanaCliente() != null ? e.getCampanaCliente().getNombre() : "");
                row.createCell(3).setCellValue(e.getImpulsadorNombre() != null ? e.getImpulsadorNombre() : "ID: " + e.getImpulsadorId());

                RegistroCliente rc = e.getRegistroCliente();
                if (rc != null) {
                    row.createCell(4).setCellValue(rc.getPuntoVenta() != null ? rc.getPuntoVenta() : "");
                } else {
                    row.createCell(4).setCellValue("");
                }

                row.createCell(5).setCellValue(e.getPremioRuleta() != null ? e.getPremioRuleta().getNombre() : "");

                if (rc != null) {
                    row.createCell(6).setCellValue((rc.getNombreCliente() != null ? rc.getNombreCliente() : "") + " " + (rc.getApellidoCliente() != null ? rc.getApellidoCliente() : ""));
                    row.createCell(7).setCellValue(rc.getCi() != null ? rc.getCi() : "");
                    row.createCell(8).setCellValue(rc.getCelular() != null ? rc.getCelular() : "");
                    row.createCell(9).setCellValue(rc.getCiudad() != null ? rc.getCiudad() : "");
                    row.createCell(10).setCellValue(rc.getProductoModelo() != null ? rc.getProductoModelo() : "");
                    row.createCell(11).setCellValue(rc.getProductoPulgadas() != null ? rc.getProductoPulgadas().toString() : "");
                    row.createCell(12).setCellValue(rc.getSerial() != null ? rc.getSerial() : "");
                    row.createCell(13).setCellValue(rc.getFechaCompra() != null ? rc.getFechaCompra().toString() : "");
                    row.createCell(14).setCellValue(rc.getEstado() != null ? rc.getEstado().name() : "");
                } else {
                    for (int c = 6; c <= 14; c++) row.createCell(c).setCellValue("");
                }

                boolean tieneFoto = e.getFotoEntrega() != null && !e.getFotoEntrega().isBlank();
                row.createCell(15).setCellValue(tieneFoto ? "SÍ" : "NO");
                row.createCell(16).setCellValue(tieneFoto ? e.getFotoEntrega() : "Sin fotografía");
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    @GetMapping("/ganadores-excel")
    @Operation(summary = "Exportar lista de ganadores de sorteo a Excel")
    public void exportarGanadores(
            @RequestParam Long campanaId,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=ganadores_sorteo.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             OutputStream out = response.getOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Ganadores");
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Campaña", "Sorteo", "Tipo Sorteo", "Fecha Sorteo",
                    "Orden", "Premio", "Tipo Ganador", "Nombre Ganador", "C.I.",
                    "Celular", "N° Ticket", "Serial", "Modelo Producto", "Fecha Registro"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            List<SorteoGanador> ganadores = sorteoGanadorRepository.findBySorteoPeriodoCampanaClienteIdOrderByCreatedAtDesc(campanaId);
            int rowIdx = 1;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (SorteoGanador g : ganadores) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(g.getId());
                row.createCell(1).setCellValue(g.getSorteoPeriodo() != null && g.getSorteoPeriodo().getCampanaCliente() != null 
                        ? g.getSorteoPeriodo().getCampanaCliente().getNombre() : "");
                row.createCell(2).setCellValue(g.getSorteoPeriodo() != null ? g.getSorteoPeriodo().getNombre() : "");
                row.createCell(3).setCellValue(g.getSorteoPeriodo() != null && g.getSorteoPeriodo().getTipoSorteo() != null 
                        ? g.getSorteoPeriodo().getTipoSorteo().name() : "");
                row.createCell(4).setCellValue(g.getSorteoPeriodo() != null && g.getSorteoPeriodo().getFechaSorteo() != null 
                        ? g.getSorteoPeriodo().getFechaSorteo().toString() : "");
                row.createCell(5).setCellValue(g.getOrden());
                row.createCell(6).setCellValue(g.getPremio() != null ? g.getPremio() : "");
                row.createCell(7).setCellValue(g.getTipoParticipante() != null ? g.getTipoParticipante() : "CLIENTE");
                row.createCell(8).setCellValue(g.getClienteNombre() != null ? g.getClienteNombre() : "");
                row.createCell(9).setCellValue(g.getClienteCi() != null ? g.getClienteCi() : "");
                row.createCell(10).setCellValue(g.getClienteCelular() != null ? g.getClienteCelular() : "");
                String ticketNum = g.getTicketCliente() != null ? g.getTicketCliente().getCodigoTicket() 
                        : (g.getTicketVendedor() != null ? g.getTicketVendedor().getNumeroTicket() : "");
                row.createCell(11).setCellValue(ticketNum);
                row.createCell(12).setCellValue(g.getSerial() != null ? g.getSerial() : "");
                row.createCell(13).setCellValue(g.getProductoModelo() != null ? g.getProductoModelo() : "");
                row.createCell(14).setCellValue(g.getCreatedAt() != null ? g.getCreatedAt().format(dtf) : "");
            }

            workbook.write(out);
            workbook.dispose();
        }
    }
}

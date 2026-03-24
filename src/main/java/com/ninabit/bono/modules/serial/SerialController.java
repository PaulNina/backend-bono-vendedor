package com.ninabit.bono.modules.serial;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

import com.ninabit.bono.modules.campaign.CampanaProducto;
import com.ninabit.bono.modules.campaign.CampanaProductoRepository;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;

@RestController
@RequestMapping("/serials")
@RequiredArgsConstructor
@Tag(name = "Seriales")
public class SerialController {

    private final SerialRepository serialRepository;
    private final CampanaProductoRepository campanaProductoRepository;
    private final VentaRepository ventaRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Transactional(readOnly = true)
    @Operation(summary = "Listar todos los seriales paginados y filtrados")
    public Page<Serial> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Specification<Serial> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("numeroSerie")), pattern),
                        cb.like(cb.lower(root.get("container")), pattern),
                        cb.like(cb.lower(root.get("invoice")), pattern)));
            }

            if (status != null && !status.isBlank() && !status.equals("all")) {
                if (status.equals("BLOQUEADO")) {
                    predicates.add(cb.equal(root.get("bloqueado"), true));
                } else if (status.equals("DISPONIBLE")) {
                    predicates.add(cb.and(
                            cb.equal(root.get("bloqueado"), false),
                            cb.equal(root.get("estado"), Serial.Estado.DISPONIBLE),
                            cb.isNull(root.get("registroVendedorId"))));
                } else if (status.equals("USADO")) {
                    predicates.add(cb.or(
                            cb.equal(root.get("estado"), Serial.Estado.USADO),
                            cb.isNotNull(root.get("registroVendedorId"))));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return serialRepository.findAll(spec, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Operation(summary = "Estadísticas de seriales")
    public Map<String, Object> getStats() {
        long total = serialRepository.count();
        long disponibles = serialRepository.countDisponibles();
        long usados = serialRepository.countUsados();
        long bloqueados = serialRepository.countBloqueados();
        return Map.of(
                "total", total,
                "disponibles", disponibles,
                "usados", usados,
                "bloqueados", bloqueados);
    }

    @GetMapping("/{numeroSerie}/validate")
    @Transactional(readOnly = true)
    @Operation(summary = "Validar si un serial está disponible")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable String numeroSerie) {
        return serialRepository.findByNumeroSerie(numeroSerie)
                .map(s -> {
                    Serial.Estado estado = s.getEstado() != null ? s.getEstado() : Serial.Estado.DISPONIBLE;

                    // Determinar si el serial es válido (disponible)
                    boolean yaUsadoEnCampana = s.isBloqueado();
                    boolean yaUsadoEnEsta = s.getRegistroVendedorId() != null;
                    boolean estadoUsado = estado == Serial.Estado.USADO;
                    boolean valido = !yaUsadoEnCampana && !yaUsadoEnEsta && !estadoUsado;

                    // Construir mensaje según prioridad
                    String mensaje;
                    if (valido) {
                        mensaje = "Serial válido y disponible.";
                    } else if (yaUsadoEnCampana) {
                        String motivo = s.getMotivoBloqueo() != null ? s.getMotivoBloqueo()
                                : "fue utilizado en campañas anteriores";
                        mensaje = "Serial inválido: " + motivo + ".";
                    } else if (yaUsadoEnEsta) {
                        String fecha = s.getFechaRegistroVendedor() != null
                                ? s.getFechaRegistroVendedor().toLocalDate().toString()
                                : "fecha desconocida";

                        // Buscar en qué campañas se ocupó si es posible
                        java.util.Optional<Venta> ventaAnteriorOp = ventaRepository.findBySerial(s.getNumeroSerie());
                        if (ventaAnteriorOp.isPresent()) {
                            Venta v = ventaAnteriorOp.get();
                            if (v.getDetallesCampanas() != null && !v.getDetallesCampanas().isEmpty()) {
                                String campanasStr = v.getDetallesCampanas().stream()
                                        .map(vc -> vc.getCampana().getNombre())
                                        .collect(Collectors.joining(", "));
                                mensaje = "Serial ya fue ocupado en las campañas: " + campanasStr + " en fecha " + fecha
                                        + ".";
                            } else {
                                mensaje = "Serial ya fue ocupado en fecha " + fecha + ".";
                            }
                        } else {
                            mensaje = "Serial ya fue ocupado en fecha " + fecha + ".";
                        }
                    } else {
                        mensaje = "Serial no disponible (ya utilizado).";
                    }

                    String estadoStr = yaUsadoEnCampana ? "BLOQUEADO"
                            : (yaUsadoEnEsta || estadoUsado) ? "USADO"
                                    : "DISPONIBLE";

                    Map<String, Object> result = new HashMap<>();
                    result.put("valido", valido);
                    result.put("estado", estadoStr);
                    result.put("productoId", s.getProductoId());
                    result.put("modelo", s.getModelo() != null ? s.getModelo() : "");
                    result.put("producto", s.getProductoNombre() != null ? s.getProductoNombre() : "");
                    result.put("container", s.getContainer() != null ? s.getContainer() : "");
                    result.put("seal", s.getSeal() != null ? s.getSeal() : "");
                    result.put("hojaRegistro", s.getHojaRegistro() != null ? s.getHojaRegistro() : "");
                    result.put("fechaRegistroVendedor", s.getFechaRegistroVendedor());

                    result.put("mensaje", mensaje);

                    if (s.getProductoId() != null) {
                        List<CampanaProducto> cpList = campanaProductoRepository
                                .findByProductoIdAndActivoTrue(s.getProductoId());
                        int sumPuntos = cpList.stream().mapToInt(CampanaProducto::getPuntos).sum();
                        int sumBono = cpList.stream().mapToInt(CampanaProducto::getBonoBs).sum();

                        List<Map<String, Object>> campanasDetalle = cpList.stream().map(cp -> {
                            Map<String, Object> cDetalle = new HashMap<>();
                            cDetalle.put("campanaId", cp.getCampana().getId());
                            cDetalle.put("campanaNombre", cp.getCampana().getNombre());
                            cDetalle.put("puntos", cp.getPuntos());
                            cDetalle.put("bonoBs", cp.getBonoBs());
                            return cDetalle;
                        }).toList();

                        result.put("totalPuntos", sumPuntos);
                        result.put("totalBonoBs", sumBono);
                        result.put("cantCampanas", cpList.size());
                        result.put("campanas", campanasDetalle);

                        if (cpList.isEmpty()) {
                            // Aunque el serial exista, si el producto no está en ninguna campaña activa, la
                            // venta no dará nada
                            result.put("mensaje",
                                    mensaje + " (Atención: El producto no pertenece a ninguna campaña activa)");
                        }
                    } else {
                        result.put("totalPuntos", 0);
                        result.put("totalBonoBs", 0);
                        result.put("cantCampanas", 0);
                    }


                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("valido", false);
                    err.put("estado", "NO_ENCONTRADO");
                    err.put("mensaje", "Serial no encontrado en la base de datos.");
                    return ResponseEntity.ok(err);
                });
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un serial")
    public Serial create(@RequestBody Serial serial) {
        Serial saved = serialRepository.save(serial);
        auditoriaService.log(getUser(), "SERIAL_CREADO", 
            "Serial individual creado: " + saved.getNumeroSerie(), "Serial", saved.getId().toString());
        return saved;
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importar múltiples seriales")
    public Map<String, Object> bulkCreate(@RequestBody List<Serial> seriales) {
        int guardados = 0;
        int omitidos = 0;
        for (Serial s : seriales) {
            String ns = s.getNumeroSerie();
            if (ns == null || ns.isBlank())
                continue;
            if (!serialRepository.existsByNumeroSerie(ns)) {
                if (s.getEstado() == null)
                    s.setEstado(Serial.Estado.DISPONIBLE);
                serialRepository.save(s);
                guardados++;
            } else {
                omitidos++;
            }
        }
        auditoriaService.log(getUser(), "IMPORTACION_SERIALES_BULK", 
            "Importación masiva de seriales: " + guardados + " guardados, " + omitidos + " omitidos", 
            "Serial", null);
        return Map.of("guardados", guardados, "omitidos", omitidos);
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Specification<Serial> getSpec(String search, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("numeroSerie")), pattern),
                        cb.like(cb.lower(root.get("container")), pattern),
                        cb.like(cb.lower(root.get("invoice")), pattern)));
            }

            if (status != null && !status.isBlank() && !status.equals("all")) {
                if (status.equals("BLOQUEADO")) {
                    predicates.add(cb.equal(root.get("bloqueado"), true));
                } else if (status.equals("DISPONIBLE")) {
                    predicates.add(cb.and(
                            cb.equal(root.get("bloqueado"), false),
                            cb.equal(root.get("estado"), Serial.Estado.DISPONIBLE),
                            cb.isNull(root.get("registroVendedorId"))));
                } else if (status.equals("USADO")) {
                    predicates.add(cb.or(
                            cb.equal(root.get("estado"), Serial.Estado.USADO),
                            cb.isNotNull(root.get("registroVendedorId"))));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Transactional(readOnly = true)
    @Operation(summary = "Exportar todos los seriales a Excel (Streaming)")
    public void exportToExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=seriales.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                OutputStream out = response.getOutputStream()) {

            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Seriales");
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Serial", "Modelo", "Pulgadas", "Estado", "Comprador",
                    "Fecha Registro Comprador", "Fecha Compra Comprador", "Container",
                    "Seal", "Invoice", "Vendedor", "Fecha Registro Vendedor"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int pageSize = 1000;
            int pageIdx = 0;
            boolean hasMore = true;
            int rowIdx = 1;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            while (hasMore) {
                Specification<Serial> spec = getSpec(search, status);
                Page<Serial> serialPage = serialRepository.findAll(spec,
                        PageRequest.of(pageIdx, pageSize, Sort.by("id").ascending()));
                List<Serial> list = serialPage.getContent();

                if (list.isEmpty())
                    break;

                for (Serial s : list) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(s.getId());
                    row.createCell(1).setCellValue(s.getNumeroSerie());

                    // Modelo, Pulgadas
                    String modelName = s.getModelo() != null ? s.getModelo() : "";
                    int pulgadas = 0;
                    if (s.getProducto() != null) {
                        modelName = s.getProducto().getModelo() != null ? s.getProducto().getModelo()
                                : s.getProducto().getModeloCodigo();
                        pulgadas = s.getProducto().getTamanoPulgadas() != null ? s.getProducto().getTamanoPulgadas()
                                : (s.getProducto().getPulgadas() != null ? s.getProducto().getPulgadas() : 0);
                    }
                    row.createCell(2).setCellValue(modelName);
                    row.createCell(3).setCellValue(pulgadas);

                    // Estado
                    String estadoStr = "Disponible";
                    if (s.isBloqueado())
                        estadoStr = "Bloqueado";
                    else if (s.getRegistroCompradorId() != null || s.getEstado() == Serial.Estado.USADO)
                        estadoStr = "Usado";
                    row.createCell(4).setCellValue(estadoStr);

                    // Comprador (Sin entidad, dejamos vacío o ID si estuviera disponible)
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue(
                            s.getFechaRegistroComprador() != null ? s.getFechaRegistroComprador().format(dtf) : "");
                    row.createCell(7).setCellValue(""); // Sin fecha compra en entidad serial

                    // Logística
                    row.createCell(8).setCellValue(s.getContainer() != null ? s.getContainer() : "");
                    row.createCell(9).setCellValue(s.getSeal() != null ? s.getSeal() : "");
                    row.createCell(10).setCellValue(s.getInvoice() != null ? s.getInvoice() : "");

                    // Vendedor
                    String vendedorName = "";
                    if (s.getRegistroVendedorId() != null) {
                        vendedorName = "Vendedor no encontrado";
                        if (s.getRegistroVendedor() != null) {
                            vendedorName = s.getRegistroVendedor().getVendorName() != null
                                    ? s.getRegistroVendedor().getVendorName()
                                    : (s.getRegistroVendedor().getVendedor() != null
                                            ? s.getRegistroVendedor().getVendedor().getNombreCompleto()
                                            : "Vendedor no encontrado");
                        }
                    }
                    row.createCell(11).setCellValue(vendedorName);
                    row.createCell(12).setCellValue(
                            s.getFechaRegistroVendedor() != null ? s.getFechaRegistroVendedor().format(dtf) : "");
                }

                if (!serialPage.hasNext())
                    hasMore = false;
                else
                    pageIdx++;
            }

            workbook.write(out);
            workbook.dispose();
        }
    }
}

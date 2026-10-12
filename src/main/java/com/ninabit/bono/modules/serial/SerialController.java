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
import com.ninabit.bono.modules.product.ProductoRepository;

@RestController
@RequestMapping("/serials")
@RequiredArgsConstructor
@Tag(name = "Seriales")
public class SerialController {

    private final SerialRepository serialRepository;
    private final ProductoRepository productoRepository;
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

                    // Determinar si el serial es válido para Bono Vendedor (desacoplado de Cliente Final)
                    boolean yaUsadoEnCampana = s.isBloqueado();
                    boolean yaUsadoEnVendedor = s.getRegistroVendedorId() != null;
                    boolean usadoEnCliente = s.getRegistroCompradorId() != null;
                    boolean valido = !yaUsadoEnCampana && !yaUsadoEnVendedor;

                    // Construir mensaje según prioridad
                    String mensaje;
                    if (valido) {
                        if (usadoEnCliente) {
                            mensaje = "Serial válido para Bono Vendedor (registrado también en Cliente Final).";
                        } else {
                            mensaje = "Serial válido y disponible.";
                        }
                    } else if (yaUsadoEnCampana) {
                        String motivo = s.getMotivoBloqueo() != null ? s.getMotivoBloqueo()
                                : "fue utilizado en campañas anteriores o restringido";
                        mensaje = "Este televisor no está habilitado para registro (" + motivo + "). Consulta con tu supervisor si consideras que es un error.";
                    } else if (yaUsadoEnVendedor) {
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
                                mensaje = "Este televisor ya fue registrado anteriormente (" + campanasStr + ") en fecha " + fecha
                                        + ". No es posible registrarlo dos veces.";
                            } else {
                                mensaje = "Este televisor ya fue registrado anteriormente en fecha " + fecha + ". No es posible registrarlo dos veces.";
                            }
                        } else {
                            mensaje = "Este televisor ya fue registrado anteriormente en fecha " + fecha + ". No es posible registrarlo dos veces.";
                        }
                    } else {
                        mensaje = "Serial no disponible para registro.";
                    }

                    String estadoStr = yaUsadoEnCampana ? "BLOQUEADO"
                            : yaUsadoEnVendedor ? "USADO_VENDEDOR"
                            : usadoEnCliente ? "USADO_CLIENTE"
                            : "DISPONIBLE";

                    Map<String, Object> result = new HashMap<>();
                    result.put("valido", valido);
                    result.put("estado", estadoStr);
                    result.put("usadoEnClienteFinal", usadoEnCliente);
                    result.put("usadoEnVendedor", yaUsadoEnVendedor);
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
                                .findActiveAndValidByProductoId(s.getProductoId());
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
                    err.put("mensaje", "El número de serie no existe en el sistema. Verifica que esté bien escrito según la etiqueta trasera del televisor.");
                    return ResponseEntity.ok(err);
                });
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un serial individual")
    public ResponseEntity<?> create(@RequestBody Serial serial) {
        if (serial.getNumeroSerie() == null || serial.getNumeroSerie().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El número de serie es obligatorio"));
        }
        String cleanSerial = serial.getNumeroSerie().trim().toUpperCase();
        if (serialRepository.existsByNumeroSerie(cleanSerial)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El número de serie '" + cleanSerial + "' ya existe en el sistema"));
        }
        serial.setNumeroSerie(cleanSerial);
        if (serial.getEstado() == null) {
            serial.setEstado(Serial.Estado.DISPONIBLE);
        }

        // Auto-fill product info if product ID is provided
        if (serial.getProductoId() != null) {
            productoRepository.findById(serial.getProductoId()).ifPresent(p -> {
                if (serial.getModelo() == null || serial.getModelo().isBlank()) {
                    serial.setModelo(p.getModelo() != null ? p.getModelo() : p.getModeloCodigo());
                }
                if (serial.getProductoNombre() == null || serial.getProductoNombre().isBlank()) {
                    serial.setProductoNombre(p.getNombre());
                }
            });
        }

        Serial saved = serialRepository.save(serial);
        auditoriaService.log(getUser(), "SERIAL_CREADO", 
            "Serial individual creado: " + saved.getNumeroSerie(), "Serial", saved.getId().toString());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importar múltiples seriales")
    public Map<String, Object> bulkCreate(@RequestBody List<Serial> seriales) {
        int guardados = 0;
        int omitidos = 0;
        List<String> omitidosList = new ArrayList<>();

        for (Serial s : seriales) {
            String ns = s.getNumeroSerie();
            if (ns == null || ns.isBlank())
                continue;
            String cleanNs = ns.trim().toUpperCase();
            if (!serialRepository.existsByNumeroSerie(cleanNs)) {
                s.setNumeroSerie(cleanNs);
                if (s.getEstado() == null)
                    s.setEstado(Serial.Estado.DISPONIBLE);

                if (s.getProductoId() != null && (s.getModelo() == null || s.getProductoNombre() == null)) {
                    productoRepository.findById(s.getProductoId()).ifPresent(p -> {
                        if (s.getModelo() == null || s.getModelo().isBlank()) {
                            s.setModelo(p.getModelo() != null ? p.getModelo() : p.getModeloCodigo());
                        }
                        if (s.getProductoNombre() == null || s.getProductoNombre().isBlank()) {
                            s.setProductoNombre(p.getNombre());
                        }
                    });
                }

                serialRepository.save(s);
                guardados++;
            } else {
                omitidos++;
                if (omitidosList.size() < 20) {
                    omitidosList.add(cleanNs);
                }
            }
        }
        auditoriaService.log(getUser(), "IMPORTACION_SERIALES_BULK", 
            "Importación masiva de seriales: " + guardados + " guardados, " + omitidos + " omitidos", 
            "Serial", null);
        return Map.of(
            "guardados", guardados,
            "omitidos", omitidos,
            "omitidosMuestra", omitidosList
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un serial")
    public ResponseEntity<Serial> update(@PathVariable Long id, @RequestBody SerialUpdateDto payload) {
        return serialRepository.findById(id).map(existing -> {
            if (payload.getNumeroSerie() != null && !payload.getNumeroSerie().isBlank())
                existing.setNumeroSerie(payload.getNumeroSerie());
            existing.setModelo(payload.getModelo());
            existing.setProductoId(payload.getProductoId());
            existing.setProductoNombre(payload.getProductoNombre());
            existing.setContainer(payload.getContainer());
            existing.setSeal(payload.getSeal());
            existing.setHojaRegistro(payload.getHojaRegistro());
            existing.setInvoice(payload.getInvoice());
            if (payload.getDateInvoice() != null && !payload.getDateInvoice().isBlank()) {
                try {
                    if (payload.getDateInvoice().contains("T")) {
                        existing.setDateInvoice(java.time.LocalDateTime.parse(payload.getDateInvoice()));
                    } else {
                        existing.setDateInvoice(java.time.LocalDate.parse(payload.getDateInvoice()).atStartOfDay());
                    }
                } catch (Exception ignored) {}
            } else {
                existing.setDateInvoice(null);
            }
            if (payload.getEstado() != null) {
                try { existing.setEstado(Serial.Estado.valueOf(payload.getEstado())); } catch (Exception ignored) {}
            }
            if (payload.getBloqueado() != null) existing.setBloqueado(payload.getBloqueado());
            existing.setMotivoBloqueo(payload.getMotivoBloqueo());
            Serial saved = serialRepository.save(existing);
            auditoriaService.log(getUser(), "SERIAL_ACTUALIZADO",
                "Serial actualizado: " + saved.getNumeroSerie(), "Serial", saved.getId().toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un serial")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return serialRepository.findById(id).map(existing -> {
            serialRepository.delete(existing);
            auditoriaService.log(getUser(), "SERIAL_ELIMINADO",
                "Serial eliminado: " + existing.getNumeroSerie(), "Serial", id.toString());
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
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

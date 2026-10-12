package com.ninabit.bono.modules.sale;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;
import com.ninabit.bono.modules.serial.Serial;
import com.ninabit.bono.modules.serial.SerialRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import com.ninabit.bono.modules.campaign.CampanaProducto;
import com.ninabit.bono.modules.campaign.CampanaProductoRepository;
import com.ninabit.bono.modules.email.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ventas")
public class VentaController {

    private final VentaRepository ventaRepository;
    private final SerialRepository serialRepository;
    private final ProductoRepository productoRepository;
    private final VendedorRepository vendedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final CampanaProductoRepository campanaProductoRepository;
    private final VentaCampanaRepository ventaCampanaRepository;
    private final com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository configuracionGlobalRepository;
    private final EmailService emailService;
    private final com.ninabit.bono.modules.report.PaymentExcelGenerator paymentExcelGenerator;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.ninabit.bono.modules.tienda.TiendaRepository tiendaRepository;
    private final com.ninabit.bono.modules.tienda.VendedorTiendaRepository vendedorTiendaRepository;
    private final com.ninabit.bono.modules.vendorprize.PuntosVendedorService puntosVendedorService;
    private final com.ninabit.bono.modules.vendorprize.MovimientoPuntosRepository movimientoPuntosRepository;
    private final com.ninabit.bono.modules.vendorprize.TicketVendedorRepository ticketVendedorRepository;
    private final com.ninabit.bono.modules.liquidation.IncidenciaRegularizacionRepository incidenciaRegularizacionRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private List<Venta> populateTransientFields(List<Venta> ventas) {
        if (ventas == null || ventas.isEmpty()) return ventas;

        // Carga todos los vendedores necesarios EN UNA SOLA QUERY (evita N+1)
        List<Long> ids = ventas.stream()
                .map(Venta::getVendedorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Vendedor> vendedorMap = vendedorRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Vendedor::getId, v -> v));

        ventas.forEach(v -> {
            Vendedor vendedor = vendedorMap.get(v.getVendedorId());
            if (vendedor != null) {
                v.setVendorName(vendedor.getNombreCompleto());
                if (v.getStoreName() == null || v.getStoreName().isBlank()) {
                    v.setStoreName(vendedor.getTienda());
                }
                v.setVendorPhone(vendedor.getTelefono());
                v.setVendorEmail(vendedor.getEmail());
                v.setVendorCi(vendedor.getCi());
            }
        });
        return ventas;
    }

    private org.springframework.data.jpa.domain.Specification<Venta> buildUserScopeSpec(Usuario usuario) {
        if (usuario.getRol() == Usuario.Rol.ADMIN_TOTAL || usuario.getRol() == Usuario.Rol.ADMIN) {
            return null;
        }

        List<String> allowedCities = (usuario.getCiudad() != null && !usuario.getCiudad().isBlank())
                ? Arrays.stream(usuario.getCiudad().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : null;
        Long scopedTiendaId = usuario.getTiendaId();

        if (allowedCities == null && scopedTiendaId == null) {
            return null;
        }

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (allowedCities != null && !allowedCities.isEmpty()) {
                List<jakarta.persistence.criteria.Predicate> cityPredicates = new ArrayList<>();
                for (String c : allowedCities) {
                    cityPredicates.add(cb.equal(cb.lower(root.get("ciudad")), c.toLowerCase().trim()));
                }
                predicates.add(cb.or(cityPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            if (scopedTiendaId != null) {
                predicates.add(cb.equal(root.get("tiendaId"), scopedTiendaId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'REVIEWER', 'REVISOR', 'SUPERVISOR', 'ENCARGADO_TIENDA', 'VISOR')")
    @Operation(summary = "Listar todas las ventas con paginación y filtros")
    public org.springframework.data.domain.Page<Venta> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String dateType,
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Authentication auth) {

        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc")
                ? org.springframework.data.domain.Sort.by(sortBy).descending()
                : org.springframework.data.domain.Sort.by(sortBy).ascending();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                sort);

        org.springframework.data.jpa.domain.Specification<Venta> spec = VentaSpecification.filterBy(search, city,
                status, startDate, endDate, campanaId, dateType, tiendaId, vendedorId);

        org.springframework.data.jpa.domain.Specification<Venta> scopeSpec = buildUserScopeSpec(usuario);
        if (scopeSpec != null) {
            spec = spec.and(scopeSpec);
        }

        org.springframework.data.domain.Page<Venta> pageResult = ventaRepository.findAll(spec, pageable);

        // Populate transient fields for the current page
        populateTransientFields(pageResult.getContent());

        return pageResult;
    }

    @GetMapping("/export-excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'REVIEWER', 'REVISOR', 'SUPERVISOR', 'VISOR')")
    @Operation(summary = "Exportar reporte de auditoría de ventas a Excel")
    public ResponseEntity<byte[]> exportAuditExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String dateType,
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) Long vendedorId,
            Authentication auth) {

        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        org.springframework.data.jpa.domain.Specification<Venta> spec = VentaSpecification.filterBy(search, city,
                status, startDate, endDate, campanaId, dateType, tiendaId, vendedorId);

        org.springframework.data.jpa.domain.Specification<Venta> scopeSpec = buildUserScopeSpec(usuario);
        if (scopeSpec != null) {
            spec = spec.and(scopeSpec);
        }

        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by("createdAt").descending();
        List<Venta> ventas = ventaRepository.findAll(spec, sort);
        populateTransientFields(ventas);

        byte[] excelBytes = paymentExcelGenerator.generateAuditSalesReport(ventas, startDate, endDate, city, status);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType
                .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Reporte_Auditoria_Ventas.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return org.springframework.http.ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'REVIEWER', 'REVISOR', 'SUPERVISOR', 'VISOR')")
    @Operation(summary = "Obtener estadísticas de ventas con filtros")
    public Map<String, Long> getStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String dateType,
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) Long vendedorId,
            Authentication auth) {

        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        org.springframework.data.jpa.domain.Specification<Venta> baseSpec = VentaSpecification.filterBy(search, city,
                null, startDate, endDate, campanaId, dateType, tiendaId, vendedorId);

        org.springframework.data.jpa.domain.Specification<Venta> scopeSpec = buildUserScopeSpec(usuario);
        if (scopeSpec != null) {
            baseSpec = baseSpec.and(scopeSpec);
        }

        Map<String, Long> stats = new HashMap<>();

        final org.springframework.data.jpa.domain.Specification<Venta> finalSpec = baseSpec;
        stats.put("total", ventaRepository.count(finalSpec));
        stats.put("pending", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.PENDIENTE))));
        stats.put("observed", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.OBSERVADA))));
        stats.put("approved", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.APROBADA))));
        stats.put("rejected", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.RECHAZADA))));

        return stats;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('REVIEWER', 'REVISOR', 'ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Listar ventas pendientes")
    public List<Venta> getPending(@RequestParam(required = false) String city, Authentication auth) {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        org.springframework.data.jpa.domain.Specification<Venta> spec = (root, query, cb) ->
                cb.equal(root.get("estado"), Venta.Estado.PENDIENTE);

        if (city != null && !city.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("ciudad")), city.toLowerCase().trim()));
        }

        org.springframework.data.jpa.domain.Specification<Venta> scopeSpec = buildUserScopeSpec(usuario);
        if (scopeSpec != null) {
            spec = spec.and(scopeSpec);
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 200,
                org.springframework.data.domain.Sort.by("createdAt").descending());

        List<Venta> ventas = ventaRepository.findAll(spec, pageable).getContent();
        return populateTransientFields(ventas);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Ventas del vendedor logueado")
    public List<Venta> getMySales(Authentication auth) {
        String email = auth.getName();
        return vendedorRepository.findByEmail(email)
                .map(v -> populateTransientFields(ventaRepository.findByVendedorId(v.getId())))
                .orElse(List.of());
    }

    @PostMapping(consumes = { "multipart/form-data" })
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Registrar venta con fotos")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> create(
            @RequestParam Long productoId,
            @RequestParam String serial,
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) String saleDate,
            @RequestParam MultipartFile fotoTag,
            @RequestParam MultipartFile fotoPoliza,
            @RequestParam MultipartFile fotoNota,
            Authentication auth) throws IOException {

        if (fotoTag == null || fotoTag.isEmpty() || fotoPoliza == null || fotoPoliza.isEmpty() || fotoNota == null || fotoNota.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Debes adjuntar las 3 fotografías obligatorias: Foto TAG, Póliza de garantía y Nota de venta."));
        }

        // validar serial - desacoplado de cliente final (Section 13)
        Serial ser = serialRepository.findBySerial(serial.trim())
                .orElse(null);
        if (ser == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "El número de serie ingresado ('" + serial.trim() + "') no existe en el sistema. Verifica que esté bien escrito según la etiqueta del televisor."));
        }
        if (ser.isBloqueado()) {
            String motivo = ser.getMotivoBloqueo() != null ? ser.getMotivoBloqueo() : "fue utilizado en campañas anteriores o restringido";
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Este televisor no está habilitado para registro (" + motivo + "). Si consideras que es un error, consulta con tu supervisor."));
        }
        if (ser.getRegistroVendedorId() != null) {
            String fecha = ser.getFechaRegistroVendedor() != null
                    ? ser.getFechaRegistroVendedor().toLocalDate().toString()
                    : "fecha previa";
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Este televisor (Serial " + serial.trim() + ") ya fue registrado anteriormente el " + fecha + ". No es posible registrar el mismo televisor dos veces."));
        }

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "El modelo de televisor seleccionado ya no se encuentra en el catálogo. Por favor, selecciónalo nuevamente de la lista."));
        }

        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(Map.of("error",
                    "No pudimos verificar tu cuenta de vendedor. Por favor, cierra sesión y vuelve a ingresar."));
        }

        // Resolver tienda y fotografía comercial histórica (Section 2.1)
        com.ninabit.bono.modules.tienda.Tienda tiendaSeleccionada = null;
        if (tiendaId != null) {
            tiendaSeleccionada = tiendaRepository.findById(tiendaId).orElse(null);
        }
        if (tiendaSeleccionada == null) {
            List<com.ninabit.bono.modules.tienda.Tienda> tiendasVendedor = vendedorTiendaRepository.findActiveTiendasByVendedorId(vendedor.getId());
            if (!tiendasVendedor.isEmpty()) {
                tiendaSeleccionada = tiendasVendedor.get(0);
            } else if (vendedor.getTienda() != null && !vendedor.getTienda().isBlank()) {
                tiendaSeleccionada = tiendaRepository.findFirstByNombreIgnoreCase(vendedor.getTienda().trim()).orElse(null);
            }
        }

        String storeName = tiendaSeleccionada != null ? tiendaSeleccionada.getNombre() : vendedor.getTienda();
        String storeCode = tiendaSeleccionada != null ? tiendaSeleccionada.getCodigo() : null;
        String storeCity = tiendaSeleccionada != null && tiendaSeleccionada.getCiudad() != null
                ? tiendaSeleccionada.getCiudad().getNombre()
                : (vendedor.getCiudad() != null ? vendedor.getCiudad().getNombre() : "Desconocida");
        String storeDept = tiendaSeleccionada != null ? tiendaSeleccionada.getDepartamento() : null;
        String storeChannel = tiendaSeleccionada != null ? tiendaSeleccionada.getCanal() : null;
        Long storeId = tiendaSeleccionada != null ? tiendaSeleccionada.getId() : null;

        List<CampanaProducto> campanasActivas = campanaProductoRepository
                .findActiveAndValidByProductoId(producto.getId());

        if (storeId != null && !campanasActivas.isEmpty()) {
            List<CampanaProducto> campanasFiltradas = campanasActivas.stream()
                    .filter(cp -> cp.getCampana().getTiendasExcluidas() == null ||
                            cp.getCampana().getTiendasExcluidas().stream().noneMatch(t -> t.getId().equals(storeId)))
                    .toList();

            if (campanasFiltradas.isEmpty()) {
                String nomCampanas = campanasActivas.stream()
                        .map(cp -> cp.getCampana().getNombre())
                        .distinct()
                        .collect(java.util.stream.Collectors.joining(", "));
                return ResponseEntity.badRequest().body(Map.of("error",
                        "La tienda '" + storeName + "' no participa de la campaña '" + nomCampanas + "'. Consulta las tiendas habilitadas con tu supervisor."));
            }
            campanasActivas = campanasFiltradas;
        }

        if (campanasActivas.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "El modelo '" + producto.getNombre() + "' no cuenta con ninguna campaña de bonos vigente en este momento. Consulta las campañas activas con tu supervisor."));
        }

        int totalPuntos = campanasActivas.stream().mapToInt(CampanaProducto::getPuntos).sum();
        int totalBono = campanasActivas.stream().mapToInt(CampanaProducto::getBonoBs).sum();
        int totalBonoTienda = campanasActivas.stream()
                .mapToInt(cp -> cp.getBonoBsTienda() != null ? cp.getBonoBsTienda() : 0)
                .sum();
        int totalPuntosTienda = campanasActivas.stream()
                .mapToInt(cp -> cp.getPuntosTienda() != null ? cp.getPuntosTienda() : 0)
                .sum();

        LocalDate fechaVenta = (saleDate != null && !saleDate.isBlank())
                ? LocalDate.parse(saleDate)
                : LocalDate.now();

        // Validar que la fecha sea de la semana actual (lunes a hoy) o según
        // configuración
        LocalDate today = LocalDate.now();
        int maxSemanasAtras = 0;
        try {
            maxSemanasAtras = Integer.parseInt(configuracionGlobalRepository
                    .findById("venta_fecha_max_semanas")
                    .map(com.ninabit.bono.modules.configuracion.ConfiguracionGlobal::getValor)
                    .orElse("0"));
        } catch (Exception e) {
            log.warn("Error parseando venta_fecha_max_semanas, usando 0");
        }

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(maxSemanasAtras);

        if (fechaVenta.isBefore(monday)) {
            String msg = maxSemanasAtras == 0
                    ? "Solo se permiten ventas de la semana actual (a partir del lunes " + monday + ")."
                    : "Solo se permiten ventas de hasta " + (maxSemanasAtras + 1) + " semanas atrás (a partir del lunes "
                            + monday + ").";
            return ResponseEntity.badRequest().body(Map.of("error", "Fecha no permitida: " + msg));
        }
        if (fechaVenta.isAfter(today)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Fecha no válida: No es posible registrar ventas con fecha futura. Por favor, selecciona la fecha de hoy o la fecha en que se realizó la venta."));
        }

        String year = String.valueOf(fechaVenta.getYear());
        String subDir = "vendedores/" + year + "/SERIAL_" + serial.trim().replaceAll("\\s+", "_");

        log.info("Iniciando guardado de fotos para serial {} en subDir {}", serial, subDir);
        // Guardar fotos
        String tagPath;
        String polizaPath;
        String notaPath;
        try {
            tagPath = saveFile(fotoTag, subDir, "tag_poliza");
            polizaPath = saveFile(fotoPoliza, subDir, "poliza_garantia");
            notaPath = saveFile(fotoNota, subDir, "nota_venta_vendedor");
            log.info("Fotos guardadas exitosamente: {}, {}, {}", tagPath, polizaPath, notaPath);
        } catch (IOException e) {
            log.error("Error guardando archivos para serial " + serial, e);
            return ResponseEntity.internalServerError().body(Map.of("error",
                    "No se pudieron guardar las fotografías de respaldo en el servidor. Por favor, intenta tomando fotos con menor resolución."));
        }

        Venta venta = Venta.builder()
                .vendedorId(vendedor.getId())
                .vendorName(vendedor.getNombreCompleto())
                .tiendaId(storeId)
                .storeName(storeName)
                .storeCode(storeCode)
                .storeCity(storeCity)
                .storeDepartment(storeDept)
                .storeChannel(storeChannel)
                .productoId(producto.getId())
                .productType(
                        producto.getTipoProducto() != null ? producto.getTipoProducto().getNombre() : "Desconocido")
                .productModel(producto.getModelo() != null ? producto.getModelo() : producto.getModeloCodigo())
                .productSize(
                        (producto.getTamanoPulgadas() != null ? producto.getTamanoPulgadas() : producto.getPulgadas())
                                + "\"")
                .serial(serial)
                .saleDate(fechaVenta)
                .puntos(totalPuntos)
                .bonoBs(totalBono)
                .bonoBsTienda(totalBonoTienda)
                .puntosTienda(totalPuntosTienda)
                .ciudad(storeCity)
                .fotoTag(tagPath)
                .fotoPoliza(polizaPath)
                .fotoNota(notaPath)
                .build();

        Venta saved = ventaRepository.save(venta);

        for (CampanaProducto cp : campanasActivas) {
            ventaCampanaRepository.save(VentaCampana.builder()
                    .venta(saved)
                    .campana(cp.getCampana())
                    .puntosGanados(cp.getPuntos())
                    .bonoGanado(cp.getBonoBs())
                    .puntosTiendaGanados(cp.getPuntosTienda() != null ? cp.getPuntosTienda() : 0)
                    .bonoTiendaGanado(cp.getBonoBsTienda() != null ? cp.getBonoBsTienda() : 0)
                    .build());
        }

        // Marcar serial como usado en Bono Vendedor
        ser.setEstado(Serial.Estado.USADO);
        ser.setRegistroVendedorId(saved.getId());
        ser.setFechaRegistroVendedor(java.time.LocalDateTime.now());
        serialRepository.save(ser);

        auditoriaService.log(auth.getName(), "REGISTRO_VENTA",
                "Venta registrada: serial=" + serial + " en tienda=" + storeName, "Venta", saved.getId().toString());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "Aprobar venta")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Venta> approve(@PathVariable Long id, Authentication auth) {
        return ventaRepository.findById(id).map(v -> {
            if (v.getEstado() == Venta.Estado.APROBADA) {
                // Idempotencia: Si ya está aprobada, retornar inmediatamente sin duplicar bonos ni correos
                return ResponseEntity.ok(v);
            }
            v.setEstado(Venta.Estado.APROBADA);
            v.setFechaRevision(LocalDateTime.now());
            Venta saved = ventaRepository.save(v);
            log.info("Usuario {} APROBÓ la venta ID {} del vendedor {} (Serial {})", auth.getName(), id,
                    v.getVendorName(), v.getSerial());
            auditoriaService.log(auth.getName(), "APROBACION_VENTA",
                    "Venta aprobada: " + id, "Venta", id.toString());

            // Emitir ticket digital de vendedor y registrar movimiento contable de puntos por campaña (§6 y §7)
            try {
                puntosVendedorService.emitirTicketVendedorSiAplica(saved);
                List<VentaCampana> vcs = ventaCampanaRepository.findByVentaId(saved.getId());
                Long campId = null;
                String campNom = null;
                if (!vcs.isEmpty() && vcs.get(0).getCampana() != null) {
                    campId = vcs.get(0).getCampana().getId();
                    campNom = vcs.get(0).getCampana().getNombre();
                }

                final Long finalCampId = campId;
                final String finalCampNom = campNom;
                vendedorRepository.findById(v.getVendedorId()).ifPresent(vendedor -> {
                    int saldo = puntosVendedorService.getSaldoPuntos(vendedor.getId(), finalCampId);
                    com.ninabit.bono.modules.vendorprize.MovimientoPuntos mov = com.ninabit.bono.modules.vendorprize.MovimientoPuntos.builder()
                            .vendedor(vendedor)
                            .tipo(com.ninabit.bono.modules.vendorprize.MovimientoPuntos.TipoMovimiento.ABONO_VENTA)
                            .puntos(v.getPuntos() != null ? v.getPuntos() : 0)
                            .saldoResultante(saldo)
                            .referenciaId(saved.getId())
                            .campanaId(finalCampId)
                            .campanaNombre(finalCampNom)
                            .descripcion("Abono por venta aprobada #" + saved.getId() + " (" + saved.getProductModel() + ")")
                            .fecha(LocalDateTime.now())
                            .usuario(auth.getName())
                            .build();
                    movimientoPuntosRepository.save(mov);
                });
            } catch (Exception e) {
                log.error("Error registrando puntos o emitiendo ticket de venta: {}", e.getMessage());
            }

            // Notificar al vendedor
            try {
                vendedorRepository.findById(v.getVendedorId()).ifPresent(vendedor -> {
                    String subject = "Venta Aprobada - Skyworth";
                    String text = String.format(
                            "Hola %s,\n\n" +
                                    "Tu registro de venta ha sido APROBADO.\n\n" +
                                    "- Producto: %s\n" +
                                    "- Serie: %s\n" +
                                    "- Fecha: %s\n\n" +
                                    "¡Felicidades! Los puntos y bonos han sido sumados a tu cuenta.\n\n" +
                                    "Equipo Skyworth",
                            vendedor.getNombreCompleto(),
                            v.getProductModel(),
                            v.getSerial(),
                            v.getSaleDate());
                    emailService.sendEmail(vendedor.getEmail(), subject, text);
                });
            } catch (Exception e) {
                log.error("Error enviando correo de aprobación: {}", e.getMessage());
            }

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "Rechazar venta")
    public ResponseEntity<Venta> reject(@PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String motivo = body.getOrDefault("motivo", "");
        if (motivo.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ventaRepository.findById(id).map(v -> {
            boolean estabaAprobada = (v.getEstado() == Venta.Estado.APROBADA);
            v.setEstado(Venta.Estado.RECHAZADA);
            v.setMotivoRechazo(motivo);
            v.setFechaRevision(LocalDateTime.now());

            if (estabaAprobada) {
                List<VentaCampana> detalles = ventaCampanaRepository.findByVentaId(id);
                int totalBonoPagado = detalles.stream()
                        .filter(vc -> vc.getPago() != null)
                        .mapToInt(vc -> vc.getBonoGanado() != null ? vc.getBonoGanado() : 0)
                        .sum();

                if (totalBonoPagado > 0) {
                    com.ninabit.bono.modules.liquidation.IncidenciaRegularizacion inc = com.ninabit.bono.modules.liquidation.IncidenciaRegularizacion.builder()
                            .tipo(com.ninabit.bono.modules.liquidation.IncidenciaRegularizacion.TipoIncidencia.VENTA_PAGADA_ANULADA)
                            .ventaId(v.getId())
                            .serialAfectado(v.getSerial())
                            .vendedorId(v.getVendedorId())
                            .vendedorNombre(v.getVendorName())
                            .tiendaId(v.getTiendaId())
                            .tiendaNombre(v.getStoreName())
                            .montoACompensar(java.math.BigDecimal.valueOf(totalBonoPagado))
                            .saldoPendiente(java.math.BigDecimal.valueOf(totalBonoPagado))
                            .estado(com.ninabit.bono.modules.liquidation.IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE)
                            .fechaRegistro(LocalDateTime.now())
                            .observaciones("Venta previamente APROBADA y PAGADA fue RECHAZADA. Motivo: " + motivo)
                            .build();
                    incidenciaRegularizacionRepository.save(inc);
                    log.warn("Generada Incidencia de Regularización por compensación para venta {}: Bs {}", id, totalBonoPagado);
                }
            }

            // Acumular en historial de rechazos
            List<Map<String, Object>> listaHistorial = new ArrayList<>();
            String historialExistente = v.getHistorialRechazos();
            if (historialExistente != null && !historialExistente.isBlank()) {
                try {
                    List<Map<String, Object>> parsed = objectMapper.readValue(
                            historialExistente,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                    if (parsed != null) {
                        listaHistorial.addAll(parsed);
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo deserializar historialRechazos existente para venta {}: {}", id, ex.getMessage());
                }
            }
            Map<String, Object> nuevoRechazo = new HashMap<>();
            nuevoRechazo.put("fecha", LocalDateTime.now().toString());
            nuevoRechazo.put("motivo", motivo);
            nuevoRechazo.put("revisor", auth != null ? auth.getName() : "SISTEMA");
            listaHistorial.add(nuevoRechazo);

            try {
                v.setHistorialRechazos(objectMapper.writeValueAsString(listaHistorial));
            } catch (Exception ex) {
                log.error("Error al serializar historialRechazos para venta {}: {}", id, ex.getMessage());
            }

            Venta saved = ventaRepository.save(v);

            // Liberar serial para Bono Vendedor (manteniendo Cliente Final intacto si existe)
            serialRepository.findBySerial(v.getSerial()).ifPresent(s -> {
                s.setRegistroVendedorId(null);
                s.setFechaRegistroVendedor(null);
                if (s.getRegistroCompradorId() == null) {
                    s.setEstado(Serial.Estado.DISPONIBLE);
                }
                serialRepository.save(s);
            });

            log.info("Usuario {} RECHAZÓ la venta ID {} del vendedor {} (Serial {}) - Motivo: {}", auth.getName(), id,
                    v.getVendorName(), v.getSerial(), motivo);
            auditoriaService.log(auth.getName(), "RECHAZO_VENTA",
                    "Venta rechazada: " + id + " | Motivo: " + motivo, "Venta", id.toString());

            // Notificar al vendedor
            try {
                vendedorRepository.findById(v.getVendedorId()).ifPresent(vendedor -> {
                    String subject = "Venta Rechazada - Skyworth";
                    String text = String.format(
                            "Hola %s,\n\n" +
                                    "Tu registro de venta ha sido RECHAZADO.\n\n" +
                                    "- Producto: %s\n" +
                                    "- Serie: %s\n" +
                                    "- Fecha: %s\n" +
                                    "- Motivo del rechazo: %s\n\n" +
                                    "Por favor, verifica la información e intenta registrarla nuevamente si corresponde.\n\n" +
                                    "Equipo Skyworth",
                            vendedor.getNombreCompleto(),
                            v.getProductModel(),
                            v.getSerial(),
                            v.getSaleDate(),
                            motivo);
                    emailService.sendEmail(vendedor.getEmail(), subject, text);
                });
            } catch (Exception e) {
                log.error("Error enviando correo de rechazo: {}", e.getMessage());
            }

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/observe")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Observar venta para subsanación por el vendedor")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> observe(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String motivo = body.getOrDefault("motivo", "").trim();
        String archivosObservados = body.getOrDefault("archivosObservados", "POLIZA,TAG,NOTA").trim();
        if (motivo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El motivo de observación es obligatorio"));
        }

        return ventaRepository.findById(id).map(v -> {
            v.setEstado(Venta.Estado.OBSERVADA);
            v.setMotivoObservacion(motivo);
            v.setArchivosObservados(archivosObservados);
            v.setFechaObservacion(LocalDateTime.now());

            // Agregar al historial de observaciones / rechazos
            List<Map<String, Object>> listaHistorial = new ArrayList<>();
            String historialExistente = v.getHistorialRechazos();
            if (historialExistente != null && !historialExistente.isBlank()) {
                try {
                    List<Map<String, Object>> parsed = objectMapper.readValue(
                            historialExistente,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                    if (parsed != null) {
                        listaHistorial.addAll(parsed);
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo deserializar historial para venta {}: {}", id, ex.getMessage());
                }
            }
            Map<String, Object> nuevaObs = new HashMap<>();
            nuevaObs.put("tipo", "OBSERVACION");
            nuevaObs.put("fecha", LocalDateTime.now().toString());
            nuevaObs.put("motivo", motivo);
            nuevaObs.put("archivos", archivosObservados);
            nuevaObs.put("revisor", auth != null ? auth.getName() : "SISTEMA");
            listaHistorial.add(nuevaObs);

            try {
                v.setHistorialRechazos(objectMapper.writeValueAsString(listaHistorial));
            } catch (Exception ex) {
                log.error("Error al serializar historial en observación para venta {}: {}", id, ex.getMessage());
            }

            Venta saved = ventaRepository.save(v);

            auditoriaService.log(auth.getName(), "OBSERVACION_VENTA",
                    "Venta observada: " + id + ". Motivo: " + motivo + " | Archivos requeridos: " + archivosObservados,
                    "Venta", id.toString());

            // Notificar al vendedor por email (§8 Informe Complementario: indicación concreta y enlace directo)
            try {
                vendedorRepository.findById(v.getVendedorId()).ifPresent(vendedor -> {
                    StringBuilder filesDesc = new StringBuilder();
                    for (String f : archivosObservados.split(",")) {
                        String clean = f.trim().toUpperCase();
                        if (clean.equals("POLIZA")) filesDesc.append("• Póliza de Garantía debidamente llenada\n");
                        else if (clean.equals("TAG")) filesDesc.append("• Fotografía nítida del Serial / TAG del televisor\n");
                        else if (clean.equals("NOTA")) filesDesc.append("• Factura o Nota de Venta correspondiente\n");
                        else filesDesc.append("• ").append(clean).append("\n");
                    }

                    String subject = "Venta Observada - Requiere Subsanación - Skyworth";
                    String text = String.format(
                            "Estimado(a) %s,\n\n" +
                            "Tu registro de venta para el producto %s (Serial %s) ha sido OBSERVADO.\n\n" +
                            "Motivo de observación:\n%s\n\n" +
                            "Documentos que debes sustituir:\n%s\n" +
                            "Para subsanar y reenviar tu venta de inmediato desde tu teléfono o computador, ingresa a:\n" +
                            "https://skyworth.ninabit.com/mis-ventas?search=%s\n\n" +
                            "Una vez sustituido el respaldo, tu venta entrará a revisión prioritaria.\n\n" +
                            "Atentamente,\n" +
                            "Equipo de Revisión y Liquidación Skyworth",
                            vendedor.getNombreCompleto(),
                            v.getProductModel(),
                            v.getSerial(),
                            motivo,
                            filesDesc.toString(),
                            v.getSerial());
                    emailService.sendEmail(vendedor.getEmail(), subject, text);
                });
            } catch (Exception e) {
                log.error("Error enviando email de observación: {}", e.getMessage());
            }

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @RequestMapping(value = "/{id}/resubmit", method = { RequestMethod.PUT, RequestMethod.POST }, consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    @Operation(summary = "Subsanar venta observada subiendo nuevos respaldos")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> resubmit(
            @PathVariable Long id,
            @RequestParam(required = false) MultipartFile fotoTag,
            @RequestParam(required = false) MultipartFile fotoPoliza,
            @RequestParam(required = false) MultipartFile fotoNota,
            Authentication auth) throws IOException {

        java.util.Optional<Venta> ventaOpt = ventaRepository.findById(id);
        if (ventaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Venta v = ventaOpt.get();
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);

        // Si es vendedor, verificar que la venta le pertenezca
        if (vendedor != null && !v.getVendedorId().equals(vendedor.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para modificar esta venta"));
        }

        if (v.getEstado() != Venta.Estado.OBSERVADA && v.getEstado() != Venta.Estado.PENDIENTE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se pueden subsanar ventas en estado OBSERVADA o PENDIENTE"));
        }

        if ((fotoTag == null || fotoTag.isEmpty()) &&
            (fotoPoliza == null || fotoPoliza.isEmpty()) &&
            (fotoNota == null || fotoNota.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes adjuntar al menos una fotografía para subsanar"));
        }

        // Historial de evidencias anteriores
        List<Map<String, Object>> evidencias = new ArrayList<>();
        if (v.getEvidenciasAnteriores() != null && !v.getEvidenciasAnteriores().isBlank()) {
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        v.getEvidenciasAnteriores(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                if (parsed != null) {
                    evidencias.addAll(parsed);
                }
            } catch (Exception ex) {
                log.warn("Error leyendo evidenciasAnteriores: {}", ex.getMessage());
            }
        }

        Map<String, Object> evidenciaAnterior = new HashMap<>();
        evidenciaAnterior.put("fechaReemplazo", LocalDateTime.now().toString());
        evidenciaAnterior.put("fotoTagAnterior", v.getFotoTag());
        evidenciaAnterior.put("fotoPolizaAnterior", v.getFotoPoliza());
        evidenciaAnterior.put("fotoNotaAnterior", v.getFotoNota());
        evidenciaAnterior.put("motivoObservacion", v.getMotivoObservacion());

        String year = String.valueOf(v.getSaleDate() != null ? v.getSaleDate().getYear() : LocalDate.now().getYear());
        String subDir = "vendedores/" + year + "/SERIAL_" + v.getSerial().replaceAll("\\s+", "_");

        List<String> sustituidos = new ArrayList<>();
        if (fotoTag != null && !fotoTag.isEmpty()) {
            String newTag = saveFile(fotoTag, subDir, "tag_poliza_subsanado_" + System.currentTimeMillis());
            v.setFotoTag(newTag);
            sustituidos.add("TAG");
        }
        if (fotoPoliza != null && !fotoPoliza.isEmpty()) {
            String newPoliza = saveFile(fotoPoliza, subDir, "poliza_garantia_subsanada_" + System.currentTimeMillis());
            v.setFotoPoliza(newPoliza);
            sustituidos.add("POLIZA");
        }
        if (fotoNota != null && !fotoNota.isEmpty()) {
            String newNota = saveFile(fotoNota, subDir, "nota_venta_subsanada_" + System.currentTimeMillis());
            v.setFotoNota(newNota);
            sustituidos.add("NOTA");
        }

        evidenciaAnterior.put("archivosSustituidos", sustituidos);
        evidencias.add(evidenciaAnterior);

        try {
            v.setEvidenciasAnteriores(objectMapper.writeValueAsString(evidencias));
        } catch (Exception ex) {
            log.error("Error guardando evidenciasAnteriores: {}", ex.getMessage());
        }

        // Vuelve a estar en cola de revisión
        v.setEstado(Venta.Estado.PENDIENTE);
        v.setFechaSubsanacion(LocalDateTime.now());
        Venta saved = ventaRepository.save(v);

        auditoriaService.log(auth.getName(), "SUBSANACION_VENTA",
                "Venta " + id + " subsanada por vendedor. Archivos reemplazados: " + String.join(", ", sustituidos),
                "Venta", id.toString());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/update-store")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Corregir tienda asignada a una venta antes de aprobar")
    public ResponseEntity<?> updateSaleStore(
            @PathVariable Long id,
            @RequestParam Long tiendaId,
            @RequestParam(required = false) String motivo,
            Authentication auth) {
        com.ninabit.bono.modules.tienda.Tienda tienda = tiendaRepository.findById(tiendaId).orElse(null);
        if (tienda == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tienda no encontrada"));
        }

        return ventaRepository.findById(id).map(v -> {
            String tiendaAnterior = v.getStoreName();
            v.setTiendaId(tienda.getId());
            v.setStoreName(tienda.getNombre());
            v.setStoreCode(tienda.getCodigo());
            v.setStoreCity(tienda.getCiudad() != null ? tienda.getCiudad().getNombre() : v.getCiudad());
            v.setStoreDepartment(tienda.getDepartamento());
            v.setStoreChannel(tienda.getCanal());
            if (tienda.getCiudad() != null) {
                v.setCiudad(tienda.getCiudad().getNombre());
            }
            Venta saved = ventaRepository.save(v);

            auditoriaService.log(auth.getName(), "CORRECCION_TIENDA_VENTA",
                    "Tienda cambiada de '" + tiendaAnterior + "' a '" + tienda.getNombre() + "' en venta ID " + id + ". Motivo: " + (motivo != null ? motivo : "Sin motivo especificado"),
                    "Venta", id.toString());

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/revert-to-pending")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "Revertir venta a estado PENDIENTE para reevaluación")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> revertToPending(@PathVariable Long id, Authentication auth) {
        return ventaRepository.findById(id).map(v -> {
            // Si el serial estaba liberado o disponible, verificar si está disponible para re-reservarlo
            java.util.Optional<Serial> serialOpt = serialRepository.findBySerial(v.getSerial());
            if (serialOpt.isPresent()) {
                Serial s = serialOpt.get();
                // Si otro registro diferente está usando el serial en Bono Vendedor
                if (s.getRegistroVendedorId() != null && !s.getRegistroVendedorId().equals(v.getId())) {
                    return ResponseEntity.status(409).body(Map.of("error", "No se puede revertir a pendiente: el serial " + v.getSerial() + " ya está siendo utilizado por otra venta activa de vendedor."));
                }
                s.setEstado(Serial.Estado.USADO);
                s.setRegistroVendedorId(v.getId());
                s.setFechaRegistroVendedor(v.getCreatedAt() != null ? v.getCreatedAt() : LocalDateTime.now());
                serialRepository.save(s);
            }

            v.setEstado(Venta.Estado.PENDIENTE);
            v.setMotivoRechazo(null);
            v.setFechaRevision(null);
            Venta saved = ventaRepository.save(v);

            log.info("Usuario {} RESTABLECIÓ a PENDIENTE la venta ID {} del vendedor {} (Serial {})",
                    auth.getName(), id, v.getVendorName(), v.getSerial());
            auditoriaService.log(auth.getName(), "REVERTIR_VENTA_PENDIENTE",
                    "Venta restablecida a estado PENDIENTE: " + id + " | Serial: " + v.getSerial(), "Venta", id.toString());

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/deletion-impact")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Analizar el impacto previo a la eliminación/baja de una venta")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getDeletionImpact(@PathVariable Long id) {
        return ventaRepository.findById(id).map(v -> {
            boolean isPaid = v.getDetallesCampanas() != null && v.getDetallesCampanas().stream().anyMatch(dc -> dc.getPago() != null);
            Long pagoId = (v.getDetallesCampanas() != null) ? v.getDetallesCampanas().stream()
                    .filter(dc -> dc.getPago() != null)
                    .map(dc -> dc.getPago().getId())
                    .findFirst()
                    .orElse(null) : null;

            boolean canDelete = !isPaid;
            String blockingReason = null;
            if (isPaid) {
                blockingReason = "Esta venta está asociada al Pago/Liquidación ID #" + pagoId + ". No puede eliminarse directamente del sistema; debe tramitarse como una incidencia de regularización administrativa compensatoria.";
            }

            List<String> warnings = new ArrayList<>();
            warnings.add("El serial " + v.getSerial() + " será liberado para futuros registros de vendedores.");
            warnings.add("Las evidencias fotográficas registradas en el servidor serán eliminadas de forma permanente.");
            if (v.getEstado() == Venta.Estado.APROBADA) {
                warnings.add("La venta se encuentra en estado APROBADA con Bs " + v.getBonoBs() + " y " + v.getPuntos() + " puntos acumulados que serán descontados de los balances.");
            }

            Optional<Serial> serialOpt = serialRepository.findBySerial(v.getSerial());
            boolean usedByBuyer = serialOpt.map(s -> s.getRegistroCompradorId() != null).orElse(false);
            if (usedByBuyer) {
                warnings.add("El serial también fue registrado por un cliente final. El registro del cliente final permanecerá INTACTO.");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("saleId", v.getId());
            response.put("serial", v.getSerial());
            response.put("estado", v.getEstado().name());
            response.put("vendorName", v.getVendorName());
            response.put("ciudad", v.getCiudad());
            response.put("bonoBs", v.getBonoBs());
            response.put("puntos", v.getPuntos());
            response.put("isPaid", isPaid);
            response.put("pagoId", pagoId);
            response.put("canDelete", canDelete);
            response.put("blockingReason", blockingReason);
            response.put("warnings", warnings);
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/full-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Eliminar completamente una venta de forma controlada y auditada (exclusivo ADMIN)")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> fullDelete(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {

        String motivoFinal = (motivo != null && !motivo.isBlank())
                ? motivo
                : (body != null ? body.get("motivo") : null);

        if (motivoFinal == null || motivoFinal.trim().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El motivo de baja es obligatorio para auditoría."));
        }

        return ventaRepository.findById(id).map(v -> {
            // Validación de Blindaje: No se puede eliminar si está asociada a un pago liquidado
            boolean isPaid = v.getDetallesCampanas() != null && v.getDetallesCampanas().stream().anyMatch(dc -> dc.getPago() != null);
            if (isPaid) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                        .body(Map.of("error", "Venta asociada a liquidación pagada. Debe tramitarse como incidencia de regularización."));
            }

            // 1. Resetear serial para Bono Vendedor (manteniendo Cliente Final intacto si existe)
            serialRepository.findBySerial(v.getSerial()).ifPresent(s -> {
                s.setRegistroVendedorId(null);
                s.setFechaRegistroVendedor(null);
                if (s.getRegistroCompradorId() == null) {
                    s.setEstado(Serial.Estado.DISPONIBLE);
                }
                serialRepository.save(s);
                log.info("Serial {} liberado para Bono Vendedor tras baja controlada de venta {}", v.getSerial(), id);
            });

            // 2. Eliminar archivos de imagen
            try {
                String subDir = "vendedores/" + v.getSaleDate().getYear() + "/SERIAL_"
                        + v.getSerial().replaceAll("\\s+", "_");
                Path targetDirPath = Paths.get(uploadDir, subDir);
                if (Files.exists(targetDirPath)) {
                    try (java.util.stream.Stream<Path> walk = Files.walk(targetDirPath)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        log.error("Error eliminando archivo/directorio: " + path, e);
                                    }
                                });
                    }
                    log.info("Archivos de imagen eliminados en: {}", targetDirPath);
                }
            } catch (Exception e) {
                log.error("Error al intentar eliminar directorio de imágenes", e);
            }

            // 3. Eliminar tickets de vendedor asociados si existen (para evitar violación de FK)
            try {
                ticketVendedorRepository.findByVentaId(id).ifPresent(ticketVendedorRepository::delete);
            } catch (Exception e) {
                log.warn("No se pudieron eliminar tickets vinculados a venta {}: {}", id, e.getMessage());
            }

            // 4. Registrar auditoria con motivo y snapshot antes de borrar
            String snapshot = String.format("ID=%d, Serial=%s, Vendedor=%s, Ciudad=%s, Estado=%s, BonoBs=%d, Puntos=%d, MotivoBaja=%s",
                    id, v.getSerial(), v.getVendorName(), v.getCiudad(), v.getEstado(), v.getBonoBs(), v.getPuntos(), motivoFinal.trim());
            auditoriaService.log(auth.getName(), "BAJA_VENTA_CONTROLADA", snapshot, "Venta", id.toString());

            // 5. Borrar la venta (cascada a VentaCampana está configurada en la entidad)
            ventaRepository.delete(v);

            log.info("Venta ID {} eliminada con baja controlada por {}. Motivo: {}", id, auth.getName(), motivoFinal.trim());
            return ResponseEntity.ok(Map.of("message", "Venta dada de baja correctamente y serial liberado"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String saveFile(MultipartFile file, String subDir, String fixedFilename) throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = ".jpg"; // Default to .jpg as requested or if missing
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String filename = fixedFilename + extension;
        Path targetDirPath = Paths.get(uploadDir, subDir);
        Files.createDirectories(targetDirPath);

        Path filePath = targetDirPath.resolve(filename);
        Files.write(filePath, file.getBytes());

        return "uploads/" + subDir + "/" + filename;
    }
}

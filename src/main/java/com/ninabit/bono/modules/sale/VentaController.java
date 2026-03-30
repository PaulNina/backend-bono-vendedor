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

    @Value("${app.upload.dir}")
    private String uploadDir;

    private List<Venta> populateTransientFields(List<Venta> ventas) {
        ventas.forEach(v -> {
            vendedorRepository.findById(v.getVendedorId()).ifPresent(vendedor -> {
                // Always overwrite with source of truth from Vendedor table
                v.setVendorName(vendedor.getNombreCompleto());

                v.setStoreName(vendedor.getTienda());
                v.setVendorPhone(vendedor.getTelefono());
                v.setVendorEmail(vendedor.getEmail());
                v.setVendorCi(vendedor.getCi());
            });
        });
        return ventas;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Operation(summary = "Listar todas las ventas con paginación y filtros")
    public org.springframework.data.domain.Page<Venta> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String dateType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Authentication auth) {

        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        boolean isReviewer = usuario.getRol() == Usuario.Rol.REVIEWER;
        List<String> allowedCities = isReviewer && usuario.getCiudad() != null && !usuario.getCiudad().isBlank()
                ? Arrays.stream(usuario.getCiudad().split(",")).map(String::trim).collect(Collectors.toList())
                : null;

        if (isReviewer && allowedCities != null && allowedCities.isEmpty()) {
            return org.springframework.data.domain.Page.empty();
        }

        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc")
                ? org.springframework.data.domain.Sort.by(sortBy).descending()
                : org.springframework.data.domain.Sort.by(sortBy).ascending();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                sort);

        org.springframework.data.jpa.domain.Specification<Venta> spec = VentaSpecification.filterBy(search, city,
                status, startDate, endDate, campanaId, dateType);

        // Add reviewer city restriction to spec if applicable
        if (allowedCities != null) {
            org.springframework.data.jpa.domain.Specification<Venta> citySpec = (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                List<jakarta.persistence.criteria.Predicate> cityPredicates = new ArrayList<>();
                for (String c : allowedCities) {
                    cityPredicates.add(cb.equal(cb.lower(root.get("ciudad")), c.toLowerCase().trim()));
                }
                predicates.add(cb.or(cityPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            spec = spec.and(citySpec);
        }

        org.springframework.data.domain.Page<Venta> pageResult = ventaRepository.findAll(spec, pageable);

        // Populate transient fields for the current page
        populateTransientFields(pageResult.getContent());

        return pageResult;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Operation(summary = "Obtener estadísticas de ventas con filtros")
    public Map<String, Long> getStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String dateType,
            Authentication auth) {

        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        boolean isReviewer = usuario.getRol() == Usuario.Rol.REVIEWER;
        List<String> allowedCities = isReviewer && usuario.getCiudad() != null && !usuario.getCiudad().isBlank()
                ? Arrays.stream(usuario.getCiudad().split(",")).map(String::trim).collect(Collectors.toList())
                : null;

        org.springframework.data.jpa.domain.Specification<Venta> baseSpec = VentaSpecification.filterBy(search, city,
                null, startDate, endDate, campanaId, dateType);

        if (allowedCities != null) {
            org.springframework.data.jpa.domain.Specification<Venta> citySpec = (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> cityPredicates = new ArrayList<>();
                for (String c : allowedCities) {
                    cityPredicates.add(cb.equal(cb.lower(root.get("ciudad")), c.toLowerCase().trim()));
                }
                return cb.or(cityPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            baseSpec = baseSpec.and(citySpec);
        }

        Map<String, Long> stats = new HashMap<>();

        final org.springframework.data.jpa.domain.Specification<Venta> finalSpec = baseSpec;
        stats.put("total", ventaRepository.count(finalSpec));
        stats.put("pending", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.PENDIENTE))));
        stats.put("approved", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.APROBADA))));
        stats.put("rejected", ventaRepository
                .count(finalSpec.and((root, query, cb) -> cb.equal(root.get("estado"), Venta.Estado.RECHAZADA))));

        return stats;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Listar ventas pendientes")
    public List<Venta> getPending(@RequestParam(required = false) String city, Authentication auth) {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();
        boolean isReviewer = usuario.getRol() == Usuario.Rol.REVIEWER;
        List<String> allowedCities = isReviewer && usuario.getCiudad() != null && !usuario.getCiudad().isBlank()
                ? Arrays.stream(usuario.getCiudad().split(",")).map(String::trim).collect(Collectors.toList())
                : (isReviewer ? List.of() : null);

        List<Venta> ventas;
        if (city != null && !city.isBlank()) {
            ventas = ventaRepository.findByEstadoAndCiudad(Venta.Estado.PENDIENTE, city);
        } else {
            ventas = ventaRepository.findByEstado(Venta.Estado.PENDIENTE);
        }

        if (isReviewer) {
            if (allowedCities.isEmpty())
                return List.of(); // Si no tiene ciudades asignadas, no ve nada.
            ventas = ventas.stream()
                    .filter(v -> allowedCities.stream()
                            .anyMatch(c -> c.equalsIgnoreCase(v.getCiudad() != null ? v.getCiudad().trim() : "")))
                    .collect(Collectors.toList());
        }

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
    public ResponseEntity<?> create(
            @RequestParam Long productoId,
            @RequestParam String serial,
            @RequestParam(required = false) String saleDate,
            @RequestParam MultipartFile fotoTag,
            @RequestParam MultipartFile fotoPoliza,
            @RequestParam MultipartFile fotoNota,
            Authentication auth) throws IOException {

        // validar serial - comprueba tanto estado legacy como nuevos campos
        Serial ser = serialRepository.findBySerial(serial)
                .orElse(null);
        if (ser == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Serial no encontrado"));
        }
        Serial.Estado estadoSer = ser.getEstado() != null ? ser.getEstado() : Serial.Estado.DISPONIBLE;
        if (ser.isBloqueado()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Serial inválido: fue utilizado en campañas anteriores"));
        }
        if (ser.getRegistroVendedorId() != null) {
            String fecha = ser.getFechaRegistroVendedor() != null
                    ? ser.getFechaRegistroVendedor().toLocalDate().toString()
                    : "fecha desconocida";
            return ResponseEntity.badRequest().body(Map.of("error", "Serial ya fue utilizado el " + fecha));
        }
        if (estadoSer != Serial.Estado.DISPONIBLE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Serial no disponible"));
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

        List<CampanaProducto> campanasActivas = campanaProductoRepository
                .findByProductoIdAndActivoTrue(producto.getId());
        if (campanasActivas.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El producto no aplica a ninguna campaña activa"));
        }

        int totalPuntos = campanasActivas.stream().mapToInt(CampanaProducto::getPuntos).sum();
        int totalBono = campanasActivas.stream().mapToInt(CampanaProducto::getBonoBs).sum();

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
                    ? "solo se permiten ventas de la semana actual (desde el lunes " + monday + ")"
                    : "solo se permiten ventas de hasta " + (maxSemanasAtras + 1) + " semanas atrás (desde el lunes "
                            + monday + ")";
            return ResponseEntity.badRequest().body(Map.of("error", "Fecha inválida: " + msg));
        }
        if (fechaVenta.isAfter(today)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Fecha inválida: no se pueden registrar ventas con fecha futura"));
        }

        String year = String.valueOf(fechaVenta.getYear());
        String subDir = "vendedores/" + year + "/SERIAL_" + serial.replaceAll("\\s+", "_");

        log.info("Iniciando guardado de fotos para serial {} en subDir {}", serial, subDir);
        // Guardar fotos
        String tagPath = saveFile(fotoTag, subDir, "tag_poliza");
        String polizaPath = saveFile(fotoPoliza, subDir, "poliza_garantia");
        String notaPath = saveFile(fotoNota, subDir, "nota_venta_vendedor");
        log.info("Fotos guardadas exitosamente: {}, {}, {}", tagPath, polizaPath, notaPath);

        Venta venta = Venta.builder()
                .vendedorId(vendedor.getId())
                .vendorName(vendedor.getNombreCompleto())
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
                .ciudad(vendedor.getCiudad() != null ? vendedor.getCiudad().getNombre() : "Desconocida")
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
                    .build());
        }

        // Marcar serial como usado (campos nuevo + legacy)
        ser.setEstado(Serial.Estado.USADO);
        ser.setRegistroVendedorId(saved.getId());
        ser.setFechaRegistroVendedor(java.time.LocalDateTime.now());
        serialRepository.save(ser);

        auditoriaService.log(auth.getName(), "REGISTRO_VENTA",
                "Venta registrada: serial=" + serial, "Venta", saved.getId().toString());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "Aprobar venta")
    public ResponseEntity<Venta> approve(@PathVariable Long id, Authentication auth) {
        return ventaRepository.findById(id).map(v -> {
            v.setEstado(Venta.Estado.APROBADA);
            v.setFechaRevision(LocalDateTime.now());
            Venta saved = ventaRepository.save(v);
            log.info("Usuario {} APROBÓ la venta ID {} del vendedor {} (Serial {})", auth.getName(), id,
                    v.getVendorName(), v.getSerial());
            auditoriaService.log(auth.getName(), "APROBACION_VENTA",
                    "Venta aprobada: " + id, "Venta", id.toString());

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
            v.setEstado(Venta.Estado.RECHAZADA);
            v.setMotivoRechazo(motivo);
            v.setFechaRevision(LocalDateTime.now());
            Venta saved = ventaRepository.save(v);

            // Liberar serial
            serialRepository.findBySerial(v.getSerial()).ifPresent(s -> {
                s.setEstado(Serial.Estado.DISPONIBLE);
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

    @DeleteMapping("/{id}/full-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Eliminar completamente una venta (resetea serial y borra fotos)")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> fullDelete(@PathVariable Long id, Authentication auth) {
        return ventaRepository.findById(id).map(v -> {
            // 1. Resetear serial
            serialRepository.findBySerial(v.getSerial()).ifPresent(s -> {
                s.setEstado(Serial.Estado.DISPONIBLE);
                s.setRegistroVendedorId(null);
                s.setFechaRegistroVendedor(null);
                serialRepository.save(s);
                log.info("Serial {} reseteado tras eliminación de venta {}", v.getSerial(), id);
            });

            // 2. Eliminar archivos de imagen
            try {
                // La ruta base es SERIAL_{serial}
                String subDir = "vendedores/" + v.getSaleDate().getYear() + "/SERIAL_"
                        + v.getSerial().replaceAll("\\s+", "_");
                Path targetDirPath = Paths.get(uploadDir, subDir);
                if (Files.exists(targetDirPath)) {
                    // Borrar archivos dentro del directorio y el directorio mismo si está vacío
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

            // 3. Registrar auditoria antes de borrar
            auditoriaService.log(auth.getName(), "ELIMINACION_COMPLETA_VENTA",
                    "Venta eliminada completamente: ID=" + id + ", Serial=" + v.getSerial() + ", Vendedor="
                            + v.getVendorName(),
                    "Venta", id.toString());

            // 4. Borrar la venta (cascada a VentaCampana está configurada en la entidad)
            ventaRepository.delete(v);

            log.info("Venta ID {} eliminada completamente por {}", id, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Venta eliminada correctamente y serial liberado"));
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

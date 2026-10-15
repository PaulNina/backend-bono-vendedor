package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tiendas")
@RequiredArgsConstructor
@Tag(name = "Tiendas")
public class TiendaController {

    private final TiendaRepository tiendaRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;
    private final VendedorRepository vendedorRepository;
    private final VentaRepository ventaRepository;
    private final com.ninabit.bono.modules.city.CiudadRepository ciudadRepository;
    private final VendedorTiendaRepository vendedorTiendaRepository;
    private final com.ninabit.bono.modules.user.UsuarioRepository usuarioRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @lombok.Data
    public static class TiendaRequest {
        private String codigo;
        private String nombre;
        private String canal;
        private String departamento;
        private String nombrePropietario;
        private String encargadoNombre;
        private String encargadoTelefono;
        private String encargadoEmail;
        private String encargadoCi;
        private String qrPagoUrl;
        private String banco;
        private String nroCuenta;
        private String titularCobro;
        private String telefonoTitular;
        private String direccion;
        private String telefono;
        private Long ciudadId;
        private String nit;
    }

    @GetMapping
    @Operation(summary = "Listar todas las tiendas")
    public List<Tienda> getAll(@RequestParam(required = false) String city) {
        if (city != null && !city.isBlank()) {
            return tiendaRepository.findByCiudadNombre(city);
        }
        return tiendaRepository.findAll();
    }

    @GetMapping("/active")
    @Operation(summary = "Listar tiendas activas")
    public List<Tienda> getActive() {
        return tiendaRepository.findByActivoTrue();
    }

    @GetMapping("/by-city/{ciudad}")
    @Operation(summary = "Tiendas activas de una ciudad (para registro de vendedor)")
    public List<Tienda> getActiveByCiudad(@PathVariable String ciudad) {
        return tiendaRepository.findByCiudadNombreAndActivoTrue(ciudad);
    }

    @GetMapping("/my-stores")
    @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
    @Operation(summary = "Tiendas asignadas al vendedor autenticado")
    public List<Tienda> getMyStores(org.springframework.security.core.Authentication auth) {
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return List.of();
        }
        List<Tienda> tiendas = new java.util.ArrayList<>(vendedorTiendaRepository.findActiveTiendasByVendedorId(vendedor.getId()));
        if (tiendas.isEmpty()) {
            // Fallback por nombre legacy
            if (vendedor.getTienda() != null && !vendedor.getTienda().isBlank()) {
                tiendaRepository.findFirstByNombreIgnoreCase(vendedor.getTienda().trim())
                        .ifPresent(tiendas::add);
            }
            // Si aún está vacía, retornar tiendas activas de su ciudad
            if (tiendas.isEmpty() && vendedor.getCiudad() != null) {
                tiendas.addAll(tiendaRepository.findByCiudadNombreAndActivoTrue(vendedor.getCiudad().getNombre()));
            }
            // Si aún no hay, retornar tiendas activas generales para no bloquear
            if (tiendas.isEmpty()) {
                tiendas.addAll(tiendaRepository.findByActivoTrue());
            }
        }
        return tiendas;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tienda> getById(@PathVariable Long id) {
        return tiendaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear tienda")
    public ResponseEntity<Tienda> create(@RequestBody TiendaRequest body) {
        com.ninabit.bono.modules.city.Ciudad ciudad = null;
        if (body.getCiudadId() != null) {
            ciudad = ciudadRepository.findById(body.getCiudadId()).orElse(null);
        }

        Tienda tienda = Tienda.builder()
                .codigo(body.getCodigo())
                .nombre(body.getNombre())
                .canal(body.getCanal())
                .departamento(body.getDepartamento())
                .nombrePropietario(body.getNombrePropietario())
                .encargadoNombre(body.getEncargadoNombre())
                .encargadoTelefono(body.getEncargadoTelefono())
                .encargadoEmail(body.getEncargadoEmail())
                .encargadoCi(body.getEncargadoCi())
                .qrPagoUrl(body.getQrPagoUrl())
                .banco(body.getBanco())
                .nroCuenta(body.getNroCuenta())
                .titularCobro(body.getTitularCobro())
                .telefonoTitular(body.getTelefonoTitular())
                .direccion(body.getDireccion())
                .telefono(body.getTelefono())
                .ciudad(ciudad)
                .nit(body.getNit())
                .activo(true)
                .build();
        Tienda saved = tiendaRepository.save(tienda);
        auditoriaService.log(getUser(), "TIENDA_CREADA",
                "Tienda creada: " + saved.getNombre() + " (Código: " + saved.getCodigo() + ")", "Tienda", saved.getId().toString());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar tienda")
    public ResponseEntity<Tienda> update(@PathVariable Long id, @RequestBody TiendaRequest body) {
        return tiendaRepository.findById(id).map(t -> {
            if (body.getCodigo() != null && !body.getCodigo().isBlank()) {
                t.setCodigo(body.getCodigo());
            }
            t.setNombre(body.getNombre());
            t.setCanal(body.getCanal());
            t.setDepartamento(body.getDepartamento());
            t.setNombrePropietario(body.getNombrePropietario());
            t.setEncargadoNombre(body.getEncargadoNombre());
            t.setEncargadoTelefono(body.getEncargadoTelefono());
            t.setEncargadoEmail(body.getEncargadoEmail());
            t.setEncargadoCi(body.getEncargadoCi());
            if (body.getQrPagoUrl() != null) {
                t.setQrPagoUrl(body.getQrPagoUrl());
            }
            t.setBanco(body.getBanco());
            t.setNroCuenta(body.getNroCuenta());
            t.setTitularCobro(body.getTitularCobro());
            t.setTelefonoTitular(body.getTelefonoTitular());
            t.setDireccion(body.getDireccion());
            t.setTelefono(body.getTelefono());
            if (body.getCiudadId() != null) {
                ciudadRepository.findById(body.getCiudadId()).ifPresent(t::setCiudad);
            }
            t.setNit(body.getNit());
            Tienda saved = tiendaRepository.save(t);
            auditoriaService.log(getUser(), "TIENDA_ACTUALIZADA",
                    "Tienda actualizada: " + saved.getNombre(), "Tienda", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{id}/qr-pago", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Subir imagen de QR de pago de la tienda")
    public ResponseEntity<?> uploadQrPago(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        return tiendaRepository.findById(id).map(t -> {
            try {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body(java.util.Map.of("error", "El archivo está vacío."));
                }
                java.nio.file.Path dir = java.nio.file.Paths.get(uploadDir, "tiendas", "qrs");
                if (!java.nio.file.Files.exists(dir)) {
                    java.nio.file.Files.createDirectories(dir);
                }
                String ext = "jpg";
                String original = file.getOriginalFilename();
                if (original != null && original.contains(".")) {
                    ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
                }
                String filename = "qr_tienda_" + id + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "." + ext;
                java.nio.file.Path targetPath = dir.resolve(filename);
                java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                String relativeUrl = "uploads/tiendas/qrs/" + filename;
                t.setQrPagoUrl(relativeUrl);
                Tienda saved = tiendaRepository.save(t);
                auditoriaService.log(getUser(), "TIENDA_QR_SUBIDO",
                        "QR de cobro subido para tienda: " + saved.getNombre(), "Tienda", id.toString());
                return ResponseEntity.ok(saved);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Error guardando imagen QR: " + e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar/Desactivar tienda")
    public ResponseEntity<Tienda> toggle(@PathVariable Long id) {
        return tiendaRepository.findById(id).map(t -> {
            t.setActivo(!t.isActivo());
            Tienda saved = tiendaRepository.save(t);
            auditoriaService.log(getUser(), "TIENDA_TOGGLE_ACTIVO",
                    "Tienda " + (saved.isActivo() ? "activada" : "desactivada") + ": " + saved.getNombre(),
                    "Tienda", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Estadísticas de ventas y bono de una tienda")
    public ResponseEntity<?> getStats(@PathVariable Long id) {
        return tiendaRepository.findById(id).map(tienda -> {
            List<Object[]> stats = ventaRepository.getStatsByTiendaId(id);
            Object[] row = (!stats.isEmpty() && stats.get(0) != null && stats.get(0).length > 0) ? stats.get(0)
                    : new Object[] { 0L, 0L, 0 };

            long totalVendedores = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            long totalVentas = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long totalBono = row[2] != null ? ((Number) row[2]).longValue() : 0L;

            java.util.HashMap<String, Object> result = new java.util.HashMap<>();
            result.put("tiendaId", id);
            result.put("tiendaNombre", tienda.getNombre());
            result.put("totalVendedores", totalVendedores);
            result.put("totalVentasAprobadas", totalVentas);
            result.put("totalBonoBs", totalBono);
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/vendors")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Vendedores asignados a una tienda")
    public List<Vendedor> getVendorsByStore(@PathVariable Long id) {
        return vendedorTiendaRepository.findActiveVendedoresByTiendaId(id);
    }

    @GetMapping("/vendor/{vendedorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'REVIEWER')")
    @Operation(summary = "Tiendas asignadas a un vendedor específico")
    public List<Tienda> getStoresByVendor(@PathVariable Long vendedorId) {
        return vendedorTiendaRepository.findActiveTiendasByVendedorId(vendedorId);
    }

    @RequestMapping(value = "/{id}/assign-vendor/{vendedorId}", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Asignar un vendedor a esta tienda (N a M)")
    public ResponseEntity<?> assignVendor(@PathVariable Long id, @PathVariable Long vendedorId) {
        Tienda tienda = tiendaRepository.findById(id).orElse(null);
        if (tienda == null) return ResponseEntity.notFound().build();
        Vendedor vendedor = vendedorRepository.findById(vendedorId).orElse(null);
        if (vendedor == null) return ResponseEntity.notFound().build();

        VendedorTienda vt = vendedorTiendaRepository.findFirstByVendedorIdAndTiendaId(vendedorId, id)
                .orElse(VendedorTienda.builder()
                        .vendedor(vendedor)
                        .tienda(tienda)
                        .fechaAsignacion(java.time.LocalDateTime.now())
                        .build());
        vt.setActivo(true);
        vendedorTiendaRepository.save(vt);

        // Mantener sincronizado el texto legacy si está vacío
        if (vendedor.getTienda() == null || vendedor.getTienda().isBlank()) {
            vendedor.setTienda(tienda.getNombre());
            if (tienda.getCiudad() != null) {
                vendedor.setCiudad(tienda.getCiudad());
            }
            vendedorRepository.save(vendedor);
        }

        auditoriaService.log(getUser(), "TIENDA_VENDEDOR_ASIGNADO",
                "Vendedor " + vendedor.getNombreCompleto() + " asignado a tienda " + tienda.getNombre(),
                "VendedorTienda", vt.getId().toString());

        return ResponseEntity.ok(vendedor);
    }

    @DeleteMapping("/{id}/remove-vendor/{vendedorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desasignar un vendedor de esta tienda")
    public ResponseEntity<?> removeVendorFromStore(@PathVariable Long id, @PathVariable Long vendedorId) {
        return vendedorTiendaRepository.findFirstByVendedorIdAndTiendaId(vendedorId, id)
                .map(vt -> {
                    vt.setActivo(false);
                    vendedorTiendaRepository.save(vt);
                    auditoriaService.log(getUser(), "TIENDA_VENDEDOR_DESASIGNADO",
                            "Vendedor " + vendedorId + " desasignado de tienda " + id,
                            "VendedorTienda", vt.getId().toString());
                    return ResponseEntity.ok(java.util.Map.of("message", "Vendedor desasignado correctamente"));
                }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/vendor/{vendedorId}/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sincronizar tiendas asignadas a un vendedor")
    public ResponseEntity<?> syncVendorStores(@PathVariable Long vendedorId, @RequestBody List<Long> tiendaIds) {
        Vendedor vendedor = vendedorRepository.findById(vendedorId).orElse(null);
        if (vendedor == null) return ResponseEntity.notFound().build();

        List<VendedorTienda> actuales = vendedorTiendaRepository.findByVendedorIdAndActivoTrue(vendedorId);
        for (VendedorTienda vt : actuales) {
            if (!tiendaIds.contains(vt.getTienda().getId())) {
                vt.setActivo(false);
                vendedorTiendaRepository.save(vt);
            }
        }

        for (Long tId : tiendaIds) {
            tiendaRepository.findById(tId).ifPresent(t -> {
                VendedorTienda vt = vendedorTiendaRepository.findFirstByVendedorIdAndTiendaId(vendedorId, tId)
                        .orElse(VendedorTienda.builder().vendedor(vendedor).tienda(t).build());
                vt.setActivo(true);
                vt.setFechaAsignacion(java.time.LocalDateTime.now());
                vendedorTiendaRepository.save(vt);
            });
        }

        // Si el vendedor no tiene nombre de tienda asignado, asignarle el primero
        if (!tiendaIds.isEmpty()) {
            tiendaRepository.findById(tiendaIds.get(0)).ifPresent(primera -> {
                vendedor.setTienda(primera.getNombre());
                if (primera.getCiudad() != null) vendedor.setCiudad(primera.getCiudad());
                vendedorRepository.save(vendedor);
            });
        }

        return ResponseEntity.ok(java.util.Map.of("message", "Tiendas sincronizadas correctamente"));
    }

    @GetMapping("/{id}/encargado")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL', 'ADMIN_OPERATIVO')")
    @Operation(summary = "Obtener el usuario encargado vinculado a esta tienda")
    public ResponseEntity<?> getEncargado(@PathVariable Long id) {
        Tienda tienda = tiendaRepository.findById(id).orElse(null);
        if (tienda == null) return ResponseEntity.notFound().build();

        com.ninabit.bono.modules.user.Usuario u = usuarioRepository.findFirstByTiendaIdAndActivoTrue(id).orElse(null);
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("tiendaId", id);
        res.put("tiendaNombre", tienda.getNombre());
        res.put("nombrePropietario", tienda.getNombrePropietario());
        res.put("encargadoNombre", tienda.getEncargadoNombre());
        res.put("encargadoEmail", tienda.getEncargadoEmail());
        res.put("encargadoTelefono", tienda.getEncargadoTelefono());
        if (u != null) {
            res.put("tieneUsuario", true);
            res.put("usuarioId", u.getId());
            res.put("usuarioEmail", u.getEmail());
            res.put("usuarioNombre", u.getNombreCompleto());
            res.put("usuarioRol", u.getRol().name());
            res.put("usuarioActivo", u.isActivo());
        } else {
            res.put("tieneUsuario", false);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/vincular-encargado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vincular o crear un usuario encargado para la tienda")
    public ResponseEntity<?> vincularEncargado(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body
    ) {
        Tienda tienda = tiendaRepository.findById(id).orElse(null);
        if (tienda == null) return ResponseEntity.notFound().build();

        String usuarioIdStr = body.get("usuarioId");
        String email = body.get("email");
        String nombre = body.get("nombre");
        String telefono = body.get("telefono");
        String password = body.get("password");

        com.ninabit.bono.modules.user.Usuario usuario = null;

        if (usuarioIdStr != null && !usuarioIdStr.isBlank()) {
            try {
                Long uId = Long.parseLong(usuarioIdStr.trim());
                usuario = usuarioRepository.findById(uId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }

        if (usuario == null && email != null && !email.isBlank()) {
            usuario = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        }

        if (usuario == null && email != null && !email.isBlank()) {
            // Crear nuevo usuario encargado
            String rawPass = (password != null && !password.isBlank()) ? password.trim() : "skyworth123";
            usuario = com.ninabit.bono.modules.user.Usuario.builder()
                    .email(email.trim().toLowerCase())
                    .nombreCompleto(nombre != null && !nombre.isBlank() ? nombre.trim() : tienda.getNombre() + " - Encargado")
                    .password(passwordEncoder.encode(rawPass))
                    .rol(com.ninabit.bono.modules.user.Usuario.Rol.ENCARGADO_TIENDA)
                    .tiendaId(tienda.getId())
                    .ciudad(tienda.getCiudad() != null ? tienda.getCiudad().getNombre() : "")
                    .activo(true)
                    .build();
            usuario = usuarioRepository.save(usuario);
        } else if (usuario != null) {
            // Asignar al existente
            usuario.setTiendaId(tienda.getId());
            if (usuario.getRol() == null || usuario.getRol() == com.ninabit.bono.modules.user.Usuario.Rol.VENDOR) {
                usuario.setRol(com.ninabit.bono.modules.user.Usuario.Rol.ENCARGADO_TIENDA);
            }
            if (nombre != null && !nombre.isBlank()) {
                usuario.setNombreCompleto(nombre.trim());
            }
            usuario.setActivo(true);
            usuario = usuarioRepository.save(usuario);
        } else {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Debe proporcionar usuarioId o email."));
        }

        // Actualizar datos informativos en la tienda
        if (nombre != null && !nombre.isBlank()) tienda.setEncargadoNombre(nombre.trim());
        tienda.setEncargadoEmail(usuario.getEmail());
        if (telefono != null && !telefono.isBlank()) tienda.setEncargadoTelefono(telefono.trim());
        tiendaRepository.save(tienda);

        auditoriaService.log(getUser(), "TIENDA_ENCARGADO_VINCULADO",
                "Usuario " + usuario.getEmail() + " vinculado como encargado de la tienda " + tienda.getNombre(),
                "Tienda", tienda.getId().toString());

        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Encargado vinculado correctamente",
                "usuarioId", usuario.getId(),
                "usuarioEmail", usuario.getEmail()
        ));
    }

    @DeleteMapping("/{id}/desvincular-encargado/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desvincular el usuario encargado de la tienda")
    public ResponseEntity<?> desvincularEncargado(
            @PathVariable Long id,
            @PathVariable Long usuarioId
    ) {
        Tienda tienda = tiendaRepository.findById(id).orElse(null);
        if (tienda == null) return ResponseEntity.notFound().build();

        return usuarioRepository.findById(usuarioId).map(u -> {
            if (java.util.Objects.equals(u.getTiendaId(), id)) {
                u.setTiendaId(null);
                usuarioRepository.save(u);
                auditoriaService.log(getUser(), "TIENDA_ENCARGADO_DESVINCULADO",
                        "Usuario " + u.getEmail() + " desvinculado de la tienda " + tienda.getNombre(),
                        "Tienda", tienda.getId().toString());
            }
            return ResponseEntity.ok(java.util.Map.of("message", "Usuario desvinculado de la tienda"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getName();
    }
}

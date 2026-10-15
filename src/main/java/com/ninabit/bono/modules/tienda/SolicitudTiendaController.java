package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.city.CiudadRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/tiendas/solicitudes")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de Tienda")
public class SolicitudTiendaController {

    private final SolicitudTiendaRepository solicitudRepository;
    private final TiendaRepository tiendaRepository;
    private final CiudadRepository ciudadRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final com.ninabit.bono.modules.vendor.VendedorRepository vendedorRepository;
    private final VendedorTiendaRepository vendedorTiendaRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Data
    public static class SolicitudPublicRequest {
        private String tipoSolicitud; // "NUEVA_TIENDA" o "RECLAMAR_TIENDA"
        private Long tiendaExistenteId;

        // Datos Tienda
        private String nombreTienda;
        private String codigoTienda;
        private String canal;
        private Long ciudadId;
        private String ciudadNombre;
        private String departamento;
        private String direccion;
        private String telefonoTienda;
        private String nit;

        // Datos Solicitante
        private String nombreSolicitante;
        private String ciSolicitante;
        private String telefonoSolicitante;
        private String emailSolicitante;
        private String password;
        private String cargoSolicitante;

        // Datos Cobro
        private String banco;
        private String nroCuenta;
        private String titularCobro;
        private String telefonoTitular;
        private String qrPagoUrl;

        // Respaldo
        private String fotoVerificacionUrl;
        private String notasAdicionales;
    }

    /**
     * Endpoint público para enviar solicitud de registro de nueva tienda o reclamo de tienda existente.
     */
    @PostMapping("/public")
    @Operation(summary = "Registrar solicitud de tienda (nueva o reclamo de existente) - Público")
    public ResponseEntity<?> submitPublicRequest(@RequestBody SolicitudPublicRequest body) {
        if (body.getNombreSolicitante() == null || body.getNombreSolicitante().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre del solicitante es obligatorio."));
        }
        if (body.getTelefonoSolicitante() == null || body.getTelefonoSolicitante().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El teléfono de contacto es obligatorio."));
        }
        if (body.getEmailSolicitante() == null || body.getEmailSolicitante().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo electrónico es obligatorio."));
        }

        String rawEmail = body.getEmailSolicitante().trim().toLowerCase();
        SolicitudTienda.TipoSolicitud tipo = "RECLAMAR_TIENDA".equalsIgnoreCase(body.getTipoSolicitud())
                ? SolicitudTienda.TipoSolicitud.RECLAMAR_TIENDA
                : SolicitudTienda.TipoSolicitud.NUEVA_TIENDA;

        Tienda tiendaExistente = null;
        if (tipo == SolicitudTienda.TipoSolicitud.RECLAMAR_TIENDA) {
            if (body.getTiendaExistenteId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe seleccionar la tienda existente que desea reclamar."));
            }
            tiendaExistente = tiendaRepository.findById(body.getTiendaExistenteId()).orElse(null);
            if (tiendaExistente == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La tienda seleccionada no fue encontrada."));
            }
        } else {
            if (body.getNombreTienda() == null || body.getNombreTienda().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la nueva tienda es obligatorio."));
            }
        }

        Ciudad ciudad = null;
        if (body.getCiudadId() != null) {
            ciudad = ciudadRepository.findById(body.getCiudadId()).orElse(null);
        } else if (body.getCiudadNombre() != null && !body.getCiudadNombre().isBlank()) {
            ciudad = ciudadRepository.findByNombreIgnoreCase(body.getCiudadNombre().trim()).orElse(null);
        }

        String rawPassword = (body.getPassword() != null && !body.getPassword().isBlank())
                ? body.getPassword().trim()
                : "skyworth" + (int)(Math.random() * 9000 + 1000);

        String encodedPassword = passwordEncoder.encode(rawPassword);

        String deptoFinal = body.getDepartamento();
        if ((deptoFinal == null || deptoFinal.isBlank()) && ciudad != null) {
            deptoFinal = ciudad.getDepartamento();
        }

        SolicitudTienda solicitud = SolicitudTienda.builder()
                .tipoSolicitud(tipo)
                .tiendaExistente(tiendaExistente)
                .nombreTienda(tipo == SolicitudTienda.TipoSolicitud.RECLAMAR_TIENDA ? tiendaExistente.getNombre() : body.getNombreTienda().trim())
                .codigoTienda(body.getCodigoTienda())
                .canal(body.getCanal() != null && !body.getCanal().isBlank() ? body.getCanal() : "Retail")
                .ciudad(ciudad != null ? ciudad : (tiendaExistente != null ? tiendaExistente.getCiudad() : null))
                .departamento(deptoFinal != null ? deptoFinal : (tiendaExistente != null ? tiendaExistente.getDepartamento() : null))
                .direccion(body.getDireccion() != null ? body.getDireccion() : (tiendaExistente != null ? tiendaExistente.getDireccion() : null))
                .telefonoTienda(body.getTelefonoTienda() != null ? body.getTelefonoTienda() : (tiendaExistente != null ? tiendaExistente.getTelefono() : null))
                .nit(body.getNit() != null ? body.getNit() : (tiendaExistente != null ? tiendaExistente.getNit() : null))
                .nombreSolicitante(body.getNombreSolicitante().trim())
                .ciSolicitante(body.getCiSolicitante())
                .telefonoSolicitante(body.getTelefonoSolicitante().trim())
                .emailSolicitante(rawEmail)
                .passwordHash(encodedPassword)
                .cargoSolicitante(body.getCargoSolicitante() != null ? body.getCargoSolicitante() : "Encargado de Tienda")
                .banco(body.getBanco() != null ? body.getBanco() : (tiendaExistente != null ? tiendaExistente.getBanco() : null))
                .nroCuenta(body.getNroCuenta() != null ? body.getNroCuenta() : (tiendaExistente != null ? tiendaExistente.getNroCuenta() : null))
                .titularCobro(body.getTitularCobro() != null ? body.getTitularCobro() : (tiendaExistente != null ? tiendaExistente.getTitularCobro() : null))
                .telefonoTitular(body.getTelefonoTitular() != null ? body.getTelefonoTitular() : (tiendaExistente != null ? tiendaExistente.getTelefonoTitular() : null))
                .qrPagoUrl(body.getQrPagoUrl() != null ? body.getQrPagoUrl() : (tiendaExistente != null ? tiendaExistente.getQrPagoUrl() : null))
                .fotoVerificacionUrl(body.getFotoVerificacionUrl())
                .notasAdicionales(body.getNotasAdicionales())
                .estado(SolicitudTienda.EstadoSolicitud.PENDIENTE)
                .build();

        SolicitudTienda saved = solicitudRepository.save(solicitud);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", saved.getId(),
                "message", "Tu solicitud para " + (tipo == SolicitudTienda.TipoSolicitud.RECLAMAR_TIENDA ? "reclamar la tienda" : "crear la tienda")
                        + " ha sido enviada con éxito. Será evaluada por el equipo de supervisores de Skyworth."
        ));
    }

    /**
     * Subida pública de comprobante / verificación (fachada, NIT, CI de representante).
     */
    @PostMapping(value = "/upload-verificacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir foto o documento de verificación de tienda")
    public ResponseEntity<?> uploadVerificacion(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
            }
            Path dir = Paths.get(uploadDir, "tiendas", "solicitudes");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String ext = "jpg";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
            }
            String filename = "verif_tienda_" + UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis() + "." + ext;
            Path targetPath = dir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String relativeUrl = "uploads/tiendas/solicitudes/" + filename;
            return ResponseEntity.ok(Map.of("url", relativeUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error subiendo archivo: " + e.getMessage()));
        }
    }

    /**
     * Listar solicitudes de tiendas (Admin / Supervisor).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL', 'ADMIN_OPERATIVO')")
    @Operation(summary = "Listar solicitudes de tiendas")
    public List<SolicitudTienda> listSolicitudes(@RequestParam(required = false) String estado) {
        if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase("TODOS")) {
            try {
                SolicitudTienda.EstadoSolicitud est = SolicitudTienda.EstadoSolicitud.valueOf(estado.toUpperCase());
                return solicitudRepository.findByEstadoOrderByCreatedAtDesc(est);
            } catch (IllegalArgumentException ignored) {}
        }
        return solicitudRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Estadísticas de solicitudes.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL', 'ADMIN_OPERATIVO')")
    @Operation(summary = "Contador de solicitudes por estado")
    public Map<String, Object> getStats() {
        long pendientes = solicitudRepository.countByEstado(SolicitudTienda.EstadoSolicitud.PENDIENTE);
        long aprobadas = solicitudRepository.countByEstado(SolicitudTienda.EstadoSolicitud.APROBADA);
        long rechazadas = solicitudRepository.countByEstado(SolicitudTienda.EstadoSolicitud.RECHAZADA);
        long total = pendientes + aprobadas + rechazadas;

        return Map.of(
                "total", total,
                "pendientes", pendientes,
                "aprobadas", aprobadas,
                "rechazadas", rechazadas
        );
    }

    /**
     * Mis solicitudes de tienda (para perfil de vendedor o usuario logueado).
     */
    @GetMapping("/my-requests")
    @Operation(summary = "Obtener mis solicitudes de tienda (como vendedor o usuario)")
    public List<SolicitudTienda> getMyRequests() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return solicitudRepository.findByEmailSolicitante(email.trim().toLowerCase());
    }

    /**
     * Detalle de una solicitud.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL', 'ADMIN_OPERATIVO')")
    @Operation(summary = "Obtener detalle de solicitud")
    public ResponseEntity<SolicitudTienda> getDetail(@PathVariable Long id) {
        return solicitudRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Aprobar solicitud de tienda:
     * - Si es nueva: crea la tienda y la activa.
     * - Si es reclamo: actualiza la tienda existente.
     * - En ambos casos: crea o vincula la cuenta de usuario con rol ENCARGADO_TIENDA.
     */
    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL')")
    @Operation(summary = "Aprobar solicitud de tienda")
    public ResponseEntity<?> aprobarSolicitud(@PathVariable Long id) {
        SolicitudTienda s = solicitudRepository.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.notFound().build();
        }
        if (s.getEstado() == SolicitudTienda.EstadoSolicitud.APROBADA) {
            return ResponseEntity.badRequest().body(Map.of("error", "Esta solicitud ya fue aprobada anteriormente."));
        }

        String userLogged = SecurityContextHolder.getContext().getAuthentication().getName();

        Tienda targetTienda;

        if (s.getTipoSolicitud() == SolicitudTienda.TipoSolicitud.RECLAMAR_TIENDA && s.getTiendaExistente() != null) {
            // Reclamar tienda existente
            targetTienda = s.getTiendaExistente();
            if (s.getNombreSolicitante() != null && !s.getNombreSolicitante().isBlank()) {
                targetTienda.setEncargadoNombre(s.getNombreSolicitante());
                targetTienda.setNombrePropietario(s.getNombreSolicitante());
            }
            if (s.getCiSolicitante() != null) targetTienda.setEncargadoCi(s.getCiSolicitante());
            if (s.getTelefonoSolicitante() != null) targetTienda.setEncargadoTelefono(s.getTelefonoSolicitante());
            if (s.getEmailSolicitante() != null) targetTienda.setEncargadoEmail(s.getEmailSolicitante());
            if (s.getDireccion() != null && !s.getDireccion().isBlank()) targetTienda.setDireccion(s.getDireccion());
            if (s.getNit() != null && !s.getNit().isBlank()) targetTienda.setNit(s.getNit());
            if (s.getBanco() != null) targetTienda.setBanco(s.getBanco());
            if (s.getNroCuenta() != null) targetTienda.setNroCuenta(s.getNroCuenta());
            if (s.getTitularCobro() != null) targetTienda.setTitularCobro(s.getTitularCobro());
            if (s.getTelefonoTitular() != null) targetTienda.setTelefonoTitular(s.getTelefonoTitular());
            if (s.getQrPagoUrl() != null) targetTienda.setQrPagoUrl(s.getQrPagoUrl());
            targetTienda.setActivo(true);
            targetTienda = tiendaRepository.save(targetTienda);
        } else {
            // Crear nueva tienda
            targetTienda = Tienda.builder()
                    .codigo(s.getCodigoTienda())
                    .nombre(s.getNombreTienda())
                    .canal(s.getCanal() != null ? s.getCanal() : "Retail")
                    .departamento(s.getDepartamento())
                    .nombrePropietario(s.getNombreSolicitante())
                    .encargadoNombre(s.getNombreSolicitante())
                    .encargadoCi(s.getCiSolicitante())
                    .encargadoTelefono(s.getTelefonoSolicitante())
                    .encargadoEmail(s.getEmailSolicitante())
                    .direccion(s.getDireccion())
                    .telefono(s.getTelefonoTienda() != null ? s.getTelefonoTienda() : s.getTelefonoSolicitante())
                    .ciudad(s.getCiudad())
                    .nit(s.getNit())
                    .banco(s.getBanco())
                    .nroCuenta(s.getNroCuenta())
                    .titularCobro(s.getTitularCobro())
                    .telefonoTitular(s.getTelefonoTitular())
                    .qrPagoUrl(s.getQrPagoUrl())
                    .activo(true)
                    .build();
            targetTienda = tiendaRepository.save(targetTienda);
        }

        // Crear o asociar la cuenta de usuario
        String emailSol = s.getEmailSolicitante().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(emailSol).orElse(null);
        if (usuario == null) {
            String passHash = s.getPasswordHash() != null ? s.getPasswordHash() : passwordEncoder.encode("skyworth123");
            usuario = Usuario.builder()
                    .email(emailSol)
                    .password(passHash)
                    .rol(Usuario.Rol.ENCARGADO_TIENDA)
                    .nombreCompleto(s.getNombreSolicitante())
                    .tiendaId(targetTienda.getId())
                    .ciudad(targetTienda.getCiudad() != null ? targetTienda.getCiudad().getNombre() : "")
                    .activo(true)
                    .build();
            usuario = usuarioRepository.save(usuario);
        } else {
            // Usuario ya existía, vincularle la tienda
            usuario.setTiendaId(targetTienda.getId());
            if (usuario.getRol() == null) {
                usuario.setRol(Usuario.Rol.ENCARGADO_TIENDA);
            }
            usuario.setActivo(true);
            usuario = usuarioRepository.save(usuario);
        }

        // Si el solicitante es un Vendedor registrado, sincronizar su tienda asignada
        final Tienda fTargetTienda = targetTienda;
        vendedorRepository.findByEmail(emailSol).ifPresent(v -> {
            v.setTienda(fTargetTienda.getNombre());
            if (fTargetTienda.getCiudad() != null) {
                v.setCiudad(fTargetTienda.getCiudad());
            }
            vendedorRepository.save(v);
            VendedorTienda vt = vendedorTiendaRepository.findFirstByVendedorIdAndTiendaId(v.getId(), fTargetTienda.getId())
                    .orElse(VendedorTienda.builder().vendedor(v).tienda(fTargetTienda).build());
            vt.setActivo(true);
            vendedorTiendaRepository.save(vt);
        });

        // Actualizar solicitud
        s.setEstado(SolicitudTienda.EstadoSolicitud.APROBADA);
        s.setFechaRevision(LocalDateTime.now());
        s.setRevisadoPor(userLogged);
        s.setTiendaResultado(targetTienda);
        s.setUsuarioCreado(usuario);
        solicitudRepository.save(s);

        auditoriaService.log(userLogged, "SOLICITUD_TIENDA_APROBADA",
                "Solicitud de tienda #" + s.getId() + " aprobada para: " + targetTienda.getNombre()
                        + " (Encargado: " + usuario.getEmail() + ")",
                "SolicitudTienda", s.getId().toString());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Solicitud aprobada con éxito. Tienda activada y cuenta de usuario habilitada.",
                "tiendaId", targetTienda.getId(),
                "usuarioId", usuario.getId(),
                "usuarioEmail", usuario.getEmail()
        ));
    }

    /**
     * Rechazar solicitud de tienda.
     */
    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ADMIN_TOTAL')")
    @Operation(summary = "Rechazar solicitud de tienda")
    public ResponseEntity<?> rechazarSolicitud(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        SolicitudTienda s = solicitudRepository.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.notFound().build();
        }
        if (s.getEstado() == SolicitudTienda.EstadoSolicitud.RECHAZADA) {
            return ResponseEntity.badRequest().body(Map.of("error", "Esta solicitud ya fue rechazada anteriormente."));
        }

        String userLogged = SecurityContextHolder.getContext().getAuthentication().getName();
        String motivo = (body != null && body.get("motivo") != null && !body.get("motivo").isBlank())
                ? body.get("motivo").trim()
                : "No cumple con los requisitos mínimos de validación.";

        s.setEstado(SolicitudTienda.EstadoSolicitud.RECHAZADA);
        s.setMotivoRechazo(motivo);
        s.setFechaRevision(LocalDateTime.now());
        s.setRevisadoPor(userLogged);
        solicitudRepository.save(s);

        auditoriaService.log(userLogged, "SOLICITUD_TIENDA_RECHAZADA",
                "Solicitud de tienda #" + s.getId() + " rechazada. Motivo: " + motivo,
                "SolicitudTienda", s.getId().toString());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Solicitud rechazada correctamente.",
                "motivo", motivo
        ));
    }
}

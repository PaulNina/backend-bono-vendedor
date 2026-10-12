package com.ninabit.bono.modules.configuracion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@Tag(name = "Configuración")
public class ConfiguracionController {

    private final RolPermisoRepository rolPermisoRepository;
    private final PermisoAccionRepository permisoAccionRepository;
    private final ConfiguracionGlobalRepository configuracionGlobalRepository;
    private final com.ninabit.bono.modules.email.EmailConfigService emailConfigService;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Obtener permisos por rol")
    public List<RolPermiso> getRolesPermissions() {
        return rolPermisoRepository.findAll();
    }

    @GetMapping("/global")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Obtener configuración global (ej: SMTP)")
    public Map<String, String> getGlobalConfig() {
        return configuracionGlobalRepository.findAll().stream()
                .collect(Collectors.toMap(ConfiguracionGlobal::getClave, ConfiguracionGlobal::getValor));
    }

    @PutMapping("/global")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Guardar configuración global")
    public Map<String, String> updateGlobalConfig(@RequestBody Map<String, String> updates) {
        updates.forEach((k, v) -> {
            ConfiguracionGlobal config = configuracionGlobalRepository.findById(k).orElseGet(() -> {
                return ConfiguracionGlobal.builder().clave(k).build();
            });
            config.setValor(v);
            configuracionGlobalRepository.save(config);
        });

        auditoriaService.log(getUser(), "CONFIG_GLOBAL_UPDATE", "Configuración global actualizada",
                "ConfiguracionGlobal", "global");

        return getGlobalConfig();
    }

    @PostMapping("/test-smtp")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Probar conexión SMTP")
    public Map<String, Object> testSmtpConnection(@RequestBody Map<String, String> config) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(config.get("smtp_host"));
            mailSender.setPort(Integer.parseInt(config.getOrDefault("smtp_port", "587")));
            mailSender.setUsername(config.get("smtp_username"));
            mailSender.setPassword(config.get("smtp_password"));

            java.util.Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.debug", "false");

            mailSender.testConnection();

            return Map.of("success", true, "message", "Conexión SMTP exitosa");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "cause", e.getCause() != null ? e.getCause().getMessage() : "null");
        }
    }

    @GetMapping("/mis-permisos")
    @Operation(summary = "Obtener permisos del usuario actual")
    public Map<String, List<String>> getMyPermissions() {
        List<RolPermiso> permisos = rolPermisoRepository.findAll();
        return permisos.stream().collect(Collectors.toMap(
                RolPermiso::getRol,
                p -> List.of(p.getModulosPermitidos().split(","))));
    }

    @PutMapping("/roles/{rol}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Actualizar permisos de un rol")
    public RolPermiso updateRolPermissions(@PathVariable String rol, @RequestBody Map<String, String> payload) {
        RolPermiso permiso = rolPermisoRepository.findById(rol).orElseGet(() -> {
            return RolPermiso.builder().rol(rol).build();
        });

        permiso.setModulosPermitidos(payload.getOrDefault("modulosPermitidos", ""));
        permiso.setUpdatedAt(LocalDateTime.now());

        RolPermiso saved = rolPermisoRepository.save(permiso);
        auditoriaService.log(getUser(), "ROL_PERMISOS_UPDATE", 
            "Permisos actualizados para el rol: " + rol, "RolPermiso", rol);
        return saved;
    }

    @GetMapping("/permisos-acciones")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Listar permisos granulares por acción y módulo")
    public List<PermisoAccion> getPermisosAcciones() {
        return permisoAccionRepository.findAll();
    }

    @PostMapping("/permisos-acciones")
    @PreAuthorize("hasAnyRole('ADMIN_TOTAL', 'ADMIN')")
    @Operation(summary = "Guardar o actualizar un permiso granular por acción y módulo")
    public PermisoAccion savePermisoAccion(@RequestBody PermisoAccion body) {
        PermisoAccion p = permisoAccionRepository
                .findByRolAndModuloAndAccion(body.getRol().toUpperCase().trim(), body.getModulo().toUpperCase().trim(), body.getAccion().toUpperCase().trim())
                .orElseGet(() -> PermisoAccion.builder()
                        .rol(body.getRol().toUpperCase().trim())
                        .modulo(body.getModulo().toUpperCase().trim())
                        .accion(body.getAccion().toUpperCase().trim())
                        .build());
        p.setPermitido(body.isPermitido());
        p.setUpdatedAt(LocalDateTime.now());
        PermisoAccion saved = permisoAccionRepository.save(p);
        auditoriaService.log(getUser(), "PERMISO_ACCION_UPDATE",
                "Permiso " + p.getAccion() + " en " + p.getModulo() + " para rol " + p.getRol() + ": " + p.isPermitido(),
                "PermisoAccion", saved.getId().toString());
        return saved;
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

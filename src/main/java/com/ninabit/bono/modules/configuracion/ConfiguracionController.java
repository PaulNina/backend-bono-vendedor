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
    private final ConfiguracionGlobalRepository configuracionGlobalRepository;
    private final com.ninabit.bono.modules.email.EmailConfigService emailConfigService;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener permisos por rol")
    public List<RolPermiso> getRolesPermissions() {
        return rolPermisoRepository.findAll();
    }

    @GetMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración global (ej: SMTP)")
    public Map<String, String> getGlobalConfig() {
        return configuracionGlobalRepository.findAll().stream()
                .collect(Collectors.toMap(ConfiguracionGlobal::getClave, ConfiguracionGlobal::getValor));
    }

    @PutMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración global en lote")
    public Map<String, String> updateGlobalConfig(@RequestBody Map<String, String> payload) {
        payload.forEach((clave, valor) -> {
            ConfiguracionGlobal config = configuracionGlobalRepository.findById(clave).orElseGet(() -> {
                return ConfiguracionGlobal.builder().clave(clave).descripcion("Auto-generado").build();
            });
            config.setValor(valor);
            configuracionGlobalRepository.save(config);
        });
        auditoriaService.log(getUser(), "CONFIGURACION_GLOBAL_UPDATE", 
            "Actualización masiva de configuración global: " + payload.keySet(), 
            "ConfiguracionGlobal", null);
        return getGlobalConfig();
    }

    @GetMapping("/test-smtp")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Probar conexión SMTP")
    public Map<String, Object> testSmtp() {
        try {
            JavaMailSenderImpl mailSender = (JavaMailSenderImpl) emailConfigService.createMailSender();
            mailSender.testConnection();
            return Map.of(
                    "success", true,
                    "host", mailSender.getHost(),
                    "port", mailSender.getPort(),
                    "user", mailSender.getUsername() != null ? mailSender.getUsername() : "null");
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
        // En una app más robusta sacaríamos el rol del token. Como el Frontend necesita
        // los módulos según rol
        // Deolveremos un mapa completo de los roles para que el AuthContext frontend
        // decida
        List<RolPermiso> permisos = rolPermisoRepository.findAll();
        return permisos.stream().collect(Collectors.toMap(
                RolPermiso::getRol,
                p -> List.of(p.getModulosPermitidos().split(","))));
    }

    @PutMapping("/roles/{rol}")
    @PreAuthorize("hasRole('ADMIN')")
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

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

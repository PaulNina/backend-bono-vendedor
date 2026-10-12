package com.ninabit.bono.modules.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.city.CiudadRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;

@RestController
@RequiredArgsConstructor
@Tag(name = "Usuarios y Roles")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final VendedorRepository vendedorRepository;
    private final CiudadRepository ciudadRepository;
    private final com.ninabit.bono.modules.sale.VentaRepository ventaRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    /** GET /users/roles — lista todos los usuarios con su rol y filtros */
    @GetMapping("/users/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Listar usuarios y sus roles con filtros")
    public List<Map<String, Object>> getRoles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String rol) {
        
        List<Ciudad> allCities = ciudadRepository.findAll();
        Map<String, String> cityToDept = allCities.stream()
                .filter(c -> c.getNombre() != null && c.getDepartamento() != null)
                .collect(Collectors.toMap(c -> c.getNombre().trim().toLowerCase(), Ciudad::getDepartamento, (existing, replacing) -> existing));

        return usuarioRepository.findAll().stream()
                .filter(Usuario::isActivo)
                .filter(u -> {
                    boolean match = true;
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase();
                        match = (u.getEmail() != null && u.getEmail().toLowerCase().contains(s)) ||
                                (u.getNombreCompleto() != null && u.getNombreCompleto().toLowerCase().contains(s));
                    }
                    if (match && rol != null && !rol.isBlank() && !rol.equalsIgnoreCase("all")) {
                        match = u.getRol().name().equalsIgnoreCase(rol);
                    }
                    return match;
                })
                .map(u -> {
            String ciudad = "";
            String departamento = "";

            if (u.getRol() == Usuario.Rol.VENDOR && u.getVendedorId() != null) {
                Vendedor v = vendedorRepository.findById(u.getVendedorId()).orElse(null);
                if (v != null && v.getCiudad() != null) {
                    ciudad = v.getCiudad().getNombre();
                    departamento = cityToDept.getOrDefault(ciudad, "");
                }
            } else if (u.getCiudad() != null && !u.getCiudad().isEmpty()) {
                ciudad = u.getCiudad();
                departamento = java.util.Arrays.stream(ciudad.split(","))
                        .map(c -> cityToDept.getOrDefault(c.trim(), ""))
                        .filter(d -> !d.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(", "));
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("email", u.getEmail());
            map.put("nombreCompleto", u.getNombreCompleto() != null ? u.getNombreCompleto() : "");
            map.put("rol", u.getRol().name());
            map.put("ciudad", ciudad);
            map.put("departamento", departamento);
            map.put("tiendaId", u.getTiendaId());
            map.put("createdAt", "");
            return map;
        }).collect(Collectors.toList());
    }

    /** POST /users/roles — asignar rol a un usuario por email */
    @PostMapping("/users/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Asignar o cambiar rol a un usuario")
    public ResponseEntity<?> assignRole(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String rol = body.get("rol");
        String ciudad = body.get("ciudad");
        String nombreCompleto = body.get("nombreCompleto");
        String tiendaIdStr = body.get("tiendaId");

        if (email == null || rol == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email y rol son obligatorios"));
        }

        Usuario.Rol newRol;
        try {
            newRol = Usuario.Rol.valueOf(rol.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rol inválido: " + rol));
        }

        String password = body.get("password");

        Usuario u = usuarioRepository.findByEmail(email).orElse(null);
        if (u == null) {
            String passToUse = (password != null && !password.isBlank()) ? password : "skyworth123";
            u = Usuario.builder()
                    .email(email)
                    .password(passwordEncoder.encode(passToUse))
                    .rol(newRol)
                    .nombreCompleto(nombreCompleto != null && !nombreCompleto.isBlank() ? nombreCompleto.trim() : email)
                    .ciudad(ciudad != null ? ciudad : "")
                    .activo(true)
                    .build();

            if (tiendaIdStr != null && !tiendaIdStr.isBlank() && !tiendaIdStr.equalsIgnoreCase("null")) {
                try {
                    u.setTiendaId(Long.parseLong(tiendaIdStr.trim()));
                } catch (NumberFormatException ignored) {}
            }

            if (newRol == Usuario.Rol.VENDOR) {
                final String finalCiudad = ciudad;
                final String finalNombre = nombreCompleto;
                Vendedor v = vendedorRepository.findByEmail(email.trim().toLowerCase())
                        .orElseGet(() -> {
                            com.ninabit.bono.modules.city.Ciudad c = null;
                            if (finalCiudad != null && !finalCiudad.isBlank()) {
                                String firstCity = finalCiudad.split(",")[0].trim();
                                c = ciudadRepository.findByNombreIgnoreCase(firstCity).orElse(null);
                            }
                            return Vendedor.builder()
                                    .nombreCompleto(finalNombre != null && !finalNombre.isBlank() ? finalNombre.trim() : email)
                                    .email(email.trim().toLowerCase())
                                    .telefono("0")
                                    .ci("0")
                                    .ciudad(c)
                                    .activo(true)
                                    .pendingApproval(false)
                                    .build();
                        });
                v.setActivo(true);
                v.setPendingApproval(false);
                v = vendedorRepository.save(v);
                u.setVendedorId(v.getId());
            }

            Usuario saved = usuarioRepository.save(u);
            auditoriaService.log(getUser(), "USUARIO_CREADO",
                "Usuario creado con rol " + newRol + ": " + email, "Usuario", saved.getId().toString());
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "email", saved.getEmail(),
                    "rol", saved.getRol().name(),
                    "message", "Usuario creado exitosamente"
            ));
        } else {
            u.setActivo(true);
            u.setRol(newRol);
            if (nombreCompleto != null && !nombreCompleto.isBlank()) {
                u.setNombreCompleto(nombreCompleto.trim());
            }

            if (tiendaIdStr != null && !tiendaIdStr.isBlank() && !tiendaIdStr.equalsIgnoreCase("null")) {
                try {
                    u.setTiendaId(Long.parseLong(tiendaIdStr.trim()));
                } catch (NumberFormatException ignored) {}
            } else if (body.containsKey("tiendaId")) {
                u.setTiendaId(null);
            }

            if (newRol == Usuario.Rol.VENDOR) {
                final String finalCiudad = ciudad;
                final String finalNombre = nombreCompleto;
                if (u.getVendedorId() != null) {
                    vendedorRepository.findById(u.getVendedorId()).ifPresent(v -> {
                        v.setActivo(true);
                        v.setPendingApproval(false);
                        if (finalNombre != null && !finalNombre.isBlank()) {
                            v.setNombreCompleto(finalNombre.trim());
                        }
                        vendedorRepository.save(v);
                    });
                } else {
                    Vendedor v = vendedorRepository.findByEmail(email.trim().toLowerCase())
                            .orElseGet(() -> {
                                com.ninabit.bono.modules.city.Ciudad c = null;
                                if (finalCiudad != null && !finalCiudad.isBlank()) {
                                    String firstCity = finalCiudad.split(",")[0].trim();
                                    c = ciudadRepository.findByNombreIgnoreCase(firstCity).orElse(null);
                                }
                                return Vendedor.builder()
                                        .nombreCompleto(finalNombre != null && !finalNombre.isBlank() ? finalNombre.trim() : email)
                                        .email(email.trim().toLowerCase())
                                        .telefono("0")
                                        .ci("0")
                                        .ciudad(c)
                                        .activo(true)
                                        .pendingApproval(false)
                                        .build();
                            });
                    v.setActivo(true);
                    v.setPendingApproval(false);
                    v = vendedorRepository.save(v);
                    u.setVendedorId(v.getId());
                }
            }

            if (body.containsKey("ciudad")) {
                u.setCiudad(ciudad != null ? ciudad : "");
                if (u.getRol() == Usuario.Rol.VENDOR && u.getVendedorId() != null) {
                    vendedorRepository.findById(u.getVendedorId()).ifPresent(v -> {
                        if (ciudad != null && !ciudad.isBlank()) {
                            ciudadRepository.findByNombreIgnoreCase(ciudad.trim()).ifPresent(c -> {
                                v.setCiudad(c);
                                vendedorRepository.save(v);
                                ventaRepository.backfillCiudadVendedor(v.getId(), c.getNombre());
                            });
                        }
                    });
                }
            }
            if (password != null && !password.isBlank()) {
                u.setPassword(passwordEncoder.encode(password));
            }
            Usuario saved = usuarioRepository.save(u);
            auditoriaService.log(getUser(), "USUARIO_ROL_ASIGNADO", 
                "Rol " + newRol + " asignado a " + email, "Usuario", saved.getId().toString());
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "email", saved.getEmail(),
                    "rol", saved.getRol().name(),
                    "message", "Usuario actualizado exitosamente"
            ));
        }
    }

    /** DELETE /users/roles/{id} — revocar/eliminar usuario (desactivar) */
    @DeleteMapping("/users/roles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Desactivar usuario (revocar acceso)")
    public ResponseEntity<Void> revokeRole(@PathVariable Long id) {
        return usuarioRepository.findById(id).map(u -> {
            u.setActivo(false);
            usuarioRepository.save(u);
            auditoriaService.log(getUser(), "USUARIO_DESACTIVADO", 
                "Usuario desactivado ID " + id + ": " + u.getEmail(), "Usuario", id.toString());
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/roles/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Alternar estado activo/inactivo de un usuario")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        return usuarioRepository.findById(id).map(u -> {
            u.setActivo(!u.isActivo());
            Usuario saved = usuarioRepository.save(u);
            auditoriaService.log(getUser(), "USUARIO_ESTADO_CAMBIADO", 
                "Estado de usuario " + u.getEmail() + " cambiado a " + (saved.isActivo() ? "ACTIVO" : "INACTIVO"), 
                "Usuario", id.toString());
            return ResponseEntity.ok(Map.<String, Object>of(
                    "id", saved.getId(),
                    "email", saved.getEmail(),
                    "activo", saved.isActivo(),
                    "message", saved.isActivo() ? "Usuario activado" : "Usuario desactivado"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Resetear contraseña de un usuario directamente como admin")
    public ResponseEntity<?> adminResetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }

        return usuarioRepository.findById(id).map(u -> {
            u.setPassword(passwordEncoder.encode(newPassword));
            usuarioRepository.save(u);
            auditoriaService.log(getUser(), "PASSWORD_RESET_ADMIN", 
                "Contraseña restablecida por admin para: " + u.getEmail(), "Usuario", id.toString());
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

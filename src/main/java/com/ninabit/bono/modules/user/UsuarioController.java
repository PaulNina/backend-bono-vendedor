package com.ninabit.bono.modules.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    private final PasswordEncoder passwordEncoder;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    /** GET /users/roles — lista todos los usuarios con su rol y filtros */
    @GetMapping("/users/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios y sus roles con filtros")
    public List<Map<String, Object>> getRoles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String rol) {
        
        Map<String, String> cityToDept = ciudadRepository.findAll().stream()
                .collect(Collectors.toMap(Ciudad::getNombre, Ciudad::getDepartamento, (a, b) -> a));

        return usuarioRepository.findAll().stream()
                .filter(u -> {
                    boolean match = true;
                    if (search != null && !search.isBlank()) {
                        match = u.getEmail().toLowerCase().contains(search.toLowerCase());
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
                if (v != null && v.getTienda() != null && v.getTienda().getCiudad() != null) {
                    ciudad = v.getTienda().getCiudad().getNombre();
                    departamento = cityToDept.getOrDefault(ciudad, "");
                }
            } else if (u.getCiudad() != null && !u.getCiudad().isEmpty()) {
                ciudad = u.getCiudad();
                // for multiple cities like "La Paz, Oruro", map to departments (unique)
                departamento = java.util.Arrays.stream(ciudad.split(","))
                        .map(c -> cityToDept.getOrDefault(c.trim(), ""))
                        .filter(d -> !d.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(", "));
            }

            return Map.<String, Object>of(
                    "id", u.getId(),
                    "email", u.getEmail(),
                    "rol", u.getRol().name(),
                    "ciudad", ciudad,
                    "departamento", departamento,
                    "createdAt", "");
        }).collect(Collectors.toList());
    }

    /** POST /users/roles — asignar rol a un usuario por email */
    @PostMapping("/users/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Asignar o cambiar rol a un usuario")
    public ResponseEntity<?> assignRole(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String rol = body.get("rol");
        String ciudad = body.get("ciudad");

        if (email == null || rol == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email y rol son obligatorios"));
        }

        Usuario.Rol newRol;
        try {
            newRol = Usuario.Rol.valueOf(rol.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rol inválido: " + rol));
        }

        return usuarioRepository.findByEmailAndActivoTrue(email)
                .map(u -> {
                    u.setRol(newRol);
                    if (body.containsKey("ciudad")) {
                        u.setCiudad(ciudad != null ? ciudad : "");
                    }
                    Usuario saved = usuarioRepository.save(u);
                    auditoriaService.log(getUser(), "USUARIO_ROL_ASIGNADO", 
                        "Rol " + newRol + " asignado a " + email, "Usuario", saved.getId().toString());
                    return ResponseEntity.ok((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /users/roles/{id} — revocar/eliminar usuario (desactivar) */
    @DeleteMapping("/users/roles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar usuario (revocar acceso)")
    public ResponseEntity<Void> revokeRole(@PathVariable Long id) {
        return usuarioRepository.findById(id).map(u -> {
            u.setActivo(false);
            usuarioRepository.save(u);
            auditoriaService.log(getUser(), "USUARIO_DESACTIVADO", 
                "Usuario desactivado: " + u.getEmail(), "Usuario", id.toString());
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /** PUT /auth/change-password — cambiar contraseña del usuario autenticado */
    @PutMapping("/auth/change-password")
    @Operation(summary = "Cambiar contraseña del usuario autenticado")
    public ResponseEntity<?> changePassword(Authentication auth,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }
        return usuarioRepository.findByEmailAndActivoTrue(auth.getName())
                .map(u -> {
                    u.setPassword(passwordEncoder.encode(newPassword));
                    usuarioRepository.save(u);
                    auditoriaService.log(auth.getName(), "USUARIO_PASSWORD_CAMBIADO", 
                        "El usuario cambió su propia contraseña", "Usuario", u.getId().toString());
                    return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

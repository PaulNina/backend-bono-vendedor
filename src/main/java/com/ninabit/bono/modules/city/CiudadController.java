package com.ninabit.bono.modules.city;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
@Tag(name = "Ciudades")
public class CiudadController {

    private final CiudadRepository ciudadRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @Operation(summary = "Listar todas las ciudades")
    public List<Ciudad> getAll() {
        return ciudadRepository.findAllByOrderByOrdenAsc();
    }

    @GetMapping("/active")
    @Operation(summary = "Listar ciudades activas")
    public List<Ciudad> getActive() {
        return ciudadRepository.findByActivoTrueOrderByOrdenAsc();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear ciudad")
    public Ciudad create(@RequestBody Ciudad ciudad) {
        Ciudad saved = ciudadRepository.save(ciudad);
        auditoriaService.log(getUser(), "CIUDAD_CREADA", 
            "Ciudad creada: " + saved.getNombre(), "Ciudad", saved.getId().toString());
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Actualizar ciudad")
    public ResponseEntity<Ciudad> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ciudadRepository.findById(id).map(c -> {
            if (body.containsKey("activo")) {
                c.setActivo((Boolean) body.get("activo"));
            }
            // Also support legacy 'isActive' key from frontend
            if (body.containsKey("isActive")) {
                c.setActivo((Boolean) body.get("isActive"));
            }
            if (body.containsKey("nombre")) {
                c.setNombre((String) body.get("nombre"));
            }
            if (body.containsKey("orden")) {
                c.setOrden(((Number) body.get("orden")).intValue());
            }
            return ResponseEntity.ok(ciudadRepository.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar ciudad")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!ciudadRepository.existsById(id))
            return ResponseEntity.notFound().build();
        ciudadRepository.deleteById(id);
        auditoriaService.log(getUser(), "CIUDAD_ELIMINADA", 
            "Ciudad eliminada con ID: " + id, "Ciudad", id.toString());
        return ResponseEntity.noContent().build();
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

package com.ninabit.bono.modules.citygroup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/city-groups")
@RequiredArgsConstructor
@Tag(name = "Grupos de Ciudades")
public class GrupoCiudadController {

    private final GrupoCiudadRepository grupoCiudadRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Listar grupos de ciudades")
    public List<GrupoCiudad> getAll() {
        return grupoCiudadRepository.findAllByOrderByOrdenAsc();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Obtener grupo de ciudades por ID")
    public ResponseEntity<GrupoCiudad> getById(@PathVariable Long id) {
        return grupoCiudadRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Crear grupo de ciudades")
    public GrupoCiudad create(@RequestBody GrupoCiudad grupo) {
        GrupoCiudad saved = grupoCiudadRepository.save(grupo);
        auditoriaService.log(getUser(), "GRUPO_CIUDAD_CREADO", 
            "Grupo de ciudades creado: " + saved.getNombre(), "GrupoCiudad", saved.getId().toString());
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Actualizar grupo de ciudades")
    public ResponseEntity<GrupoCiudad> update(@PathVariable Long id, @RequestBody GrupoCiudad grupo) {
        if (!grupoCiudadRepository.existsById(id))
            return ResponseEntity.notFound().build();
        grupo.setId(id);
        GrupoCiudad saved = grupoCiudadRepository.save(grupo);
        auditoriaService.log(getUser(), "GRUPO_CIUDAD_ACTUALIZADO", 
            "Grupo de ciudades actualizado: " + saved.getNombre(), "GrupoCiudad", id.toString());
        return ResponseEntity.ok(saved);
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Eliminar grupo de ciudades")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!grupoCiudadRepository.existsById(id))
            return ResponseEntity.notFound().build();
        grupoCiudadRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

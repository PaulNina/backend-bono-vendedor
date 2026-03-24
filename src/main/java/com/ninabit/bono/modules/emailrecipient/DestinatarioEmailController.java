package com.ninabit.bono.modules.emailrecipient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/email-recipients")
@RequiredArgsConstructor
@Tag(name = "Destinatarios de Email")
public class DestinatarioEmailController {

    private final DestinatarioEmailRepository repository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Listar todos los destinatarios de email")
    public List<DestinatarioEmail> getAll(@RequestParam(required = false) String ciudad,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) Long grupoId) {
        if (ciudad != null && !ciudad.isBlank())
            return repository.findByCiudad(ciudad);
        if (campanaId != null)
            return repository.findByCampanaId(campanaId);
        if (grupoId != null)
            return repository.findByGrupoId(grupoId);
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Agregar destinatario de email")
    public DestinatarioEmail create(@RequestBody DestinatarioEmail destinatario) {
        DestinatarioEmail saved = repository.save(destinatario);
        auditoriaService.log(getUser(), "DESTINATARIO_EMAIL_CREADO", 
            "Destinatario email agregado: " + saved.getEmail(), "DestinatarioEmail", saved.getId().toString());
        return saved;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Eliminar destinatario de email")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return repository.findById(id).map(d -> {
            repository.delete(d);
            auditoriaService.log(getUser(), "DESTINATARIO_EMAIL_ELIMINADO", 
                "Destinatario email eliminado: " + d.getEmail(), "DestinatarioEmail", id.toString());
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

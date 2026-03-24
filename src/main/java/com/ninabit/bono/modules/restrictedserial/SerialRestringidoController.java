package com.ninabit.bono.modules.restrictedserial;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/restricted-serials")
@RequiredArgsConstructor
@Tag(name = "Seriales Restringidos")
public class SerialRestringidoController {

    private final SerialRestringidoRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Listar seriales restringidos")
    public List<SerialRestringido> getAll(@RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return repository.findBySerialContainingIgnoreCaseOrderByImportadoEnDesc(search);
        }
        List<SerialRestringido> all = repository.findAllByOrderByImportadoEnDesc();
        // Limit to 500 for performance
        return all.size() > 500 ? all.subList(0, 500) : all;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Crear serial restringido")
    public SerialRestringido create(@RequestBody SerialRestringido serialRestringido) {
        return repository.save(serialRestringido);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Importar múltiples seriales restringidos")
    public Map<String, Object> bulkCreate(@RequestBody List<SerialRestringido> seriales) {
        int saved = 0;
        int skipped = 0;
        for (SerialRestringido s : seriales) {
            if (s.getSerial() == null || s.getSerial().isBlank())
                continue;
            if (!repository.existsBySerial(s.getSerial())) {
                repository.save(s);
                saved++;
            } else {
                skipped++;
            }
        }
        return Map.of("saved", saved, "skipped", skipped);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Eliminar serial restringido")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id))
            return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

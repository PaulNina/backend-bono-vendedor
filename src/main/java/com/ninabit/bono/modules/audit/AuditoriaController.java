package com.ninabit.bono.modules.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Tag(name = "Auditoría")
public class AuditoriaController {

    private final AuditoriaRepository auditoriaRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'SUPERVISOR')")
    @Operation(summary = "Listar log de auditoría con filtros y paginación")
    public Page<Auditoria> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("fecha").descending());

        Specification<Auditoria> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("usuario")), pattern),
                        cb.like(cb.lower(root.get("descripcion")), pattern)));
            }

            if (module != null && !module.isBlank() && !module.equals("ALL")) {
                predicates.add(cb.like(root.get("accion"), module + "_%"));
            }

            if (startDate != null && !startDate.isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), 
                    java.time.LocalDateTime.parse(startDate + "T00:00:00")));
            }

            if (endDate != null && !endDate.isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), 
                    java.time.LocalDateTime.parse(endDate + "T23:59:59")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditoriaRepository.findAll(spec, pageable);
    }
}

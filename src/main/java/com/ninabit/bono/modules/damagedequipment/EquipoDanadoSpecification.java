package com.ninabit.bono.modules.damagedequipment;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EquipoDanadoSpecification {

    public static Specification<EquipoDanado> filterBy(
            String ciudad,
            String cliente,
            String serial,
            String modelo,
            String tipoFalla,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // El JOIN a registroCliente solo se crea cuando hay filtros que lo requieran
            // Y NUNCA en la COUNT query (que usa Long.class como resultType).
            // Esto evita que Hibernate genere SQL inválido en la query de conteo paginado.
            boolean necesitaJoin = (ciudad != null && !ciudad.isBlank() && !"all".equalsIgnoreCase(ciudad.trim()))
                    || (cliente != null && !cliente.isBlank());

            boolean isCountQuery = Long.class.equals(query.getResultType());

            if (necesitaJoin && !isCountQuery) {
                Join<EquipoDanado, RegistroClienteDanado> clienteJoin = root.join("registroCliente", JoinType.LEFT);

                if (ciudad != null && !ciudad.isBlank() && !"all".equalsIgnoreCase(ciudad.trim())) {
                    predicates.add(cb.equal(cb.lower(clienteJoin.get("ciudad")), ciudad.trim().toLowerCase()));
                }
                if (cliente != null && !cliente.isBlank()) {
                    predicates.add(cb.like(cb.lower(clienteJoin.get("clienteNombre")), "%" + cliente.trim().toLowerCase() + "%"));
                }
            } else if (necesitaJoin) {
                // En la COUNT query usamos subquery para filtrar por cliente/ciudad sin JOIN problemático
                var subquery = query.subquery(Long.class);
                var subRoot = subquery.from(RegistroClienteDanado.class);
                subquery.select(subRoot.get("id"));
                List<Predicate> subPredicates = new ArrayList<>();
                if (ciudad != null && !ciudad.isBlank() && !"all".equalsIgnoreCase(ciudad.trim())) {
                    subPredicates.add(cb.equal(cb.lower(subRoot.get("ciudad")), ciudad.trim().toLowerCase()));
                }
                if (cliente != null && !cliente.isBlank()) {
                    subPredicates.add(cb.like(cb.lower(subRoot.get("clienteNombre")), "%" + cliente.trim().toLowerCase() + "%"));
                }
                subquery.where(subPredicates.toArray(new Predicate[0]));
                predicates.add(root.get("registroCliente").get("id").in(subquery));
            }

            if (serial != null && !serial.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("serialValidado")), "%" + serial.trim().toLowerCase() + "%"));
            }

            if (modelo != null && !modelo.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("modelo")), "%" + modelo.trim().toLowerCase() + "%"));
            }

            if (tipoFalla != null && !tipoFalla.isBlank() && !"all".equalsIgnoreCase(tipoFalla.trim())) {
                predicates.add(cb.like(cb.lower(root.get("tipoFalla")), "%" + tipoFalla.trim().toLowerCase() + "%"));
            }

            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fechaInicio));
            }

            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), fechaFin));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


package com.ninabit.bono.modules.product;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProductoSpecification {

    public static Specification<Producto> filterBy(String search, Long tipoProductoId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String likePattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("nombre")), likePattern),
                    cb.like(cb.lower(root.get("modelo")), likePattern),
                    cb.like(cb.lower(root.get("modeloCodigo")), likePattern)
                ));
            }

            if (tipoProductoId != null) {
                predicates.add(cb.equal(root.get("tipoProducto").get("id"), tipoProductoId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

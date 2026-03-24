package com.ninabit.bono.modules.sale;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VentaSpecification {

    public static Specification<Venta> filterBy(String search, String city, String status, LocalDate startDate,
            LocalDate endDate, Long campanaId, String dateType) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String cleanSearch = search.trim().toLowerCase();
                String searchPattern = "%" + cleanSearch + "%";

                jakarta.persistence.criteria.Join<Venta, com.ninabit.bono.modules.vendor.Vendedor> vendorJoin = root
                        .join("vendedor", jakarta.persistence.criteria.JoinType.LEFT);

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("vendorName")), searchPattern),
                        cb.like(cb.lower(vendorJoin.get("nombreCompleto")), searchPattern),
                        cb.like(cb.lower(root.get("serial")), searchPattern),
                        cb.like(cb.lower(root.get("ciudad")), searchPattern),
                        cb.like(cb.lower(cb.coalesce(root.get("productModel"), "")), searchPattern)));
            }

            if (city != null && !city.isBlank() && !city.equalsIgnoreCase("all")) {
                predicates.add(cb.equal(root.get("ciudad"), city));
            }

            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
                try {
                    Venta.Estado estado = Venta.Estado.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("estado"), estado));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid status
                }
            }

            String dateField = "saleDate";
            if ("createdAt".equalsIgnoreCase(dateType)) {
                dateField = "createdAt";
            }

            if (startDate != null) {
                if ("createdAt".equalsIgnoreCase(dateType)) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(dateField), startDate.atStartOfDay()));
                } else {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(dateField), startDate));
                }
            }

            if (endDate != null) {
                if ("createdAt".equalsIgnoreCase(dateType)) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(dateField), endDate.atTime(23, 59, 59)));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get(dateField), endDate));
                }
            }

            if (campanaId != null) {
                jakarta.persistence.criteria.Join<Venta, VentaCampana> campanaJoin = root.join("detallesCampanas");
                predicates.add(cb.equal(campanaJoin.get("campana").get("id"), campanaId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

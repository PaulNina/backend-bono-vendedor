package com.ninabit.bono.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VendedorRepository extends JpaRepository<Vendedor, Long> {
    List<Vendedor> findByActivoTrue();

    @Query("SELECT c.nombre, COUNT(v) FROM Vendedor v LEFT JOIN v.ciudad c WHERE v.activo = true GROUP BY c.nombre")
    List<Object[]> countActiveVendorsByCity();

    /** Ahora Vendedor.ciudad es FK directa a Ciudad */
    List<Vendedor> findByCiudadNombre(String ciudad);

    Optional<Vendedor> findByEmail(String email);

    List<Vendedor> findByPendingApprovalTrue();

    /**
     * Busca un vendedor por ID usando LEFT JOIN FETCH para evitar que Hibernate
     * descarte registros cuyo ciudad_id sea NULL (el INNER JOIN por defecto los excluye).
     */
    @Query("SELECT v FROM Vendedor v LEFT JOIN FETCH v.ciudad WHERE v.id = :id")
    Optional<Vendedor> findByIdSafe(@Param("id") Long id);

    @Query("SELECT v FROM Vendedor v WHERE " +
            "(LOWER(v.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.ci) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "v.telefono LIKE CONCAT('%', :search, '%'))")
    List<Vendedor> search(@Param("search") String search);

    @Query("SELECT v FROM Vendedor v JOIN v.ciudad c WHERE " +
            "LOWER(c.nombre) = LOWER(:ciudad) AND " +
            "(LOWER(v.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.ci) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "v.telefono LIKE CONCAT('%', :search, '%'))")
    List<Vendedor> searchByCity(@Param("search") String search, @Param("ciudad") String ciudad);
}

package com.ninabit.bono.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VendedorRepository extends JpaRepository<Vendedor, Long> {
    List<Vendedor> findByActivoTrue();

    List<Vendedor> findByTiendaCiudadNombre(String ciudad);

    Optional<Vendedor> findByEmail(String email);

    List<Vendedor> findByPendingApprovalTrue();

    @Query("SELECT v FROM Vendedor v WHERE " +
            "(LOWER(v.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.ci) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "v.telefono LIKE CONCAT('%', :search, '%'))")
    List<Vendedor> search(@Param("search") String search);

    @Query("SELECT v FROM Vendedor v JOIN v.tienda t JOIN t.ciudad c WHERE " +
            "LOWER(c.nombre) = LOWER(:ciudad) AND " +
            "(LOWER(v.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(v.ci) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "v.telefono LIKE CONCAT('%', :search, '%'))")
    List<Vendedor> searchByCity(@Param("search") String search, @Param("ciudad") String ciudad);
}

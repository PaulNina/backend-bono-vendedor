package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.vendor.Vendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendedorTiendaRepository extends JpaRepository<VendedorTienda, Long> {

    List<VendedorTienda> findByVendedorIdAndActivoTrue(Long vendedorId);

    List<VendedorTienda> findByTiendaIdAndActivoTrue(Long tiendaId);

    Optional<VendedorTienda> findFirstByVendedorIdAndTiendaIdAndActivoTrue(Long vendedorId, Long tiendaId);

    Optional<VendedorTienda> findFirstByVendedorIdAndTiendaId(Long vendedorId, Long tiendaId);

    @Query("SELECT vt.tienda FROM VendedorTienda vt WHERE vt.vendedor.id = :vendedorId AND vt.activo = true AND vt.tienda.activo = true")
    List<Tienda> findActiveTiendasByVendedorId(@Param("vendedorId") Long vendedorId);

    @Query("SELECT vt.vendedor FROM VendedorTienda vt WHERE vt.tienda.id = :tiendaId AND vt.activo = true AND vt.vendedor.activo = true")
    List<Vendedor> findActiveVendedoresByTiendaId(@Param("tiendaId") Long tiendaId);
}

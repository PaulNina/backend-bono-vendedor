package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampanaClienteProductoRepository extends JpaRepository<CampanaClienteProducto, Long> {

    List<CampanaClienteProducto> findByCampanaClienteId(Long campanaClienteId);

    List<CampanaClienteProducto> findByCampanaClienteIdAndActivoTrue(Long campanaClienteId);

    Optional<CampanaClienteProducto> findByCampanaClienteIdAndProductoId(Long campanaClienteId, Long productoId);

    Optional<CampanaClienteProducto> findByCampanaClienteIdAndProductoIdAndActivoTrue(Long campanaClienteId, Long productoId);

    void deleteByCampanaClienteId(Long campanaClienteId);
}

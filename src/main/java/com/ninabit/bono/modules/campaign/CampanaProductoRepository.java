package com.ninabit.bono.modules.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CampanaProductoRepository extends JpaRepository<CampanaProducto, Long> {
    List<CampanaProducto> findByCampanaId(Long campanaId);

    List<CampanaProducto> findByCampanaIdAndActivoTrue(Long campanaId);

    List<CampanaProducto> findByProductoId(Long productoId);

    List<CampanaProducto> findByProductoIdAndActivoTrue(Long productoId);

    Optional<CampanaProducto> findByCampanaIdAndProductoId(Long campanaId, Long productoId);
}

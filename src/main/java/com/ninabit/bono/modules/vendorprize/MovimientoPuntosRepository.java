package com.ninabit.bono.modules.vendorprize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoPuntosRepository extends JpaRepository<MovimientoPuntos, Long> {
    List<MovimientoPuntos> findByVendedorIdOrderByFechaDesc(Long vendedorId);
    List<MovimientoPuntos> findByVendedorIdAndCampanaIdOrderByFechaDesc(Long vendedorId, Long campanaId);
}

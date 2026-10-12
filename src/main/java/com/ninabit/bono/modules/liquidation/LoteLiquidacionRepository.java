package com.ninabit.bono.modules.liquidation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LoteLiquidacionRepository extends JpaRepository<LoteLiquidacion, Long> {

    boolean existsByCodigoLote(String codigoLote);

    long countByFechaCierreBetween(LocalDate start, LocalDate end);

    @Query("SELECT l FROM LoteLiquidacion l WHERE " +
           "(:campanaId IS NULL OR l.campanaId = :campanaId) AND " +
           "(:ciudad IS NULL OR :ciudad = '' OR l.ciudad = :ciudad) AND " +
           "(:estado IS NULL OR l.estado = :estado) AND " +
           "(:tipoBeneficiario IS NULL OR :tipoBeneficiario = '' OR l.tipoBeneficiario = :tipoBeneficiario) " +
           "ORDER BY l.id DESC")
    List<LoteLiquidacion> findWithFilters(
            @Param("campanaId") Long campanaId,
            @Param("ciudad") String ciudad,
            @Param("estado") LoteLiquidacion.EstadoLote estado,
            @Param("tipoBeneficiario") String tipoBeneficiario
    );
}

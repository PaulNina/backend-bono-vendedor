package com.ninabit.bono.modules.liquidation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoteLiquidacionDetalleRepository extends JpaRepository<LoteLiquidacionDetalle, Long> {

    List<LoteLiquidacionDetalle> findByLoteId(Long loteId);

    @Query("SELECT d.ventaCampanaId FROM LoteLiquidacionDetalle d " +
           "WHERE d.lote.estado != com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.ANULADO " +
           "AND d.estado != com.ninabit.bono.modules.liquidation.LoteLiquidacionDetalle.EstadoDetalle.EXCLUIDO " +
           "AND d.ventaCampanaId IS NOT NULL")
    List<Long> findActiveVentaCampanaIds();

    @Query("SELECT d.ventaId FROM LoteLiquidacionDetalle d " +
           "WHERE d.lote.estado != com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.ANULADO " +
           "AND d.estado != com.ninabit.bono.modules.liquidation.LoteLiquidacionDetalle.EstadoDetalle.EXCLUIDO")
    List<Long> findActiveVentaIds();

    List<LoteLiquidacionDetalle> findByVentaId(Long ventaId);
}

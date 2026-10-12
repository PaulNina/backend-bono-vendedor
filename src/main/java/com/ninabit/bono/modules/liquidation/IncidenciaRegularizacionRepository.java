package com.ninabit.bono.modules.liquidation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidenciaRegularizacionRepository extends JpaRepository<IncidenciaRegularizacion, Long> {

    List<IncidenciaRegularizacion> findByVendedorIdAndEstado(Long vendedorId, IncidenciaRegularizacion.EstadoIncidencia estado);

    List<IncidenciaRegularizacion> findByTiendaIdAndEstado(Long tiendaId, IncidenciaRegularizacion.EstadoIncidencia estado);

    @Query("SELECT i FROM IncidenciaRegularizacion i WHERE " +
           "(:estado IS NULL OR i.estado = :estado) AND " +
           "(:vendedorId IS NULL OR i.vendedorId = :vendedorId) AND " +
           "(:tiendaId IS NULL OR i.tiendaId = :tiendaId) " +
           "ORDER BY i.fechaRegistro DESC")
    List<IncidenciaRegularizacion> findWithFilters(
            @Param("estado") IncidenciaRegularizacion.EstadoIncidencia estado,
            @Param("vendedorId") Long vendedorId,
            @Param("tiendaId") Long tiendaId
    );
}

package com.ninabit.bono.modules.vendorprize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CanjePuntosRepository extends JpaRepository<CanjePuntos, Long> {

    List<CanjePuntos> findByVendedorIdOrderByFechaSolicitudDesc(Long vendedorId);

    List<CanjePuntos> findAllByOrderByFechaSolicitudDesc();

    List<CanjePuntos> findByEstadoOrderByFechaSolicitudDesc(CanjePuntos.EstadoCanje estado);

    List<CanjePuntos> findByVendedorIdAndPremio_Campana_IdOrderByFechaSolicitudDesc(Long vendedorId, Long campanaId);

    @Query("SELECT COALESCE(SUM(c.puntosConsumidos), 0) FROM CanjePuntos c " +
           "WHERE c.vendedor.id = :vendedorId AND c.estado IN (:estados)")
    int sumPuntosConsumidosByVendedorIdAndEstados(
            @Param("vendedorId") Long vendedorId,
            @Param("estados") Collection<CanjePuntos.EstadoCanje> estados);

    @Query("SELECT COALESCE(SUM(c.puntosConsumidos), 0) FROM CanjePuntos c " +
           "WHERE c.vendedor.id = :vendedorId AND c.premio.campana.id = :campanaId AND c.estado IN (:estados)")
    int sumPuntosConsumidosByVendedorIdAndCampanaIdAndEstados(
            @Param("vendedorId") Long vendedorId,
            @Param("campanaId") Long campanaId,
            @Param("estados") Collection<CanjePuntos.EstadoCanje> estados);
}

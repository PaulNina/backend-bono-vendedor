package com.ninabit.bono.modules.tienda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitudTiendaRepository extends JpaRepository<SolicitudTienda, Long> {

    List<SolicitudTienda> findByEstadoOrderByCreatedAtDesc(SolicitudTienda.EstadoSolicitud estado);

    List<SolicitudTienda> findAllByOrderByCreatedAtDesc();

    long countByEstado(SolicitudTienda.EstadoSolicitud estado);

    @Query("SELECT s FROM SolicitudTienda s WHERE s.emailSolicitante = :email ORDER BY s.createdAt DESC")
    List<SolicitudTienda> findByEmailSolicitante(String email);

    List<SolicitudTienda> findByTiendaExistenteId(Long tiendaId);
}

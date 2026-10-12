package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampanaClienteRepository extends JpaRepository<CampanaCliente, Long> {

    List<CampanaCliente> findByActivoTrue();

    @Query("SELECT c FROM CampanaCliente c WHERE c.activo = true AND :today BETWEEN c.fechaInicio AND c.fechaFin")
    List<CampanaCliente> findActiveForDate(LocalDate today);

    @Query("SELECT c FROM CampanaCliente c WHERE c.activo = true AND c.registroHabilitado = true AND :today BETWEEN c.fechaInicio AND c.fechaFin")
    Optional<CampanaCliente> findCurrentActiveCampaign(LocalDate today);
}

package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroClienteRepository extends JpaRepository<RegistroCliente, Long>, JpaSpecificationExecutor<RegistroCliente> {

    Optional<RegistroCliente> findByCampanaClienteIdAndSerial(Long campanaClienteId, String serial);

    boolean existsByCampanaClienteIdAndSerial(Long campanaClienteId, String serial);

    List<RegistroCliente> findByCiOrderByCreatedAtDesc(String ci);

    Optional<RegistroCliente> findByCiAndSerial(String ci, String serial);

    @Query("SELECT r.estado, COUNT(r) FROM RegistroCliente r WHERE (:campanaId IS NULL OR r.campanaCliente.id = :campanaId) GROUP BY r.estado")
    List<Object[]> countByEstadoGroupBy(Long campanaId);

    long countByCampanaClienteId(Long campanaClienteId);

    @Query("SELECT CONCAT(r.nombreCliente, ' ', r.apellidoCliente) FROM RegistroCliente r WHERE r.celular LIKE CONCAT('%', :celularCorto, '%') AND r.nombreCliente IS NOT NULL ORDER BY r.id DESC LIMIT 1")
    Optional<String> findNombrePorCelular(@org.springframework.data.repository.query.Param("celularCorto") String celularCorto);

    @Query("SELECT r FROM RegistroCliente r WHERE r.impulsadorId = :impulsadorId AND (:campanaId IS NULL OR r.campanaCliente.id = :campanaId) ORDER BY r.createdAt DESC")
    List<RegistroCliente> findByImpulsadorIdAndCampana(@org.springframework.data.repository.query.Param("impulsadorId") Long impulsadorId, @org.springframework.data.repository.query.Param("campanaId") Long campanaId);
}

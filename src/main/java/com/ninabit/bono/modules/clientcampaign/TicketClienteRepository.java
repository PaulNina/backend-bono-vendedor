package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketClienteRepository extends JpaRepository<TicketCliente, Long> {

    List<TicketCliente> findByRegistroClienteId(Long registroClienteId);

    @Query("SELECT t FROM TicketCliente t WHERE t.registroCliente.id IN :registroIds ORDER BY t.createdAt DESC")
    List<TicketCliente> findByRegistroClienteIdIn(@org.springframework.data.repository.query.Param("registroIds") List<Long> registroIds);

    List<TicketCliente> findByClienteCiOrderByCreatedAtDesc(String clienteCi);

    @Query("SELECT t FROM TicketCliente t WHERE " +
           "TRIM(t.clienteCi) = :ci OR " +
           "LOWER(TRIM(t.clienteCi)) = LOWER(:ci) OR " +
           "LOWER(t.clienteCi) LIKE LOWER(CONCAT(:ci, '%')) OR " +
           "LOWER(t.clienteCi) LIKE LOWER(CONCAT('%', :ci, '%')) OR " +
           "t.clienteCelular LIKE CONCAT('%', :ci, '%') OR " +
           "LOWER(t.serial) = LOWER(:ci) " +
           "ORDER BY t.createdAt DESC")
    List<TicketCliente> findByClienteCiFlexible(@org.springframework.data.repository.query.Param("ci") String ci);

    Optional<TicketCliente> findByCodigoTicket(String codigoTicket);

    @Query(value = "SELECT nextval('seq_ticket_cliente_codigo')", nativeQuery = true)
    Long getNextCorrelativoTicket();

    @Query("SELECT t FROM TicketCliente t WHERE t.campanaCliente.id = :campanaId AND t.estado = com.ninabit.bono.modules.clientcampaign.TicketCliente.EstadoTicket.HABILITADO AND t.excluidoQuincenal = false")
    List<TicketCliente> findEligibleForFortnightRaffle(Long campanaId);

    @Query("SELECT t FROM TicketCliente t WHERE t.campanaCliente.id = :campanaId AND t.estado = com.ninabit.bono.modules.clientcampaign.TicketCliente.EstadoTicket.HABILITADO")
    List<TicketCliente> findEligibleForFinalRaffle(Long campanaId);

    @Modifying
    @Query("UPDATE TicketCliente t SET t.estado = :nuevoEstado WHERE t.registroCliente.id = :registroId")
    int updateEstadoByRegistroId(Long registroId, TicketCliente.EstadoTicket nuevoEstado);

    @Modifying
    @Query("DELETE FROM TicketCliente t WHERE t.registroCliente.id = :registroId")
    int deleteByRegistroClienteId(Long registroId);

    @Modifying
    @Query("UPDATE TicketCliente t SET t.excluidoQuincenal = true WHERE t.campanaCliente.id = :campanaId AND t.clienteCi = :ci")
    int excludeFromFutureFortnights(Long campanaId, String ci);

    long countByCampanaClienteIdAndEstado(Long campanaId, TicketCliente.EstadoTicket estado);

    Page<TicketCliente> findByCampanaClienteId(Long campanaId, Pageable pageable);
}

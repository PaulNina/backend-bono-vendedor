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

    List<TicketCliente> findByClienteCiOrderByCreatedAtDesc(String clienteCi);

    Optional<TicketCliente> findByCodigoTicket(String codigoTicket);

    @Query("SELECT t FROM TicketCliente t WHERE t.campanaCliente.id = :campanaId AND t.estado = com.ninabit.bono.modules.clientcampaign.TicketCliente.EstadoTicket.HABILITADO AND t.excluidoQuincenal = false")
    List<TicketCliente> findEligibleForFortnightRaffle(Long campanaId);

    @Query("SELECT t FROM TicketCliente t WHERE t.campanaCliente.id = :campanaId AND t.estado = com.ninabit.bono.modules.clientcampaign.TicketCliente.EstadoTicket.HABILITADO")
    List<TicketCliente> findEligibleForFinalRaffle(Long campanaId);

    @Modifying
    @Query("UPDATE TicketCliente t SET t.estado = :nuevoEstado WHERE t.registroCliente.id = :registroId")
    int updateEstadoByRegistroId(Long registroId, TicketCliente.EstadoTicket nuevoEstado);

    @Modifying
    @Query("UPDATE TicketCliente t SET t.excluidoQuincenal = true WHERE t.campanaCliente.id = :campanaId AND t.clienteCi = :ci")
    int excludeFromFutureFortnights(Long campanaId, String ci);

    long countByCampanaClienteIdAndEstado(Long campanaId, TicketCliente.EstadoTicket estado);

    Page<TicketCliente> findByCampanaClienteId(Long campanaId, Pageable pageable);
}
